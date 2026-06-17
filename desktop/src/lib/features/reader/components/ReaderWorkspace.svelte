<script lang="ts">
  import PdfViewer from "./PdfViewer.svelte";
  import EpubNativeViewer from "./EpubNativeViewer.svelte";
  import SearchPanel from "./SearchPanel.svelte";
  import SelectionToolbar from "./SelectionToolbar.svelte";
  import ReaderTextSettings from "./ReaderTextSettings.svelte";
  import ReaderTocPanel, { type TocEntry } from "./ReaderTocPanel.svelte";
  import ReaderHeader from "./ReaderHeader.svelte";
  import ReaderFooter from "./ReaderFooter.svelte";
  import type { MessageKey } from "$lib/shared/i18n";
  import type { ReaderSettings, SearchBookTextResponse } from "$lib/shared/types";
  import type { LibraryBookDto } from "$lib/shared/types/library";
  import { debugState } from "$lib/shared/debug/debugState.svelte";
  import { saveHighlight, deleteHighlight, upsertReaderSettings, getDefaultReaderSettings } from "$lib/shared/api/tauriClient";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";
  import { createBookmarksState } from "../stores/bookmarksState.svelte";
  import { getCurrentWebviewWindow } from '@tauri-apps/api/webviewWindow';
  import { getReaderError } from "$lib/stores/readerErrorState.svelte";

  const appWindow = getCurrentWebviewWindow();

  type ActiveBook = LibraryBookDto & { filePath: string };

  type Props = {
    activeReadingBook?: ActiveBook | null;
    readerSettings?: ReaderSettings;
    percentage?: number;
    searchResponse?: SearchBookTextResponse | null;
    searchTargetLocator?: string | null;
    isSearching?: boolean;
    searchUnavailableReason?: string | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onBackToHome: () => void;
    onPdfPageChange?: (page: number, total: number) => void;
    onPdfSessionProgress?: (event: {
      startedAt: string;
      endedAt?: string;
      durationSeconds: number;
      startPercentage?: number;
      endPercentage?: number;
    }) => void;
    onEpubLocationChange?: (cfi: string, pct: number) => void;
    onReaderLocationContext?: (ctx: unknown) => void;
    onSearch?: (query: string, page: number) => void;
    onSearchJump?: (target: unknown) => void;
    preloadedBytes?: { filePath: string; data: Uint8Array } | null;
  };

  let {
    activeReadingBook = null,
    readerSettings = undefined,
    percentage = 0,
    searchResponse = null,
    searchTargetLocator = null,
    isSearching = false,
    searchUnavailableReason = null,
    preloadedBytes = null,
    t,
    onBackToHome,
    onPdfPageChange,
    onPdfSessionProgress,
    onEpubLocationChange,
    onReaderLocationContext,
    onSearch,
    onSearchJump,
  }: Props = $props();

  // ── Local reader settings state (mutable, debounce-persisted) ───
  let localReaderSettings = $state<ReaderSettings>(getDefaultReaderSettings());

  // Debounced persistence
  let persistTimer: ReturnType<typeof setTimeout> | null = null;

  function handleTextSettingsChange(updated: ReaderSettings): void {
    localReaderSettings = updated;
    if (persistTimer) clearTimeout(persistTimer);
    persistTimer = setTimeout(() => {
      upsertReaderSettings(updated);
    }, 500);
  }

  // Clean up timer on unmount
  $effect(() => {
    return () => {
      if (persistTimer) clearTimeout(persistTimer);
    };
  });

  // Sync from prop on initial load / external change
  // NOTE: JSON parse/stringify instead of structuredClone because Svelte 5 $state
  // proxies have internal slots that structuredClone cannot serialize.
  $effect(() => {
    if (readerSettings) {
      localReaderSettings = JSON.parse(JSON.stringify(readerSettings));
    }
  });

  // Selection state
  let selectedText = $state("");
  let selectionBounds = $state<{ left: number; top: number; right: number; bottom: number } | null>(null);
  let selectionContainer = $state<{ left: number; top: number; width: number; height: number } | null>(null);
  let showToolbar = $state(false);
  let selectedColor = $state("#FACC15");

  // Persisted highlights state
  type PersistedHighlight = {
    id: string;
    color: string;
    pageNumber: number;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
  };
  let persistedHighlights = $state<PersistedHighlight[]>([]);
  let lastSelectionData = $state<{
    text: string;
    bounds: { left: number; top: number; right: number; bottom: number };
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    pageNumber: number;
  } | null>(null);

  // PDF page tracking for footer
  let currentPdfPage = $state(0);
  let totalPdfPages = $state(0);

  // EPUB chapter tracking for bookmarks
  let currentEpubChapter = $state(0);

  function handlePdfPageChange(page: number, total: number): void {
    currentPdfPage = page;
    totalPdfPages = total;
    onPdfPageChange?.(page, total);
  }

  // Track current EPUB chapter from location changes
  function handleEpubLocationChange(cfi: string, pct: number): void {
    // Extract chapter index from "chapter:{index}" format
    const match = cfi.match(/chapter:(\d+)/);
    if (match) {
      currentEpubChapter = parseInt(match[1], 10);
    }
    onEpubLocationChange?.(cfi, pct);
  }

  // Search panel state
  let searchPanelOpen = $state(false);

  // Right sidebar panels
  let showTextSettings = $state(false);
  let showTocPanel = $state(false);
  let showBookmarks = $state(false);
  let isFullscreen = $state(false);
  let workspaceRoot: HTMLDivElement | null = $state(null);

  // TOC data from active viewer
  let tocEntries = $state<TocEntry[]>([]);
  let tocNavigate = $state<TocEntry | null>(null);

  // Bookmarks state
  let bookmarksState = createBookmarksState();
  let bookmarksPanelEl: HTMLDivElement | undefined = $state();

  // Focus trap for bookmarks panel when open
  $effect(() => {
    if (showBookmarks && bookmarksPanelEl) {
      const trap = createFocusTrap(bookmarksPanelEl);
      trap.activate();
      return () => trap.deactivate();
    }
  });

  // Load bookmarks when panel opens
  $effect(() => {
    if (showBookmarks && activeReadingBook) {
      bookmarksState.loadBookmarks(activeReadingBook.id);
    }
  });

  // Sync debug readerInfo when relevant state changes (gated)
  function syncDebugReaderInfo(): void {
    if (debugState.enabled) {
      debugState.readerInfo = {
        format: isPdf ? "pdf" : isEpub ? "epub" : null,
        isTocOpen: showTocPanel,
        isSearchOpen: searchPanelOpen,
        isFullscreen,
        pageInfo: `${bookProgress}%`,
        scale: 0,
      };
    }
  }

  // Fullscreen toggle using Tauri Window API (reliable in Tauri webview)
  async function toggleFullscreen(): Promise<void> {
    try {
      await appWindow.setFullscreen(!isFullscreen);
      isFullscreen = !isFullscreen;
      syncDebugReaderInfo();
    } catch {
      console.warn('Tauri fullscreen API not available');
    }
  }

  // TOC panel handlers
  function handleTocReady(entries: TocEntry[]): void {
    tocEntries = entries;
    syncDebugReaderInfo();
  }

  function handleTocNavigate(entry: TocEntry): void {
    tocNavigate = entry;
    showTocPanel = false;
  }

  function toggleTocPanel(): void {
    showTocPanel = !showTocPanel;
    syncDebugReaderInfo();
  }

  function toggleTextSettings(): void {
    showTextSettings = !showTextSettings;
    syncDebugReaderInfo();
  }

  function toggleBookmarks(): void {
    showBookmarks = !showBookmarks;
    syncDebugReaderInfo();
  }

  const isPdf = $derived(activeReadingBook?.format?.toLowerCase() === "pdf");
  const isEpub = $derived(activeReadingBook?.format?.toLowerCase() === "epub");
  const bookProgress = $derived(
    isPdf && activeReadingBook?.currentPage && activeReadingBook?.totalPages
      ? Math.round((activeReadingBook.currentPage / activeReadingBook.totalPages) * 100)
      : Math.round(percentage)
  );

  function handlePdfSelection(event: {
    text: string;
    bounds: { left: number; top: number; right: number; bottom: number };
    container: { left: number; top: number; width: number; height: number };
    placement: string;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    pageNumber: number;
  }): void {
    selectedText = event.text;
    selectionBounds = {
      left: event.bounds.left,
      top: event.bounds.top,
      right: event.bounds.right,
      bottom: event.bounds.bottom,
    };
    selectionContainer = {
      left: event.container.left,
      top: event.container.top,
      width: event.container.width,
      height: event.container.height,
    };
    // Store selection data for highlight saving
    lastSelectionData = {
      text: event.text,
      bounds: event.bounds,
      rects: event.rects,
      pageNumber: event.pageNumber,
    };
    showToolbar = true;
  }

  function handleCopy(): void {
    if (selectedText) {
      navigator.clipboard.writeText(selectedText);
    }
    dismissToolbar();
  }

  function handleNote(_text: string): void {
    // Future: save note to highlight
    dismissToolbar();
  }

  async function handleColorSelect(color: string): Promise<void> {
    selectedColor = color;

    // Save highlight and persist it visually on the PDF
    if (lastSelectionData && activeReadingBook) {
      const highlightId = crypto.randomUUID();
      const bounds = lastSelectionData.bounds;
      const pageNumber = lastSelectionData.pageNumber ?? 1;

      // Persist visually immediately
      persistedHighlights = [...persistedHighlights, {
        id: highlightId,
        color,
        pageNumber,
        rects: lastSelectionData.rects,
      }];

      // Save to backend (async, don't block UI)
      try {
        await saveHighlight({
          id: highlightId,
          bookId: activeReadingBook.id,
          text: lastSelectionData.text,
          color,
          pageNumber,
          rectLeft: bounds.left,
          rectRight: bounds.right,
          rectTop: bounds.top,
          rectBottom: bounds.bottom,
          cfi: null,
        });
      } catch (err) {
        console.error("Failed to save highlight:", err);
      }
    }
  }

  function handleHighlightAction(event: {
    highlightId: string;
    action: "updateColor" | "delete";
    color?: string;
  }): void {
    if (event.action === "updateColor" && event.color) {
      persistedHighlights = persistedHighlights.map((h) =>
        h.id === event.highlightId ? { ...h, color: event.color! } : h
      );
      if (activeReadingBook) {
        const hl = persistedHighlights.find((h) => h.id === event.highlightId);
        if (hl) {
          saveHighlight({
            id: hl.id,
            bookId: activeReadingBook.id,
            text: "",
            color: event.color,
            pageNumber: hl.pageNumber,
            rectLeft: 0,
            rectRight: 0,
            rectTop: 0,
            rectBottom: 0,
            cfi: null,
          }).catch((err) => console.error("Failed to update highlight color:", err));
        }
      }
    } else if (event.action === "delete") {
      persistedHighlights = persistedHighlights.filter((h) => h.id !== event.highlightId);
      deleteHighlight(event.highlightId).catch((err) =>
        console.error("Failed to delete highlight:", err)
      );
    }
  }

  function dismissToolbar(): void {
    showToolbar = false;
    selectedText = "";
    selectionBounds = null;
    selectionContainer = null;
    lastSelectionData = null;
    window.getSelection()?.removeAllRanges();
  }

  function toggleSearch(): void {
    searchPanelOpen = !searchPanelOpen;
    syncDebugReaderInfo();
  }
</script>

<!-- Full viewport reader layout -->
<div class="flex h-screen flex-col bg-(--color-bg-deep)" bind:this={workspaceRoot}>
  <ReaderHeader
    title={activeReadingBook?.title ?? ""}
    {showTocPanel}
    {searchPanelOpen}
    {showTextSettings}
    {showBookmarks}
    {isFullscreen}
    {t}
    {onBackToHome}
    onToggleToc={toggleTocPanel}
    onToggleSearch={toggleSearch}
    onToggleTextSettings={toggleTextSettings}
    onToggleBookmarks={toggleBookmarks}
    onToggleFullscreen={toggleFullscreen}
  />

  <!-- Reading area (centered, fill remaining space) -->
  <div
    class="flex flex-1 min-h-0"
    class:items-center={isPdf && !isFullscreen}
    class:justify-center={!isFullscreen}
    class:items-stretch={isEpub || isFullscreen}
    class:px-10={!isFullscreen}
    class:py-6={!isFullscreen}
    class:p-0={isFullscreen}
  >
    {#if getReaderError()}
      <p class="font-inter text-sm text-(--color-text-inverse)">{getReaderError()}</p>
    {:else if !activeReadingBook}
      <p class="font-inter text-sm text-(--color-text-inverse)">{t("reader.no_book_loaded")}</p>
    {:else if isPdf}
      <!-- White content card for PDF -->
      <div
        class="relative bg-white flex flex-col min-h-0"
        class:rounded-xl={!isFullscreen}
        class:shadow-lg={!isFullscreen}
        class:w-200={!isFullscreen}
        class:w-full={isFullscreen}
        class:h-full={isFullscreen}
      >
        <PdfViewer
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialPage={Math.max(1, activeReadingBook.currentPage || 1)}
          searchTargetLocator={searchTargetLocator}
          selectionColor={selectedColor}
          readerSettings={localReaderSettings}
          preloadedBytes={preloadedBytes?.filePath === activeReadingBook.filePath ? preloadedBytes.data : null}
          onPageChange={handlePdfPageChange}
          onSessionProgress={onPdfSessionProgress}
          onselection={handlePdfSelection}
          onselectionclear={dismissToolbar}
          onHighlightAction={handleHighlightAction}
          onTocReady={handleTocReady}
          externalTocNavigate={tocNavigate}
          {isFullscreen}
          onToggleFullscreen={toggleFullscreen}
          persistedHighlights={persistedHighlights}
          {t}
        />
      </div>
    {:else if isEpub}
      <!-- White content card for EPUB -->
      <div
        class="relative overflow-hidden bg-white flex flex-col h-full min-h-0"
        class:rounded-xl={!isFullscreen}
        class:shadow-lg={!isFullscreen}
        class:w-200={!isFullscreen}
        class:w-full={isFullscreen}
      >
        <EpubNativeViewer
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialPercentage={percentage}
          readerSettings={localReaderSettings}
          onLocationContext={onReaderLocationContext}
          onLocationChange={handleEpubLocationChange}
          onTocReady={handleTocReady}
          externalTocNavigate={tocNavigate}
          onselection={handlePdfSelection}
          onselectionclear={dismissToolbar}
          {isFullscreen}
          onToggleFullscreen={toggleFullscreen}
          showToc={showTocPanel}
          onToggleToc={toggleTocPanel}
          onSettingsChange={handleTextSettingsChange}
          {t}
        />
      </div>
    {:else}
      <p class="font-inter text-sm text-(--color-text-inverse)">{t("reader.formato_no_soportado")}</p>
    {/if}

    <!-- Selection Toolbar (floating) -->
    {#if showToolbar && selectionBounds && selectionContainer && selectedText}
      <SelectionToolbar
        {selectedText}
        selectionBounds={selectionBounds}
        containerRect={selectionContainer}
        onCopy={handleCopy}
        onNote={handleNote}
        onDismiss={dismissToolbar}
        onColorSelect={handleColorSelect}
        {t}
      />
    {/if}
  </div>

  <ReaderFooter
    title={activeReadingBook?.title ?? ""}
    {bookProgress}
    {currentPdfPage}
    {totalPdfPages}
    {isPdf}
    {isFullscreen}
    {t}
  />

</div>

<!-- Search Panel overlay -->
{#if searchPanelOpen && activeReadingBook}
  <SearchPanel
    bookId={activeReadingBook.id}
    disabledReason={searchUnavailableReason}
    isSearching={isSearching}
    response={searchResponse}
    onSearch={(query, page) => onSearch?.(query, page)}
    onJump={(target) => onSearchJump?.(target)}
    {t}
  />
{/if}

<!-- Text Settings Panel -->
<ReaderTextSettings
  open={showTextSettings}
  format={isPdf ? "pdf" : isEpub ? "epub" : "pdf"}
  readerSettings={localReaderSettings}
  onSettingsChange={handleTextSettingsChange}
  onClose={() => (showTextSettings = false)}
  {t}
/>

<!-- Table of Contents Panel -->
<ReaderTocPanel
  open={showTocPanel}
  entries={tocEntries}
  activeId={tocNavigate?.id}
  onNavigate={handleTocNavigate}
  onClose={() => (showTocPanel = false)}
  {t}
/>

<!-- Bookmarks Sidebar Panel -->
{#if showBookmarks && activeReadingBook}
  <div class="fixed inset-0 z-40" onclick={(e) => { if (e.target === e.currentTarget) showBookmarks = false; }} onkeydown={(e) => e.key === "Escape" && (showBookmarks = false)} role="presentation">
    <div class="absolute inset-0 bg-(--color-surface)/70"></div>
    <div bind:this={bookmarksPanelEl} class="absolute right-0 top-0 flex h-full w-65 flex-col border-l border-(--color-border-deep) bg-(--color-surface)/70 pt-15 text-(--color-text-muted) backdrop-blur-sm" onkeydown={(e) => e.key === "Escape" && (showBookmarks = false)} role="dialog" aria-label={t("reader.bookmark")} tabindex="0">
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-(--color-border)/5 px-5 py-4">
        <h2 class="text-base font-bold text-(--color-primary)">{t("reader.bookmark")}</h2>
        <div class="flex items-center gap-2">
          <button type="button" onclick={() => bookmarksState.addBookmark(activeReadingBook.id, isEpub ? currentEpubChapter + 1 : currentPdfPage || 1)} class="flex h-6 w-6 cursor-pointer items-center justify-center rounded-md bg-(--color-accent-blue) text-xs font-bold text-(--color-bg-deep) transition-colors hover:bg-(--color-accent-sky)" title={t("reader.bookmark")}>
            +
          </button>
          <button type="button" onclick={() => (showBookmarks = false)} class="cursor-pointer text-(--color-text-muted) hover:text-(--color-text-inverse)" aria-label={t("settings.close")}>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto p-4">
        {#if bookmarksState.bookmarksLoading}
          <p class="text-center text-sm italic text-(--color-text-muted)/60">{t("settings.loadingBookmarks")}</p>
        {:else if bookmarksState.bookmarksList.length === 0}
          <p class="text-center text-sm italic text-(--color-text-muted)/60">{t("settings.noBookmarks")}</p>
        {:else}
          <ul class="flex flex-col gap-2">
            {#each bookmarksState.bookmarksList as bookmark (bookmark.id)}
              <li class="flex items-center gap-2 rounded-lg border border-(--color-border-deep) bg-(--color-text-inverse)/2 px-3 py-2 transition-colors hover:bg-(--color-text-inverse)/5">
                <button type="button" class="flex flex-1 flex-col items-start gap-0.5 text-left" onclick={() => { showBookmarks = false; }}>
                  <span class="text-sm font-medium text-(--color-primary)">Page {bookmark.pageNumber}</span>
                  {#if bookmark.title}
                    <span class="text-xs text-(--color-text-muted)/60">{bookmark.title}</span>
                  {/if}
                </button>
                <button type="button" onclick={() => bookmarksState.removeBookmark(bookmark.id, activeReadingBook.id)} class="flex h-6 w-6 cursor-pointer items-center justify-center rounded text-sm text-(--color-text-muted) transition-colors hover:bg-red-500/20 hover:text-red-400" title={t("settings.deleteBookmark")}>
                  <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"></path>
                  </svg>
                </button>
              </li>
            {/each}
          </ul>
        {/if}
      </div>
    </div>
  </div>
{/if}
