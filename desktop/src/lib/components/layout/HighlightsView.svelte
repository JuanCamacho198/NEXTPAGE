<script lang="ts">
  import { onMount } from "svelte";
  import type { MessageKey } from "../../i18n";
  import type { HighlightDto, LibraryBookDto } from "$lib/types";
  import { listHighlights, deleteHighlight } from "$lib/api/tauriClient";
  import Pagination from "../ui/navigation/Pagination.svelte";
  import DropMenu from "../ui/navigation/DropMenu.svelte";
  import EmptyState from "../ui/feedback/EmptyState.svelte";
  import Skeleton from "../ui/feedback/Skeleton.svelte";
  import Button from "../ui/forms/Button.svelte";
  import {
    PAGE_SIZE,
    HIGHLIGHT_COLORS,
    formatDate,
    filterHighlights,
    type Props,
  } from "./highlightsState.svelte";

  let { books, t }: Props = $props();

  // ── State ──
  let highlights = $state<HighlightDto[]>([]);
  let isLoading = $state(true);
  let searchQuery = $state("");
  let selectedColor = $state<string | null>(null);
  let selectedBookId = $state<string | null>(null);
  let selectedDateRange = $state<string | null>(null);
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
      if (selectedDateRange === "7d") cutoff = new Date(now.getTime() - 7 * 86400000);
      else if (selectedDateRange === "30d") cutoff = new Date(now.getTime() - 30 * 86400000);
      else if (selectedDateRange === "90d") cutoff = new Date(now.getTime() - 90 * 86400000);
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

  // ── Actions ──
  async function loadHighlights() {
    isLoading = true;
    try {
      highlights = await listHighlights();
    } catch {
      highlights = [];
    } finally {
      isLoading = false;
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteHighlight(id);
      highlights = highlights.filter((h) => h.id !== id);
    } catch {
      // silent
    }
  }

  function handleCopy(text: string) {
    navigator.clipboard.writeText(text);
  }

  function clearFilters() {
    searchQuery = "";
    selectedColor = null;
    selectedBookId = null;
    selectedDateRange = null;
    currentPage = 1;
  }

  // Reset page when filters change
  $effect(() => {
    // Tracking dependencies to reset page
    void [searchQuery, selectedColor, selectedBookId, selectedDateRange];
    currentPage = 1;
  });

  // Keyboard shortcut
  const handleKeydown = (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "k") {
      e.preventDefault();
      const el = document.getElementById("highlights-search");
      el?.focus();
    }
  };

  onMount(() => {
    loadHighlights();
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  });
</script>

<section class="highlights-page">
  <!-- Header -->
  <header class="highlights-header">
    <h1 class="highlights-title">{t("home.highlightsTitle")}</h1>
    <p class="highlights-subtitle">{t("home.highlightsSubtitle")}</p>
  </header>

  <!-- Search Bar -->
  <div class="highlights-search-wrapper">
    <svg class="search-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
    </svg>
    <input
      id="highlights-search"
      type="text"
      class="highlights-search-input"
      placeholder={t("home.highlightsSearchPlaceholder")}
      bind:value={searchQuery}
    />
    <kbd class="search-shortcut">Ctrl K</kbd>
  </div>

  <!-- Filters -->
  <div class="highlights-filters">
    <div class="filter-group color-filters">
      <span class="filter-label">{t("home.highlightsFilterColor")}</span>
      <div class="color-circles">
        {#each HIGHLIGHT_COLORS as color}
          <button
            type="button"
            class="color-circle"
            class:active={selectedColor === color.key}
            style="--circle-color: {color.hex}"
            aria-label={t("highlight.selectColor", { color: t(`settings.color.${color.key}` as any) })}
            onclick={() => { selectedColor = selectedColor === color.key ? null : color.key; }}
          ></button>
        {/each}
        <button
          type="button"
          class="color-all-btn"
          class:active={!selectedColor}
          onclick={() => { selectedColor = null; }}
        >{t("home.shelfTab.all")}</button>
      </div>
    </div>

    <div class="filter-group">
      <span class="filter-label">{t("home.highlightsFilterBook")}</span>
      <select class="filter-select" bind:value={selectedBookId}>
        <option value={null}>{t("home.highlightsAllBooks")}</option>
        {#each uniqueBooks as book}
          <option value={book.id}>{book.title}</option>
        {/each}
      </select>
    </div>

    <div class="filter-group">
      <span class="filter-label">{t("home.highlightsFilterDate")}</span>
      <select class="filter-select" bind:value={selectedDateRange}>
        <option value={null}>{t("home.highlightsAllDates")}</option>
        <option value="7d">{t("home.highlightsLastWeek")}</option>
        <option value="30d">{t("home.highlightsLastMonth")}</option>
        <option value="90d">{t("home.highlightsLast3Months")}</option>
      </select>
    </div>

    <Button size="sm" variant="ghost" onclick={clearFilters}>{t("home.highlightsClearFilters")}</Button>
  </div>

  <!-- Count -->
  <p class="highlights-count">{t("home.highlightsShowingCount", { count: filteredHighlights.length })}</p>

  <!-- List -->
  {#if isLoading}
    <div class="highlights-list">
      {#each Array(3) as _}
        <Skeleton variant="book" height="100px" />
      {/each}
    </div>
  {:else if filteredHighlights.length === 0}
    <EmptyState
      icon="search"
      title={t("home.highlightsEmptyTitle")}
      description={t("home.highlightsEmptyDescription")}
    />
  {:else}
    <ul class="highlights-list">
      {#each paginatedHighlights as highlight (highlight.id)}
        {@const book = bookMap.get(highlight.bookId)}
        <li class="highlight-card" style="--bar-color: {HIGHLIGHT_COLORS.find(c => c.key === highlight.color.toLowerCase())?.hex ?? '#60a5fa'}">
          <div class="highlight-bar"></div>

          <div class="highlight-content">
            <p class="highlight-text">{highlight.text}</p>
            {#if highlight.note}
              <p class="highlight-note">📝 {highlight.note}</p>
            {/if}
            <p class="highlight-chapter">{t("home.highlightsPageLabel")} {highlight.pageNumber}{book ? ` · ${book.title}` : ""}</p>
          </div>

          {#if book}
            <div class="highlight-book-info">
              <div class="highlight-book-cover" class:no-cover={!book.coverPath}>
                {#if book.coverPath}
                  <img src={book.coverPath} alt={book.title} />
                {:else}
                  <span class="cover-placeholder">📖</span>
                {/if}
              </div>
              <p class="highlight-book-title">{book.title}</p>
              <p class="highlight-book-author">{book.author || t("app.unknownAuthor")}</p>
            </div>
          {/if}

          <div class="highlight-meta">
            <span class="highlight-date">{formatDate(highlight.createdAt)}</span>

            <DropMenu position="bottom-right">
              {#snippet trigger()}
                <button class="highlight-menu-btn" aria-label="Opciones">⋯</button>
              {/snippet}
              <div class="flex flex-col">
                <button class="menu-item" onclick={() => handleCopy(highlight.text)}>
                  <span>📋</span> {t("home.highlightsCopy")}
                </button>
                <button class="menu-item">
                  <span>📖</span> {t("home.highlightsViewInBook")}
                </button>
                {#if highlight.note}
                  <button class="menu-item">
                    <span>✏️</span> {t("home.highlightsEditNote")}
                  </button>
                {/if}
                <button class="menu-item menu-item--danger" onclick={() => handleDelete(highlight.id)}>
                  <span>🗑️</span> {t("home.highlightsDelete")}
                </button>
              </div>
            </DropMenu>
          </div>
        </li>
      {/each}
    </ul>

    <!-- Pagination -->
    {#if totalPages > 1}
      <div class="highlights-pagination">
        <Pagination bind:current={currentPage} total={totalPages} />
      </div>
    {/if}
  {/if}
</section>

<style>
  .highlights-page {
    max-width: 100%;
  }

  /* ── Header ── */
  .highlights-header {
    margin-bottom: 1.5rem;
  }
  .highlights-title {
    font-size: var(--text-3xl, 1.875rem);
    font-weight: var(--font-weight-bold, 700);
    color: var(--color-primary);
    margin: 0 0 0.25rem;
  }
  .highlights-subtitle {
    font-size: var(--text-sm, 0.875rem);
    color: var(--color-text-muted);
    margin: 0;
  }

  /* ── Search ── */
  .highlights-search-wrapper {
    position: relative;
    display: flex;
    align-items: center;
    margin-bottom: 1.25rem;
  }
  .search-icon {
    position: absolute;
    left: 1rem;
    width: 1.25rem;
    height: 1.25rem;
    color: var(--color-text-muted);
    pointer-events: none;
  }
  .highlights-search-input {
    width: 100%;
    height: 3rem;
    padding: 0 5rem 0 2.75rem;
    border-radius: var(--radius-lg, 16px);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    color: var(--color-primary);
    font-size: var(--text-sm, 0.875rem);
    font-family: var(--font-sans);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
  }
  .highlights-search-input::placeholder {
    color: var(--color-text-muted);
  }
  .highlights-search-input:focus {
    outline: none;
    border-color: var(--color-accent-blue, #49d4ff);
    box-shadow: 0 0 0 3px rgba(73, 212, 255, 0.15);
  }
  .search-shortcut {
    position: absolute;
    right: 1rem;
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-sm, 6px);
    border: 1px solid var(--color-border);
    background: var(--color-background);
    color: var(--color-text-muted);
    font-size: 0.7rem;
    font-family: var(--font-sans);
    pointer-events: none;
  }

  /* ── Filters ── */
  .highlights-filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1rem;
    padding: 0.75rem 1rem;
    border-radius: var(--radius-lg, 16px);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
  }
  .filter-group {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .filter-label {
    font-size: var(--text-xs, 0.75rem);
    font-weight: var(--font-weight-semibold, 600);
    color: var(--color-primary);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  .color-filters {
    border-right: 1px solid var(--color-border);
    padding-right: 1rem;
  }
  .color-circles {
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }
  .color-circle {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    border: 2px solid transparent;
    background: var(--circle-color);
    cursor: pointer;
    transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
  }
  .color-circle:hover {
    transform: scale(1.15);
  }
  .color-circle.active {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px rgba(73, 212, 255, 0.25);
    transform: scale(1.1);
  }
  .color-all-btn {
    padding: 0.2rem 0.6rem;
    border-radius: var(--radius-sm, 6px);
    border: 1px solid var(--color-border);
    background: transparent;
    color: var(--color-text-muted);
    font-size: var(--text-xs, 0.75rem);
    cursor: pointer;
    transition: all 0.15s ease;
    font-family: var(--font-sans);
  }
  .color-all-btn:hover {
    background: var(--color-surface-hover, rgba(25, 41, 62, 0.96));
  }
  .color-all-btn.active {
    border-color: var(--color-accent-blue, #49d4ff);
    color: var(--color-primary);
    background: var(--color-panel-accent);
  }
  .filter-select {
    padding: 0.35rem 0.75rem;
    border-radius: var(--radius-sm, 6px);
    border: 1px solid var(--color-border);
    background: var(--color-background);
    color: var(--color-primary);
    font-size: var(--text-xs, 0.75rem);
    font-family: var(--font-sans);
    cursor: pointer;
    min-width: 140px;
  }
  .filter-select:focus {
    outline: none;
    border-color: var(--color-accent-blue, #49d4ff);
  }

  /* ── Count ── */
  .highlights-count {
    font-size: var(--text-xs, 0.75rem);
    color: var(--color-text-muted);
    margin: 0 0 0.75rem;
  }

  /* ── List ── */
  .highlights-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  /* ── Card ── */
  .highlight-card {
    display: flex;
    align-items: stretch;
    gap: 1rem;
    padding: 1rem 1.25rem;
    border-radius: var(--radius-lg, 16px);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
    cursor: default;
  }
  .highlight-card:hover {
    border-color: var(--color-border-strong);
    box-shadow: var(--shadow-soft);
    background: var(--color-surface-hover, rgba(25, 41, 62, 0.96));
  }

  .highlight-bar {
    width: 4px;
    min-height: 100%;
    border-radius: 4px;
    background: var(--bar-color);
    flex-shrink: 0;
  }

  .highlight-content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 0.35rem;
  }
  .highlight-text {
    font-size: var(--text-sm, 0.875rem);
    font-weight: var(--font-weight-medium, 500);
    color: var(--color-primary);
    margin: 0;
    line-height: 1.5;
  }
  .highlight-note {
    font-size: var(--text-xs, 0.75rem);
    color: var(--color-text-muted);
    margin: 0;
    font-style: italic;
  }
  .highlight-chapter {
    font-size: var(--text-xs, 0.75rem);
    color: var(--color-text-muted);
    margin: 0;
  }

  /* ── Book info ── */
  .highlight-book-info {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.25rem;
    flex-shrink: 0;
    width: 80px;
    text-align: center;
  }
  .highlight-book-cover {
    width: 48px;
    height: 64px;
    border-radius: var(--radius-sm, 6px);
    overflow: hidden;
    border: 1px solid var(--color-border);
    background: var(--color-background);
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .highlight-book-cover img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .cover-placeholder {
    font-size: 1.25rem;
    opacity: 0.5;
  }
  .highlight-book-title {
    font-size: 0.65rem;
    font-weight: var(--font-weight-semibold, 600);
    color: var(--color-primary);
    margin: 0;
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .highlight-book-author {
    font-size: 0.6rem;
    color: var(--color-text-muted);
    margin: 0;
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* ── Meta / Date ── */
  .highlight-meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: space-between;
    flex-shrink: 0;
    min-width: 120px;
  }
  .highlight-date {
    font-size: var(--text-xs, 0.75rem);
    color: var(--color-text-muted);
    white-space: nowrap;
  }

  /* ── Menu ── */
  .highlight-menu-btn {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-sm, 6px);
    border: 1px solid transparent;
    background: transparent;
    color: var(--color-text-muted);
    font-size: 1.1rem;
    cursor: pointer;
    transition: all 0.15s ease;
    font-family: var(--font-sans);
  }
  .highlight-menu-btn:hover {
    background: var(--color-panel-accent);
    border-color: var(--color-border);
    color: var(--color-primary);
  }
  .menu-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    width: 100%;
    padding: 0.5rem 1rem;
    border: none;
    background: transparent;
    color: var(--color-primary);
    font-size: var(--text-sm, 0.875rem);
    font-family: var(--font-sans);
    cursor: pointer;
    text-align: left;
    transition: background 0.12s ease;
  }
  .menu-item:hover {
    background: var(--color-panel-accent);
  }
  .menu-item--danger {
    color: var(--color-error);
  }
  .menu-item--danger:hover {
    background: var(--color-error-bg, rgba(255, 123, 131, 0.14));
  }

  /* ── Pagination ── */
  .highlights-pagination {
    display: flex;
    justify-content: center;
    margin-top: 1.5rem;
    padding-bottom: 1rem;
  }
</style>
