<script lang="ts">
  import PdfViewer from "./PdfViewer.svelte";
  import EpubViewer from "./EpubViewer.svelte";
  import SearchPanel from "./SearchPanel.svelte";
  import SelectionToolbar from "./SelectionToolbar.svelte";
  import ReaderTextSettings from "./ReaderTextSettings.svelte";
  import ReaderTocPanel, { type TocEntry } from "./ReaderTocPanel.svelte";
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import type { MessageKey } from "$lib/i18n";
  import type { ReaderSettings } from "$lib/shared/types";
  import type { LibraryBookDto } from "$lib/shared/types/library";
  import { debugState } from "$lib/debug/debugState.svelte";
  import { saveHighlight, deleteHighlight } from "$lib/shared/api/tauriClient";
  import DebugToggle from "$lib/debug/DebugToggle.svelte";
  import DebugPanel from "$lib/debug/DebugPanel.svelte";

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

  function handlePdfPageChange(page: number, total: number) {
    currentPdfPage = page;
    totalPdfPages = total;
    onPdfPageChange?.(page, total);
  }

  // Search panel state
  let searchPanelOpen = $state(false);

  // Right sidebar panels
  let showTextSettings = $state(false);
  let showTocPanel = $state(false);
  let isFullscreen = $state(false);
  let workspaceRoot: HTMLDivElement | null = $state(null);

  // TOC data from active viewer
  let tocEntries = $state<TocEntry[]>([]);
  let tocNavigate = $state<TocEntry | null>(null);
  let activeTocId = $state("");

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
  <!-- Header (64px) - hidden in fullscreen -->
  <header class="flex h-16 shrink-0 items-center justify-between border-b border-[#1E293B] px-8" class:hidden={isFullscreen}>
    <!-- Left: back + biblioteca -->
    <div class="flex items-center gap-2">
      <button type="button" onclick={onBackToHome} class="flex cursor-pointer items-center gap-2 text-[#94A3B8] hover:text-white">
        <Icon name="chevron-left" size="sm" />
        <span class="font-inter text-sm font-medium text-[#94A3B8]">{t("reader.biblioteca")}</span>
      </button>
    </div>

    <!-- Center: book title -->
    <span class="font-inter text-sm font-medium text-[#94A3B8]">
      {activeReadingBook?.title ?? ""}
    </span>

    <!-- Right: tools -->
    <div class="flex items-center gap-6 text-[#94A3B8]">
      <button type="button" onclick={toggleTocPanel} class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label={t("reader.tabla_contenidos")}>
        <Icon name="menu" size="sm" />
      </button>
      <button type="button" onclick={toggleSearch} class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label={t("epub.search")}>
        <Icon name="search" size="sm" />
      </button>
      <button type="button" onclick={toggleTextSettings} class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label={t("reader.ajustes_texto")}>
        <Icon name="settings" size="sm" />
      </button>
      <button type="button" class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label={t("reader.bookmark")}>
        <Icon name="bookmark" size="sm" />
      </button>
      <button type="button" onclick={toggleFullscreen} class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label={isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}>
        <Icon name={isFullscreen ? "fullscreen-exit" : "fullscreen-enter"} size="sm" />
      </button>
    </div>
  </header>

  <!-- Reading area (centered, fill remaining space) -->
  <div class="flex flex-1 items-center justify-center" class:px-10={!isFullscreen} class:p-0={isFullscreen}>
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
        class:w-[800px]={!isFullscreen}
        class:w-full={isFullscreen}
      >
        <PdfViewer
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialPage={Math.max(1, activeReadingBook.currentPage || 1)}
          searchTargetLocator={searchTargetLocator}
          selectionColor={selectedColor}
          readerSettings={readerSettings}
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
      <div class="relative w-[800px] rounded-xl bg-white shadow-lg">
        <EpubViewer
          filePath={activeReadingBook.filePath}
          initialLocation={cfiLocation}
          initialPercentage={percentage}
          searchTargetLocator={searchTargetLocator}
          readerSettings={readerSettings}
          onLocationContext={onReaderLocationContext}
          onLocationChange={onEpubLocationChange}
          onTocReady={handleTocReady}
          externalTocNavigate={tocNavigate}
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

  <!-- Footer (48px) - hidden in fullscreen -->
  <footer class="flex h-12 shrink-0 items-center justify-between border-t border-[#1E293B] px-8" class:hidden={isFullscreen}>
    <span class="font-inter text-xs font-normal text-[#94A3B8]">
      {activeReadingBook?.title ?? ""}
    </span>
    {#if isPdf && totalPdfPages > 0}
      <div class="flex items-center gap-3">
        <span class="font-inter text-xs font-normal text-[#94A3B8]">{totalPdfPages - currentPdfPage} {t("pdf.pagesLeft")}</span>
        <div class="h-2 w-[200px] rounded-full bg-[#1E293B]">
          <div
            class="h-full rounded-full bg-[#38BDF8] transition-all duration-300"
            style="width: {Math.round((currentPdfPage / totalPdfPages) * 100)}%"
          ></div>
        </div>
        <span class="font-inter text-xs font-normal text-[#94A3B8]">{Math.round((currentPdfPage / totalPdfPages) * 100)}%</span>
      </div>
    {:else}
      <div class="flex items-center gap-3">
        <div class="h-2 w-[200px] rounded-full bg-[#1E293B]">
          <div
            class="h-full rounded-full bg-[#38BDF8] transition-all duration-300"
            style="width: {bookProgress}%"
          ></div>
        </div>
        <span class="font-inter text-xs font-normal text-[#94A3B8]">{bookProgress}%</span>
      </div>
    {/if}
  </footer>

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
