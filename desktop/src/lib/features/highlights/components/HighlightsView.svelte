<script lang="ts">
  import { onMount } from 'svelte';
  import type { HighlightDto } from '$lib/types';
  import { listHighlights, deleteHighlight } from '$lib/shared/api/tauriClient';
  import SafeCover from '$lib/features/library/components/SafeCover.svelte';
  import Pagination from '$lib/shared/ui/navigation/Pagination.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import DropMenu from '$lib/shared/ui/navigation/DropMenu.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import EmptyState from '$lib/shared/ui/feedback/EmptyState.svelte';
  import Skeleton from '$lib/shared/ui/feedback/Skeleton.svelte';
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import { PAGE_SIZE, HIGHLIGHT_COLORS, formatDate, type Props } from '../state.svelte';

  let { books, t }: Props = $props();

  // ── State ──
  let highlights = $state<HighlightDto[]>([]);
  let isLoading = $state(true);
  let searchQuery = $state('');
  let selectedColor = $state<string | null>(null);
  let selectedBookId = $state<string | null>('');
  let selectedDateRange = $state<string | null>('');
  let currentPage = $state(1);

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
      highlights = await listHighlights();
    } catch {
      highlights = [];
    } finally {
      isLoading = false;
    }
  }

  async function handleDelete(id: string): Promise<void> {
    try {
      await deleteHighlight(id);
      highlights = highlights.filter((h) => h.id !== id);
    } catch {
      // silent
    }
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
    window.addEventListener('keydown', handleKeydown);
    return () => window.removeEventListener('keydown', handleKeydown);
  });
</script>

<section class="max-w-full">
  <!-- Header -->
  <header class="mb-6">
    <h1 class="text-[1.875rem] font-bold text-(--color-primary) m-0 mb-1">
      {t('home.highlightsTitle')}
    </h1>
    <p class="text-[0.875rem] text-(--color-text-muted) m-0">{t('home.highlightsSubtitle')}</p>
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
          style="--bar-color: {HIGHLIGHT_COLORS.find((c) => c.key === highlight.color.toLowerCase())
            ?.hex ?? '#60a5fa'}"
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
                  onclick={() => handleDelete(highlight.id)}
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
