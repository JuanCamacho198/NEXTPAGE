<script lang="ts">
  import PdfViewer from "./PdfViewer.svelte";
  import EpubNativeViewer from "./EpubNativeViewer.svelte";
  import SearchPanel from "./SearchPanel.svelte";
  import SelectionToolbar from "./SelectionToolbar.svelte";
  import ReaderTextSettings from "./ReaderTextSettings.svelte";
  import ReaderTocPanel, { type TocEntry } from "./ReaderTocPanel.svelte";
  import ReaderHeader from "./ReaderHeader.svelte";
  import ReaderFooter from "./ReaderFooter.svelte";
  import type { MessageKey } from "$lib/i18n";
  import type { ReaderSettings } from "$lib/shared/types";
  import type { LibraryBookDto } from "$lib/shared/types/library";
  import { debugState } from "$lib/debug/debugState.svelte";
  import { saveHighlight, deleteHighlight } from "$lib/api/tauriClient";
  import DebugToggle from "$lib/debug/DebugToggle.svelte";
  import DebugPanel from "$lib/debug/DebugPanel.svelte";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";
  import { createBookmarksState } from "../stores/bookmarksState.svelte";

  type ActiveBook = LibraryBookDto & { filePath: string };

  type Props = {
    activeReadingBook?: ActiveBook | null;
    readerSettings?: ReaderSettings;
    cfiLocation?: string;
    percentage?: number;
    searchResponse?: any;
    searchTargetLocator?: string | null;
    isSearching?: boolean;
    searchUnavailableReason?: string | null;
    readerError?: string | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onBackToHome: () => void;
    onPdfPageChange?: (page: number, total: number) => void;
    onPdfSessionProgress?: (event: any) => void;
    onEpubLocationChange?: (cfi: string, pct: number) => void;
    onReaderLocationContext?: (ctx: any) => void;
    onSearch?: (query: string, page: number) => void;
    onSearchJump?: (target: any) => void;
    preloadedBytes?: { filePath: string; data: Uint8Array } | null;
  };

  let {
    activeReadingBook = null,
    readerSettings = undefined,
    cfiLocation = "",
    percentage = 0,
    searchResponse = null,
    searchTargetLocator = null,
    isSearching = false,
    searchUnavailableReason = null,
    readerError = null,
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

  function handlePdfPageChange(page: number, total: number) {
    currentPdfPage = page;
    totalPdfPages = total;
    onPdfPageChange?.(page, total);
  }

  // Track current EPUB chapter from location changes
  function handleEpubLocationChange(cfi: string, pct: number) {
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
  let activeTocId = $state("");

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
  function syncDebugReaderInfo() {
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

  // Fullscreen toggle
  async function toggleFullscreen() {
    if (!workspaceRoot) {
      return;
    }

    try {
      if (document.fullscreenElement === workspaceRoot) {
        await document.exitFullscreen();
      } else {
        await workspaceRoot.requestFullscreen();
      }
    } catch {
      // Fullscreen not supported
    }

    isFullscreen = document.fullscreenElement === workspaceRoot;
    syncDebugReaderInfo();
  }

  // Listen for fullscreen changes from browser (Esc key, etc.)
  $effect(() => {
    const handler = () => {
      isFullscreen = document.fullscreenElement === workspaceRoot;
      syncDebugReaderInfo();
    };
    document.addEventListener("fullscreenchange", handler);
    return () => document.removeEventListener("fullscreenchange", handler);
  });

  // TOC panel handlers
  function handleTocReady(entries: TocEntry[]) {
    tocEntries = entries;
    syncDebugReaderInfo();
  }

  function handleTocNavigate(entry: TocEntry) {
    tocNavigate = entry;
    showTocPanel = false;
  }

  function toggleTocPanel() {
    showTocPanel = !showTocPanel;
    syncDebugReaderInfo();
  }

  function toggleTextSettings() {
    showTextSettings = !showTextSettings;
    syncDebugReaderInfo();
  }

  function toggleBookmarks() {
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
  }) {
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

  function handleCopy() {
    if (selectedText) {
      navigator.clipboard.writeText(selectedText);
    }
    dismissToolbar();
  }

  function handleNote(text: string) {
    // Future: save note to highlight
    dismissToolbar();
  }

  async function handleColorSelect(color: string) {
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
  }) {
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

  function dismissToolbar() {
    showToolbar = false;
    selectedText = "";
    selectionBounds = null;
    selectionContainer = null;
    lastSelectionData = null;
    window.getSelection()?.removeAllRanges();
  }

  function toggleSearch() {
    searchPanelOpen = !searchPanelOpen;
    syncDebugReaderInfo();
  }
</script>

<!-- Full viewport reader layout -->
<div class="flex h-screen flex-col bg-[#0B1120]" bind:this={workspaceRoot}>
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
    class="flex flex-1"
    class:items-center={isPdf && !isFullscreen}
    class:justify-center={!isFullscreen}
    class:items-stretch={isEpub || isFullscreen}
    class:px-10={!isFullscreen}
    class:py-6={isEpub && !isFullscreen}
    class:p-0={isFullscreen}
  >
    {#if readerError}
      <p class="font-inter text-sm text-white">{readerError}</p>
    {:else if !activeReadingBook}
      <p class="font-inter text-sm text-white">{t("reader.no_book_loaded")}</p>
    {:else if isPdf}
      <!-- White content card for PDF -->
      <div
        class="relative overflow-hidden bg-white"
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
          readerSettings={readerSettings}
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
        class="relative overflow-hidden bg-white flex flex-col h-full"
        class:rounded-xl={!isFullscreen}
        class:shadow-lg={!isFullscreen}
        class:w-200={!isFullscreen}
        class:w-full={isFullscreen}
      >
        <EpubNativeViewer
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialPercentage={percentage}
          readerSettings={readerSettings}
          onLocationContext={onReaderLocationContext}
          onLocationChange={handleEpubLocationChange}
          onTocReady={handleTocReady}
          externalTocNavigate={tocNavigate}
          onselection={handlePdfSelection}
          onselectionclear={dismissToolbar}
          {isFullscreen}
          onToggleFullscreen={toggleFullscreen}
          {t}
        />
      </div>
    {:else}
      <p class="font-inter text-sm text-white">{t("reader.formato_no_soportado")}</p>
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

  <!-- Debug tools (inside workspaceRoot so visible in fullscreen) -->
  <DebugToggle />
  <DebugPanel />
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
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="fixed inset-0 z-40" onclick={(e) => { if (e.target === e.currentTarget) showBookmarks = false; }} onkeydown={(e) => e.key === "Escape" && (showBookmarks = false)} role="presentation">
    <div class="absolute inset-0 bg-[#101c2c]/70"></div>
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div bind:this={bookmarksPanelEl} class="absolute right-0 top-0 flex h-full w-65 flex-col border-l border-[#17263a] bg-[#101c2c]/70 pt-15 text-[#8fa3bf] backdrop-blur-sm" onkeydown={(e) => e.key === "Escape" && (showBookmarks = false)} role="dialog" aria-label={t("reader.bookmark")} tabindex="0">
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-[#94adce]/5 px-5 py-4">
        <h2 class="text-base font-bold text-[#f8fbff]">{t("reader.bookmark")}</h2>
        <div class="flex items-center gap-2">
          <button type="button" onclick={() => bookmarksState.addBookmark(activeReadingBook.id, isEpub ? currentEpubChapter + 1 : currentPdfPage || 1)} class="flex h-6 w-6 cursor-pointer items-center justify-center rounded-md bg-[#49d4ff] text-xs font-bold text-[#0B1120] transition-colors hover:bg-[#38bdf8]" title={t("reader.bookmark")}>
            +
          </button>
          <button type="button" onclick={() => (showBookmarks = false)} class="cursor-pointer text-[#8fa3bf] hover:text-white" aria-label={t("settings.close")}>
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
          <p class="text-center text-sm italic text-[#8fa3bf]/60">{t("settings.loadingBookmarks")}</p>
        {:else if bookmarksState.bookmarksList.length === 0}
          <p class="text-center text-sm italic text-[#8fa3bf]/60">{t("settings.noBookmarks")}</p>
        {:else}
          <ul class="flex flex-col gap-2">
            {#each bookmarksState.bookmarksList as bookmark (bookmark.id)}
              <li class="flex items-center gap-2 rounded-lg border border-[#17263a] bg-white/2 px-3 py-2 transition-colors hover:bg-white/5">
                <button type="button" class="flex flex-1 flex-col items-start gap-0.5 text-left" onclick={() => { showBookmarks = false; }}>
                  <span class="text-sm font-medium text-[#f8fbff]">Page {bookmark.pageNumber}</span>
                  {#if bookmark.title}
                    <span class="text-xs text-[#8fa3bf]/60">{bookmark.title}</span>
                  {/if}
                </button>
                <button type="button" onclick={() => bookmarksState.removeBookmark(bookmark.id, activeReadingBook.id)} class="flex h-6 w-6 cursor-pointer items-center justify-center rounded text-sm text-[#8fa3bf] transition-colors hover:bg-red-500/20 hover:text-red-400" title={t("settings.deleteBookmark")}>
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
