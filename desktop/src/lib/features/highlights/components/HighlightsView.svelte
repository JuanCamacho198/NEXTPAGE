<script lang="ts">
  import { onMount } from 'svelte';
  import type { HighlightDto } from '$lib/types';
  import {
    listHighlights,
    deleteHighlight,
    upsertRemoteHighlights,
  } from '$lib/shared/api/tauriClient';
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { authState } from '$lib/stores/authState.svelte';
  import { SupabaseProgressSync } from '$lib/shared/sync/SupabaseProgressSync';
  import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
  import SafeCover from '$lib/features/library/components/SafeCover.svelte';
  import Pagination from '$lib/shared/ui/navigation/Pagination.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import DropMenu from '$lib/shared/ui/navigation/DropMenu.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import EmptyState from '$lib/shared/ui/feedback/EmptyState.svelte';
  import Skeleton from '$lib/shared/ui/feedback/Skeleton.svelte';
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import {
    PAGE_SIZE,
    HIGHLIGHT_COLORS,
    formatDate,
    resolveHighlightHex,
    type Props,
  } from '../state.svelte';

  let { books, t }: Props = $props();

  const outboxDao = new SyncOutboxDao();

  function sortByUpdatedAtDesc(list: HighlightDto[]): HighlightDto[] {
    return [...list].sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    );
  }

  // ── State ──
  let highlights = $state<HighlightDto[]>([]);
  let isLoading = $state(true);
  let searchQuery = $state('');
  let selectedColor = $state<string | null>(null);
  let selectedBookId = $state<string | null>('');
  let selectedDateRange = $state<string | null>('');
  let currentPage = $state(1);

  // Option 1 sync indicator (silent pull, incremental merge, no blink)
  let syncState = $state<'idle' | 'syncing' | 'synced'>('idle');
  let syncTimeout: ReturnType<typeof setTimeout> | null = null;

  // ── Derived ──
  const bookMap = $derived(new Map(books.map((b) => [b.id, b])));

  const filteredHighlights = $derived.by(() => {
    let result = highlights;

    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase();
      result = result.filter((h) => {
        const book = bookMap.get(h.bookId);
        return (
          h.text.toLowerCase().includes(q) ||
          (h.note && h.note.toLowerCase().includes(q)) ||
          (book && book.title.toLowerCase().includes(q)) ||
          (book && book.author.toLowerCase().includes(q))
        );
      });
    }

    if (selectedColor) {
      result = result.filter((h) => h.color.toLowerCase() === selectedColor);
    }

    if (selectedBookId) {
      result = result.filter((h) => h.bookId === selectedBookId);
    }

    if (selectedDateRange) {
      const now = new Date();
      let cutoff: Date;
      if (selectedDateRange === '7d') cutoff = new Date(now.getTime() - 7 * 86400000);
      else if (selectedDateRange === '30d') cutoff = new Date(now.getTime() - 30 * 86400000);
      else if (selectedDateRange === '90d') cutoff = new Date(now.getTime() - 90 * 86400000);
      else cutoff = new Date(0);
      result = result.filter((h) => new Date(h.createdAt) >= cutoff);
    }

    return result;
  });

  const totalPages = $derived(Math.max(1, Math.ceil(filteredHighlights.length / PAGE_SIZE)));

  const paginatedHighlights = $derived.by(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return filteredHighlights.slice(start, start + PAGE_SIZE);
  });

  const uniqueBooks = $derived.by(() => {
    const ids = new Set(highlights.map((h) => h.bookId));
    return books.filter((b) => ids.has(b.id));
  });

  const bookFilterOptions = $derived([
    { value: '', label: t('home.highlightsAllBooks') },
    ...uniqueBooks.map((b) => ({ value: b.id, label: b.title })),
  ]);

  const dateFilterOptions: Array<{ value: string; label: string }> = $derived([
    { value: '', label: t('home.highlightsAllDates') },
    { value: '7d', label: t('home.highlightsLastWeek') },
    { value: '30d', label: t('home.highlightsLastMonth') },
    { value: '90d', label: t('home.highlightsLast3Months') },
  ]);

  // ── Actions ──
  async function loadHighlights(): Promise<void> {
    isLoading = true;
    try {
      const raw = await listHighlights();
      highlights = sortByUpdatedAtDesc(raw);
    } catch {
      highlights = [];
    } finally {
      isLoading = false;
    }
  }

  /**
   * Option 1 — silent background pull on entering Highlights screen.
   * Instant local list is already shown; this merges remote highlights
   * incrementally (500-row chunks via upsertRemoteHighlights LWW) without
   * blocking UI. Chip shows Sincronizando... → Sincronizado (3s).
   */
  async function syncHighlightsInBackground(force = false): Promise<void> {
    if (syncState === 'syncing' && !force) return;
    if (!authState.userId) return;
    syncState = 'syncing';
    try {
      const sync = new SupabaseProgressSync(authState.userId);
      const rows = await sync.fetchAllHighlightsForPull();
      if (rows.length > 0) {
        const chunkSize = 500;
        for (let i = 0; i < rows.length; i += chunkSize) {
          const chunk = rows.slice(i, i + chunkSize);
          try {
            await upsertRemoteHighlights(chunk);
          } catch {
            // chunk failure is non-fatal — continue with next chunk
          }
        }
        // Incremental merge: re-read local DB sorted DESC; Svelte keyed each prevents blink
        const fresh = await listHighlights();
        const sorted = sortByUpdatedAtDesc(fresh);
        // Avoid full-blink by only updating if something changed (length or ids/order)
        const same =
          sorted.length === highlights.length &&
          sorted.every((h, idx) => h.id === highlights[idx]?.id);
        if (!same) highlights = sorted;
      }
      syncState = 'synced';
      if (syncTimeout) clearTimeout(syncTimeout);
      syncTimeout = setTimeout(() => {
        syncState = 'idle';
      }, 3000);
    } catch {
      syncState = 'idle';
    }
  }

  async function handleDelete(highlight: HighlightDto): Promise<void> {
    try {
      await deleteHighlight(highlight.id);
      highlights = highlights.filter((h) => h.id !== highlight.id);
      // Mirror the same cross-device contract as ReaderWorkspace: enqueue a
      // HIGHLIGHT DELETE so Supabase receives the tombstone too.
      if (authState.userId) {
        const updatedAt = new Date().toISOString();
        void outboxDao.add(
          'HIGHLIGHT',
          highlight.id,
          'DELETE',
          JSON.stringify({
            userId: authState.userId,
            bookId: highlight.bookId,
            cfiRange: highlight.cfi ?? '',
            textContent: highlight.text,
            color: highlight.color,
            page: highlight.pageNumber,
            deletedAt: updatedAt,
            updatedAt,
          }),
        );
      }
    } catch {
      // silent
    }
  }

  async function handleViewInBook(highlight: HighlightDto): Promise<void> {
    const book = appState.getBookById(highlight.bookId);
    if (!book) return;
    await appState.startReading(book);
    // Set a navigation target so the viewer jumps to the highlight position.
    // EPUB highlights carry a CFI; PDF highlights fall back to the page.
    appState.searchTargetLocator = highlight.cfi
      ? highlight.cfi
      : book.format.toLowerCase() === 'pdf'
        ? `page:${highlight.pageNumber}`
        : null;
  }

  function handleCopy(text: string): void {
    navigator.clipboard.writeText(text);
  }

  function clearFilters(): void {
    searchQuery = '';
    selectedColor = null;
    selectedBookId = '';
    selectedDateRange = '';
    currentPage = 1;
  }

  // Reset page when filters change
  $effect(() => {
    // Tracking dependencies to reset page
    void [searchQuery, selectedColor, selectedBookId, selectedDateRange];
    currentPage = 1;
  });

  // Keyboard shortcut
  const handleKeydown = (e: KeyboardEvent): void => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault();
      const el = document.getElementById('highlights-search');
      el?.focus();
    }
  };

  onMount(() => {
    loadHighlights();
    // Silent pull after local render — no await, no blocking
    void syncHighlightsInBackground();
    window.addEventListener('keydown', handleKeydown);
    const onHighlightsChanged = (): void => {
      void (async () => {
        try {
          const fresh = await listHighlights();
          highlights = sortByUpdatedAtDesc(fresh);
        } catch {
          // keep current
        }
      })();
    };
    window.addEventListener('highlights:changed', onHighlightsChanged as EventListener);
    return () => {
      window.removeEventListener('keydown', handleKeydown);
      window.removeEventListener('highlights:changed', onHighlightsChanged as EventListener);
      if (syncTimeout) clearTimeout(syncTimeout);
    };
  });
</script>

<section class="max-w-full">
  <!-- Header -->
  <header class="mb-6">
    <div class="flex items-start justify-between gap-4">
      <div class="flex-1 min-w-0">
        <h1 class="text-[1.875rem] font-bold text-(--color-primary) m-0 mb-1">
          {t('home.highlightsTitle')}
        </h1>
        <p class="text-[0.875rem] text-(--color-text-muted) m-0">{t('home.highlightsSubtitle')}</p>
      </div>
      <!-- Circular ↻ refresh button — only on Highlights screen -->
      <button
        type="button"
        class="shrink-0 w-10 h-10 rounded-full flex items-center justify-center border border-(--color-border) bg-(--color-surface) text-(--color-text-muted) transition-all hover:border-(--color-border-strong) hover:text-(--color-primary) hover:bg-(--color-surface-hover,rgba(25,41,62,0.96)) disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
        aria-label={t('home.highlightsRefresh')}
        title={t('home.highlightsRefresh')}
        disabled={syncState === 'syncing'}
        onclick={() => void syncHighlightsInBackground(true)}
      >
        {#if syncState === 'syncing'}
          <svg
            class="w-5 h-5 animate-spin"
            fill="none"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        {:else}
          <!-- RefreshCw (lucide) -->
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8V3h-5l2.26 2.26A7 7 0 1 0 21 12"></path>
            <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16v5h5l-2.26-2.26A7 7 0 0 0 21 12"></path>
          </svg>
        {/if}
      </button>
    </div>
    {#if syncState === 'syncing' || syncState === 'synced'}
      <div class="mt-3 flex justify-center">
        <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-(--color-border) bg-(--color-surface) text-[0.7rem] font-medium text-(--color-text-muted) shadow-sm">
          {#if syncState === 'syncing'}
            <svg class="w-3.5 h-3.5 animate-spin" fill="none" viewBox="0 0 24 24" aria-hidden="true">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            {t('home.highlightsSyncing')}
          {:else}
            <svg class="w-3.5 h-3.5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"></path>
            </svg>
            {t('home.highlightsSynced')}
          {/if}
        </span>
      </div>
    {/if}
  </header>

  <!-- Search Bar -->
  <div class="relative flex items-center mb-5">
    <svg
      class="pointer-events-none absolute left-4 z-0 w-5 h-5 text-(--color-text-muted)"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
      />
    </svg>
    <input
      id="highlights-search"
      type="text"
      class="w-full h-12 pl-14 pr-20 rounded-2xl border border-(--color-border) bg-(--color-surface) text-(--color-primary) text-[0.875rem] font-sans transition-colors focus:outline-none focus:border-(--color-accent-blue,#49d4ff) focus:shadow-[0_0_0_3px_rgba(73,212,255,0.15)] placeholder:text-(--color-text-muted)"
      placeholder={t('home.highlightsSearchPlaceholder')}
      bind:value={searchQuery}
    />
    <kbd
      class="absolute right-4 inline-flex items-center gap-1 px-2 py-1 rounded-md border border-(--color-border) bg-(--color-background) text-(--color-text-muted) text-[0.7rem] font-sans pointer-events-none"
      >Ctrl K</kbd
    >
  </div>

  <!-- Filters -->
  <div
    class="flex flex-wrap items-center gap-4 mb-4 px-4 py-3 rounded-2xl border border-(--color-border) bg-(--color-surface)"
  >
    <div class="flex items-center gap-2 border-r border-(--color-border) pr-4">
      <span class="text-[0.75rem] font-semibold text-(--color-primary) uppercase tracking-wider"
        >{t('home.highlightsFilterColor')}</span
      >
      <div class="flex items-center gap-1">
        {#each HIGHLIGHT_COLORS as color}
          <button
            type="button"
            class="w-6 h-6 rounded-full border-2 border-transparent cursor-pointer transition-all hover:scale-[1.15] {selectedColor ===
            color.key
              ? 'border-(--color-primary) shadow-[0_0_0_3px_rgba(73,212,255,0.25)] scale-110'
              : ''}"
            style="background: {color.hex};"
            aria-label={t('highlight.selectColor', {
              color: t(`settings.color.${color.key}` as import('$lib/shared/i18n').MessageKey),
            })}
            onclick={() => {
              selectedColor = selectedColor === color.key ? null : color.key;
            }}
          ></button>
        {/each}
        <button
          type="button"
          class="px-2 py-1 rounded-md border border-(--color-border) bg-transparent text-(--color-text-muted) text-[0.75rem] cursor-pointer transition-all font-sans hover:bg-(--color-surface-hover,rgba(25,41,62,0.96)) {!selectedColor
            ? 'border-(--color-accent-blue,#49d4ff) text-(--color-primary) bg-(--color-panel-accent)'
            : ''}"
          onclick={() => {
            selectedColor = null;
          }}>{t('home.shelfTab.all')}</button
        >
      </div>
    </div>

    <div class="flex items-center gap-2">
      <span class="text-[0.75rem] font-semibold text-(--color-primary) uppercase tracking-wider"
        >{t('home.highlightsFilterBook')}</span
      >
      <Dropdown options={bookFilterOptions} bind:value={selectedBookId} class="min-w-[150px]" />
    </div>

    <div class="flex items-center gap-2">
      <span class="text-[0.75rem] font-semibold text-(--color-primary) uppercase tracking-wider"
        >{t('home.highlightsFilterDate')}</span
      >
      <Dropdown options={dateFilterOptions} bind:value={selectedDateRange} class="min-w-[140px]" />
    </div>

    <Button size="sm" variant="ghost" onclick={clearFilters}
      >{t('home.highlightsClearFilters')}</Button
    >
  </div>

  <!-- Count -->
  <p class="text-[0.75rem] text-(--color-text-muted) m-0 mb-3">
    {t('home.highlightsShowingCount', { count: filteredHighlights.length })}
  </p>

  <!-- List -->
  {#if isLoading}
    <ul class="list-none p-0 m-0 flex flex-col gap-2">
      {#each Array(3) as _}
        <Skeleton variant="book" height="100px" />
      {/each}
    </ul>
  {:else if filteredHighlights.length === 0}
    <div class="flex min-h-[55vh] items-center justify-center">
      <EmptyState
        icon="search"
        title={t('home.highlightsEmptyTitle')}
        description={t('home.highlightsEmptyDescription')}
      />
    </div>
  {:else}
    <ul class="list-none p-0 m-0 flex flex-col gap-2">
      {#each paginatedHighlights as highlight (highlight.id)}
        {@const book = bookMap.get(highlight.bookId)}
        <li
          class="flex items-stretch gap-4 p-5 rounded-2xl border border-(--color-border) bg-(--color-surface) transition-all cursor-default hover:border-(--color-border-strong) hover:shadow-(--shadow-soft) hover:bg-(--color-surface-hover,rgba(25,41,62,0.96))"
          style="--bar-color: {resolveHighlightHex(highlight.color)}"
        >
          <div class="w-1 min-h-full rounded bg-(--bar-color) shrink-0"></div>

          <div class="flex-1 min-w-0 flex flex-col justify-center gap-1">
            <p class="text-[0.875rem] font-medium text-(--color-primary) m-0 line-height-[1.5]">
              {highlight.text}
            </p>
            {#if highlight.note}
              <p class="text-[0.75rem] text-(--color-text-muted) m-0 italic">
                <Icon name="note" size="sm" />
                {highlight.note}
              </p>
            {/if}
            <p class="text-[0.75rem] text-(--color-text-muted) m-0">
              {t('home.highlightsPageLabel')}
              {highlight.pageNumber}{book ? ` · ${book.title}` : ''}
            </p>
          </div>

          {#if book}
            <div class="flex flex-col items-center gap-1 shrink-0 w-20 text-center">
              <div
                class="w-12 h-16 rounded-md overflow-hidden border border-(--color-border) bg-(--color-background) flex items-center justify-center"
              >
                {#if book.coverPath}
                  <SafeCover
                    path={book.coverPath}
                    alt={book.title}
                    className="w-full h-full object-cover"
                  />
                {:else}
                  <span class="text-[1.25rem] opacity-50"><Icon name="book" size="lg" /></span>
                {/if}
              </div>
              <p
                class="text-[0.65rem] font-semibold text-(--color-primary) m-0 max-w-20 overflow-hidden text-ellipsis whitespace-nowrap"
              >
                {book.title}
              </p>
              <p
                class="text-[0.6rem] text-(--color-text-muted) m-0 max-w-20 overflow-hidden text-ellipsis whitespace-nowrap"
              >
                {book.author || t('app.unknownAuthor')}
              </p>
            </div>
          {/if}

          <div class="flex flex-col items-end justify-between shrink-0 min-w-30">
            <span class="text-[0.75rem] text-(--color-text-muted) whitespace-nowrap"
              >{formatDate(highlight.createdAt)}</span
            >

            <DropMenu position="bottom-right">
              {#snippet trigger()}
                <button
                  class="w-8 h-8 flex items-center justify-center rounded-md border border-transparent bg-transparent text-(--color-text-muted) text-[1.1rem] cursor-pointer transition-all font-sans hover:bg-(--color-panel-accent) hover:border-(--color-border) hover:text-(--color-primary)"
                  aria-label="Opciones"><Icon name="more-dot" size="sm" /></button
                >
              {/snippet}
              <div class="flex flex-col">
                <button
                  class="flex items-center gap-2 w-full p-2 border-none bg-transparent text-(--color-primary) text-[0.875rem] font-sans cursor-pointer text-left transition-colors hover:bg-(--color-panel-accent)"
                  onclick={() => handleCopy(highlight.text)}
                >
                  <Icon name="copy" size="sm" />
                  {t('home.highlightsCopy')}
                </button>
                <button
                  class="flex items-center gap-2 w-full p-2 border-none bg-transparent text-(--color-primary) text-[0.875rem] font-sans cursor-pointer text-left transition-colors hover:bg-(--color-panel-accent)"
                  onclick={() => handleViewInBook(highlight)}
                >
                  <Icon name="book" size="sm" />
                  {t('home.highlightsViewInBook')}
                </button>
                {#if highlight.note}
                  <button
                    class="flex items-center gap-2 w-full p-2 border-none bg-transparent text-(--color-primary) text-[0.875rem] font-sans cursor-pointer text-left transition-colors hover:bg-(--color-panel-accent)"
                  >
                    <Icon name="edit" size="sm" />
                    {t('home.highlightsEditNote')}
                  </button>
                {/if}
                <button
                  class="flex items-center gap-2 w-full p-2 border-none bg-transparent text-(--color-error) text-[0.875rem] font-sans cursor-pointer text-left transition-colors hover:bg-(--color-error-bg,rgba(255,123,131,0.14))"
                  onclick={() => handleDelete(highlight)}
                >
                  <Icon name="trash" size="sm" />
                  {t('home.highlightsDelete')}
                </button>
              </div>
            </DropMenu>
          </div>
        </li>
      {/each}
    </ul>

    <!-- Pagination -->
    {#if totalPages > 1}
      <div class="flex justify-center mt-6 pb-4">
        <Pagination bind:current={currentPage} total={totalPages} />
      </div>
    {/if}
  {/if}
</section>
