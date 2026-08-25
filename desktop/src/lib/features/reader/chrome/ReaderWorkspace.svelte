<script lang="ts">
  import { untrack } from 'svelte';
  import PdfViewer from '../viewer-pdf/PdfViewer.svelte';
  import EpubNativeViewer from '../viewer-epub/EpubNativeViewer.svelte';
  import SearchPanel from '../panels/SearchPanel.svelte';
  import SelectionToolbar from '../highlight/SelectionToolbar.svelte';
  import ReaderTextSettings from './ReaderTextSettings.svelte';
  import ReaderTocPanel, { type TocEntry } from './ReaderTocPanel.svelte';
  import ReaderHeader from './ReaderHeader.svelte';
  import ReaderFooter from './ReaderFooter.svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { ReaderSettings, SearchBookTextResponse } from '$lib/shared/types';
  import type { LibraryBookDto } from '$lib/shared/types/library';
  import type { HighlightActionKind, HighlightActionOpts } from '$lib/shared/types/book';
  import { debugState } from '$lib/shared/debug/debugState.svelte';
  import { addDictionaryWord } from '$lib/shared/api/tauriClient';
  import HighlightContextMenu from '../highlight/HighlightContextMenu.svelte';
  import ColorPickerPopover from '../highlight/ColorPickerPopover.svelte';
  import TagPopover from '../highlight/TagPopover.svelte';
  import NoteEditorModal from '../highlight/NoteEditorModal.svelte';
  import { getReaderError } from '$lib/stores/readerErrorState.svelte';
  import { readerState } from '$lib/shared/stores/ReaderDomainState.svelte';
  import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
  import { clampZoomPercent } from '$lib/features/reader/viewer-pdf/pdfNavigation';
  import { createSpineResolver } from './useSpineResolver.svelte';
  import { createHighlights } from './useHighlights.svelte';
  import { createImmersiveChrome } from './useImmersiveChrome.svelte';
  import { createReaderZoom } from './useReaderZoom.svelte';
  import { createHighlightMenu } from '../highlight/useHighlightMenu.svelte';
  import { createBookmarksPanel } from './useBookmarksPanel.svelte';
  import BookmarkSidebar from './BookmarkSidebar.svelte';

  const outboxDao = new SyncOutboxDao();
  const spineResolver = createSpineResolver();
  const highlightsState = createHighlights({
    getBook: () => activeReadingBook,
    spine: spineResolver,
    outbox: outboxDao,
  });
  const highlightMenuState = createHighlightMenu({ highlights: highlightsState });
  const bookmarksPanel = createBookmarksPanel({ outboxDao });

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

  // Viewer refs for zoom delegation (hoisted before chrome/zoom so getters capture)
  let pdfRef: PdfViewer | null = $state(null);
  let epubRef: EpubNativeViewer | null = $state(null);

  // ── Reader zoom (owns localReaderSettings, 500ms persist debounce, rAF wheel) ──
  const zoom = createReaderZoom({
    getActiveBook: () => activeReadingBook,
    getRefs: () => ({ pdf: pdfRef, epub: epubRef }),
  });

  // Keep local alias for template compat (zoom owns state)
  // Derived alias so existing references keep working; mutations via zoom.handleTextSettingsChange
  let localReaderSettings = $derived(zoom.localReaderSettings);

  // Clean up zoom timers on unmount
  $effect(() => {
    return () => {
      zoom.cleanup();
    };
  });

  // Sync from prop on initial load / external change
  $effect(() => {
    if (readerSettings) {
      zoom.syncFromProps(readerSettings);
    }
  });

  function handleTextSettingsChange(updated: ReaderSettings): void {
    zoom.handleTextSettingsChange(updated);
  }

  // Selection state
  let selectedText = $state('');
  let selectionBounds = $state<{ left: number; top: number; right: number; bottom: number } | null>(
    null,
  );
  let selectionContainer = $state<{
    left: number;
    top: number;
    width: number;
    height: number;
  } | null>(null);
  let showToolbar = $state(false);

  // Persisted highlights are owned by useHighlights (32ms single-flight,
  // optimistic merge, ensureSpine, single outbox). Selection stays here.
  let lastSelectionData = $state<{
    text: string;
    bounds: { left: number; top: number; right: number; bottom: number };
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    pageNumber: number;
    cfi: string | null;
  } | null>(null);

  // Highlight menu delegated to useHighlightMenu (220ms dismiss owned there)

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

  // Highlights delegated to useHighlights (32ms single-flight, ensureSpine,
  // optimistic merge, single outbox). Three triggers: book change,
  // highlightsVersion, highlights:changed window event.
  $effect(() => {
    return () => {
      highlightsState.cleanup();
      highlightMenuState.cleanup();
      bookmarksPanel.cleanup();
    };
  });

  $effect(() => {
    if (activeReadingBook) {
      highlightsState.reloadHighlights();
    } else {
      highlightsState.cleanup();
      highlightsState.persistedHighlights = [];
    }
  });

  $effect(() => {
    const v = readerState.highlightsVersion;
    if (v > 0 && activeReadingBook) {
      console.warn(
        'RW: highlightsVersion changed',
        v,
        'reloading highlights for',
        activeReadingBook.id.slice(0, 4),
      );
      highlightsState.reloadHighlights();
    }
  });

  $effect(() => {
    const handler = (e: Event): void => {
      const detail = (e as CustomEvent).detail as { bookId?: string } | undefined;
      const evBookId = detail?.bookId;
      if (!activeReadingBook) return;
      if (evBookId && evBookId !== activeReadingBook.id) return;
      console.warn(
        'RW: highlights:changed event',
        evBookId?.slice(0, 4) ?? 'all',
        'reloading highlights',
      );
      highlightsState.reloadHighlights();
    };
    window.addEventListener('highlights:changed', handler as EventListener);
    return () => window.removeEventListener('highlights:changed', handler as EventListener);
  });

  // Bug2 diagnostics: log initialLocation prop passed to EpubNativeViewer (non-spam: only on book/chapter change)
  let lastRWInitialLocationLog = $state<string | null>(null);
  let lastRWInitialChapterIdx: number | null = $state(null);
  let lastRWInitialBookId: string | null = $state(null);
  $effect(() => {
    const loc = readerState.cfiLocation;
    const bookId = activeReadingBook?.id ?? null;
    if (!loc || !loc.startsWith('epubcfi(')) {
      // Even empty case: log once per book
      if (bookId && loc !== untrack(() => lastRWInitialLocationLog)) {
        lastRWInitialLocationLog = loc;
        lastRWInitialBookId = bookId;
        console.warn('RW: initialLocation prop=', '(empty)', 'bookId', bookId.slice(0, 4));
      }
      return;
    }
    if (loc === untrack(() => lastRWInitialLocationLog)) return;
    const spineMatch = /epubcfi\(\/6\/(\d+)!/.exec(loc);
    const chapterIdx = spineMatch ? Number.parseInt(spineMatch[1], 10) - 1 : null;
    const prevChapter = untrack(() => lastRWInitialChapterIdx);
    const prevBook = untrack(() => lastRWInitialBookId);
    // Only log when chapter or book changes to avoid scroll spam (CFI intra-chapter)
    if (chapterIdx !== null && chapterIdx === prevChapter && bookId === prevBook) {
      // update still to avoid stale, but don't log
      lastRWInitialLocationLog = loc;
      return;
    }
    lastRWInitialLocationLog = loc;
    lastRWInitialChapterIdx = chapterIdx;
    lastRWInitialBookId = bookId;
    console.warn(
      'RW: initialLocation prop=',
      loc.slice(0, 80),
      'bookId',
      bookId?.slice(0, 4) ?? '(none)',
      'chapterIdx',
      chapterIdx,
    );
  });

  // Search panel state
  let searchPanelOpen = $state(false);

  // Right sidebar panels
  let showTextSettings = $state(false);
  let showTocPanel = $state(false);
  let workspaceRoot: HTMLElement | null = $state(null);
  let panelOpen = $derived(showTextSettings || showTocPanel || bookmarksPanel.showBookmarks || searchPanelOpen);

  // ── Immersive chrome (owns headerVisible 2500ms, hoverTop 72px, edge 80px rAF, fullscreen dual) ──
  const chrome = createImmersiveChrome({ getPanelOpen: () => panelOpen });
  let isFullscreen = $derived(chrome.isFullscreen);
  let headerVisible = $derived(chrome.headerVisible);
  let edgeNavVisible = $derived(chrome.edgeNavVisible);

  function handleWorkspaceMouseLeave(): void {
    chrome.handleWorkspaceMouseLeave();
  }
  function toggleFullscreen(): void {
    chrome.toggleFullscreen();
    syncDebugReaderInfo();
  }
  function handleHeaderFontSizeChange(size: number): void {
    zoom.handleHeaderFontSizeChange(size);
  }

  $effect(() => {
    // react to isFullscreen / panelOpen / hoverTop changes via chrome
    void chrome.isFullscreen;
    void panelOpen;
    void chrome.hoverTop;
    chrome.resetIdleTimer();
    if (!chrome.isFullscreen) chrome.updateEdgeNav(chrome.mouseX);
    else chrome.updateEdgeNav(chrome.mouseX);
  });

  $effect(() => {
    const root = workspaceRoot;
    if (!root) return;
    root.addEventListener('mousemove', chrome.handleWorkspaceMouseMove);
    window.addEventListener('wheel', zoom.handleGlobalWheel as EventListener, {
      capture: true,
      passive: false,
    });
    window.addEventListener('keydown', chrome.handleGlobalKeydown as EventListener);
    window.addEventListener('keydown', zoom.handleGlobalKeydown as EventListener);
    return () => {
      root.removeEventListener('mousemove', chrome.handleWorkspaceMouseMove);
      window.removeEventListener('wheel', zoom.handleGlobalWheel as EventListener, {
        capture: true,
      } as AddEventListenerOptions);
      window.removeEventListener('keydown', chrome.handleGlobalKeydown as EventListener);
      window.removeEventListener('keydown', zoom.handleGlobalKeydown as EventListener);
      chrome.cleanup();
      zoom.cleanup();
    };
  });

  // TOC data from active viewer
  let tocEntries = $state<TocEntry[]>([]);
  let tocNavigate = $state<TocEntry | null>(null);

  // Bookmarks delegated to useBookmarksPanel (ribbon 2200ms owns state)

  // Fullscreen arrows + share helpers — fixed via viewer refs (R2)
  function goPrevPage(): void {
    const fmt = activeReadingBook?.format?.toLowerCase();
    if (fmt === 'pdf') {
      if (!pdfRef) return;
      if (currentPdfPage <= 1) return;
      void pdfRef.navigateToPage(currentPdfPage - 1);
      return;
    }
    if (fmt === 'epub') {
      if (!epubRef) return;
      if (currentEpubChapter <= 0) return;
      epubRef.goToPrev();
    }
  }

  function goNextPage(): void {
    const fmt = activeReadingBook?.format?.toLowerCase();
    if (fmt === 'pdf') {
      if (!pdfRef) return;
      if (currentPdfPage >= totalPdfPages) return;
      void pdfRef.navigateToPage(currentPdfPage + 1);
      return;
    }
    if (fmt === 'epub') {
      if (!epubRef) return;
      epubRef.goToNext();
    }
  }

  async function handleShareText(): Promise<void> {
    if (!selectedText) return;
    const text = selectedText;
    // Try Web Share API, fallback to clipboard with toast
    if (navigator.share) {
      try {
        await navigator.share({ text });
        return;
      } catch {
        // fallthrough to clipboard
      }
    }
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // silent
    }
  }

  // Bookmarks effects (load + focus trap) now owned by BookmarkSidebar component

  // Sync debug readerInfo when relevant state changes (gated)
  function syncDebugReaderInfo(): void {
    if (debugState.enabled) {
      debugState.readerInfo = {
        format: isPdf ? 'pdf' : isEpub ? 'epub' : null,
        isTocOpen: showTocPanel,
        isSearchOpen: searchPanelOpen,
        isFullscreen,
        pageInfo: `${bookProgress}%`,
        scale: 0,
      };
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
    bookmarksPanel.toggleBookmarks();
    syncDebugReaderInfo();
  }

  const isPdf = $derived(activeReadingBook?.format?.toLowerCase() === 'pdf');
  const isEpub = $derived(activeReadingBook?.format?.toLowerCase() === 'epub');
  const bookProgress = $derived(
    isPdf && activeReadingBook?.currentPage && activeReadingBook?.totalPages
      ? Math.round((activeReadingBook.currentPage / activeReadingBook.totalPages) * 100)
      : Math.round(percentage),
  );

  // ── Header unified controls (immersive second row) ─────────
  const headerCurrentPage = $derived(
    isPdf ? currentPdfPage : isEpub ? currentEpubChapter + 1 : 1,
  );
  const headerTotalPages = $derived(
    isPdf ? totalPdfPages : isEpub ? (tocEntries.length > 0 ? tocEntries.length : 0) : 0,
  );
  const headerFontSize = $derived(clampZoomPercent(localReaderSettings.epub.fontSize ?? 100));
  const showHeaderReadingControls = $derived(
    isFullscreen && headerTotalPages > 0 && activeReadingBook !== null,
  );

  async function handleHeaderGoToPage(page: number): Promise<boolean> {
    const fmt = activeReadingBook?.format?.toLowerCase();
    if (fmt === 'pdf') {
      if (!pdfRef) return false;
      return pdfRef.navigateToPage(page);
    }
    if (fmt === 'epub') {
      if (!epubRef) return false;
      return epubRef.handleGoToPage(page);
    }
    return false;
  }

  function handlePdfSelection(event: {
    text: string;
    bounds: { left: number; top: number; right: number; bottom: number };
    container: { left: number; top: number; width: number; height: number };
    placement: string;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    pageNumber: number;
    cfi?: string | null;
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
      cfi: event.cfi ?? null,
    };
    showToolbar = true;
  }

  // ─── Debug: replicate SelectionToolbar's position math so we can show
  //     the user where the toolbar SHOULD be rendered (crosshair + debug
  //     panel). Source of truth is the SelectionToolbar component itself;
  //     we keep this in sync by re-implementing the same constants.
  const DEBUG_TOOLBAR_WIDTH_ESTIMATE = 320;
  const DEBUG_TOOLBAR_HEIGHT_ESTIMATE = 56;
  const DEBUG_TOOLBAR_EDGE_PADDING = 16;
  const DEBUG_TOOLBAR_OFFSET = 16;

  const computedToolbarX = $derived.by(() => {
    if (!selectionBounds || !selectionContainer) return null;
    const center = (selectionBounds.left + selectionBounds.right) / 2;
    const min = DEBUG_TOOLBAR_EDGE_PADDING + DEBUG_TOOLBAR_WIDTH_ESTIMATE / 2;
    const max =
      selectionContainer.width - DEBUG_TOOLBAR_EDGE_PADDING - DEBUG_TOOLBAR_WIDTH_ESTIMATE / 2;
    const anchor = Math.max(min, Math.min(center, max));
    const viewerX = Math.max(0, anchor - DEBUG_TOOLBAR_WIDTH_ESTIMATE / 2);
    return selectionContainer.left + viewerX;
  });

  const computedToolbarY = $derived.by(() => {
    if (!selectionBounds) return null;
    return selectionBounds.top > DEBUG_TOOLBAR_HEIGHT_ESTIMATE + DEBUG_TOOLBAR_OFFSET
      ? selectionBounds.top - DEBUG_TOOLBAR_HEIGHT_ESTIMATE - DEBUG_TOOLBAR_OFFSET
      : selectionBounds.bottom + DEBUG_TOOLBAR_OFFSET;
  });

  $effect(() => {
    debugState.epub.parentState = {
      showToolbar,
      selectedText: selectedText.slice(0, 80),
      selectionBounds,
      selectionContainer,
    };
    debugState.epub.computedToolbarX = computedToolbarX;
    debugState.epub.computedToolbarY = computedToolbarY;
    debugState.epub.persistedHighlightsCount = highlightsState.persistedHighlights.length;
  });

  function handleCopy(): void {
    if (selectedText) {
      navigator.clipboard.writeText(selectedText).catch((err) => {
        console.error('Failed to copy to clipboard:', err);
      });
    }
    // Intentionally NOT calling dismissToolbar() here: the SelectionToolbar
    // shows a small feedback toast for ~1.5s and the parent must stay mounted
    // long enough for the user to see it. The toolbar will close naturally
    // via onselectionclear (browser clears the selection) or when the user
    // clicks outside / makes a new selection.
  }

  async function handleAddToDictionary(word: string): Promise<void> {
    try {
      await addDictionaryWord({ word });
    } catch (err) {
      console.error('Failed to add dictionary word:', err);
    }
    // Intentionally NOT calling dismissToolbar() — same reason as handleCopy:
    // the dictionary feedback toast must remain visible to the user.
  }

  async function handleColorSelect(
    color: string,
    data: NonNullable<typeof lastSelectionData>,
  ): Promise<void> {
    await highlightsState.handleColorSelect(color, data as Parameters<typeof highlightsState.handleColorSelect>[1]);
    // 220ms dismiss delegated to useHighlightMenu (preserves out:scale transition)
    highlightMenuState.scheduleToolbarDismiss(dismissToolbar);
  }

  // Delegated highlight menu helpers (thin wrappers to preserve template call sites)
  function closeHighlightMenu(): void {
    highlightMenuState.closeHighlightMenu();
  }
  function handleHighlightAction(action: HighlightActionKind, id: string, opts?: HighlightActionOpts): void {
    highlightMenuState.handleHighlightAction(action, id, opts);
  }
  function handleMenuCustomColor(): void {
    highlightMenuState.handleMenuCustomColor();
  }
  function handleMenuCopy(): void {
    highlightMenuState.handleMenuCopy();
  }
  function handleMenuTag(): void {
    highlightMenuState.handleMenuTag();
  }
  function handleMenuNote(): void {
    highlightMenuState.handleMenuNote();
  }
  function handleMenuDelete(): void {
    highlightMenuState.handleMenuDelete();
  }
  function handleNoteSave(note: string | null): void {
    highlightMenuState.handleNoteSave(note);
  }
  async function handleTagCreate(name: string, color?: string): Promise<void> {
    await highlightMenuState.handleTagCreate(name, color);
  }
  async function handleTagToggle(tagId: string): Promise<void> {
    await highlightMenuState.handleTagToggle(tagId);
  }
  function handleColorPickerSelect(color: string): void {
    highlightMenuState.handleColorPickerSelect(color);
  }

  function dismissToolbar(): void {
    debugState.epub.dismissToolbarCallCount++;
    debugState.epub.lastDismissTrigger = 'dismissToolbar()';
    showToolbar = false;
    selectedText = '';
    selectionBounds = null;
    selectionContainer = null;
    // Intentionally NOT clearing `lastSelectionData` here. The toolbar keeps
    // a reference to the data via its `selectionData` prop and forwards it
    // through `onColorSelect`. Clearing it on dismiss would race with the
    // browser's selectionchange and drop the highlight before the click
    // handler runs. `removeAllRanges` is also deferred to handleColorSelect
    // to avoid a feedback loop with selectionchange.
  }

  function toggleSearch(): void {
    searchPanelOpen = !searchPanelOpen;
    syncDebugReaderInfo();
  }
</script>

<!-- Full viewport reader layout — immersive: fixed inset-0 z-40 fills viewport -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<section
  class="flex flex-col bg-(--color-bg-deep) {isFullscreen ? 'fixed inset-0 z-40 h-screen w-screen overflow-hidden bg-[#f8f5ec]' : 'h-screen'}"
  bind:this={workspaceRoot}
  onmouseleave={handleWorkspaceMouseLeave}
>
  <ReaderHeader
    title={activeReadingBook?.title ?? ''}
    {showTocPanel}
    {searchPanelOpen}
    {showTextSettings}
    showBookmarks={bookmarksPanel.showBookmarks}
    {isFullscreen}
    headerVisible={isFullscreen ? headerVisible : true}
    {t}
    {onBackToHome}
    onToggleToc={toggleTocPanel}
    onToggleSearch={toggleSearch}
    onToggleTextSettings={toggleTextSettings}
    onToggleBookmarks={toggleBookmarks}
    onToggleFullscreen={toggleFullscreen}
    currentPage={headerCurrentPage}
    totalPages={headerTotalPages}
    currentPercentage={bookProgress}
    fontSizePercent={headerFontSize}
    onPrev={goPrevPage}
    onNext={goNextPage}
    onGoToPage={handleHeaderGoToPage}
    onFontSizeChange={handleHeaderFontSizeChange}
  />
  <!-- Spacer for fixed header — in immersive header floats over content, so hide spacer for true 100vh -->
  {#if !isFullscreen}
    <div class="shrink-0 h-16" aria-hidden="true"></div>
  {/if}

  <!-- Reading area (centered, fill remaining space) — immersive pt accounts for unified header (h-16 + h-12) -->
  <div
    class="flex flex-1 min-h-0 items-stretch justify-center"
    class:px-10={!isFullscreen}
    class:py-6={!isFullscreen}
    class:p-0={isFullscreen}
    class:pt-16={isFullscreen && headerVisible && !showHeaderReadingControls}
    class:pt-28={isFullscreen && headerVisible && showHeaderReadingControls}
  >
    {#if getReaderError()}
      <p class="font-inter text-sm text-(--color-text-inverse)">{getReaderError()}</p>
    {:else if !activeReadingBook}
      <p class="font-inter text-sm text-(--color-text-inverse)">{t('reader.no_book_loaded')}</p>
    {:else if isPdf}
      <!-- White content card for PDF. Fixed height (h-full + items-stretch) so the
           card NEVER grows with the PDF zoom: the PdfViewer's canvas container
           scrolls internally instead of resizing the workspace. -->
      <div
        class="relative bg-white flex flex-col min-h-0 h-full"
        class:rounded-xl={!isFullscreen}
        class:shadow-lg={!isFullscreen}
        class:w-200={!isFullscreen}
        class:w-full={isFullscreen}
        class:h-full={isFullscreen}
      >
        <PdfViewer
          bind:this={pdfRef}
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialPage={Math.max(1, activeReadingBook.currentPage || 1)}
          {searchTargetLocator}
          readerSettings={localReaderSettings}
          preloadedBytes={preloadedBytes?.filePath === activeReadingBook.filePath
            ? preloadedBytes.data
            : null}
          onPageChange={handlePdfPageChange}
          onSessionProgress={onPdfSessionProgress}
          onselection={handlePdfSelection}
          onselectionclear={dismissToolbar}
          onHighlightAction={handleHighlightAction}
          onTocReady={handleTocReady}
          externalTocNavigate={tocNavigate}
          {isFullscreen}
          onToggleFullscreen={toggleFullscreen}
          persistedHighlights={highlightsState.persistedHighlights}
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
          bind:this={epubRef}
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialLocation={readerState.cfiLocation}
          initialPercentage={percentage}
          {searchTargetLocator}
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
          persistedHighlights={highlightsState.persistedHighlights}
          onHighlightAction={handleHighlightAction}
          {t}
        />
      </div>
    {:else}
      <p class="font-inter text-sm text-(--color-text-inverse)">
        {t('reader.formato_no_soportado')}
      </p>
    {/if}

    <!-- Selection Toolbar (floating) -->
    {#if showToolbar && selectionBounds && selectionContainer && selectedText}
      <SelectionToolbar
        {selectedText}
        {selectionBounds}
        containerRect={selectionContainer}
        selectionData={lastSelectionData}
        onCopy={handleCopy}
        onAddToDictionary={handleAddToDictionary}
        onColorSelect={handleColorSelect}
        {t}
      />
    {/if}

    <!-- Debug: crosshair at the computed SelectionToolbar position.
         Only visible when debug mode is enabled. -->
    {#if debugState.enabled && computedToolbarX !== null && computedToolbarY !== null}
      <div
        aria-hidden="true"
        class="pointer-events-none fixed z-9998"
        style="left: {computedToolbarX}px; top: {computedToolbarY}px;"
      >
        <div class="relative">
          <div
            class="absolute -left-1 -top-1 h-3 w-3 rounded-full bg-cyan-400 ring-2 ring-white"
          ></div>
          <div
            class="absolute left-3 top-0 whitespace-nowrap rounded bg-cyan-500 px-1.5 py-0.5 text-micro font-mono text-white shadow"
          >
            toolbar target ({Math.round(computedToolbarX)},{Math.round(computedToolbarY)})
          </div>
        </div>
      </div>
    {/if}

    <!-- Debug: red dashed outline of the actual selection bounds in parent coords.
         Uses selectionBounds (container-relative) + selectionContainer.left/top
         to convert to parent coords for the overlay. -->
    {#if debugState.enabled && selectionBounds && selectionContainer}
      {@const selParentLeft = selectionContainer.left + selectionBounds.left}
      {@const selParentTop = selectionContainer.top + selectionBounds.top}
      {@const selParentWidth = selectionBounds.right - selectionBounds.left}
      {@const selParentHeight = selectionBounds.bottom - selectionBounds.top}
      <div
        aria-hidden="true"
        class="pointer-events-none fixed z-9997 border-2 border-dashed border-red-500"
        style="left: {selParentLeft}px; top: {selParentTop}px; width: {selParentWidth}px; height: {selParentHeight}px;"
      >
        <span
          class="absolute -top-5 left-0 whitespace-nowrap rounded bg-red-500 px-1.5 py-0.5 text-micro font-mono text-white shadow"
        >
          selection bounds ({Math.round(selParentLeft)},{Math.round(selParentTop)})
        </span>
      </div>
    {/if}

    <!-- Menu 2: existing-highlight context menu (EPUB + PDF) -->
    {#if highlightMenuState.highlightMenu.open && highlightMenuState.highlightMenu.highlightId && highlightMenuState.highlightMenu.position}
      <div
        class="fixed inset-0 z-[99]"
        role="presentation"
        onclick={closeHighlightMenu}
        onkeydown={(e) => {
          if (e.key === 'Escape') closeHighlightMenu();
        }}
      >
        <HighlightContextMenu
          highlightId={highlightMenuState.highlightMenu.highlightId}
          position={highlightMenuState.highlightMenu.position}
          assignedTags={highlightMenuState.highlightMenu.assignedTags}
          onCustomColor={handleMenuCustomColor}
          onCopy={handleMenuCopy}
          onTag={handleMenuTag}
          onNote={handleMenuNote}
          onDelete={handleMenuDelete}
          onClose={closeHighlightMenu}
          setColorPickerAnchor={(el) => (highlightMenuState.colorPickerAnchor = el)}
          setTagPopoverAnchor={(el) => (highlightMenuState.tagPopoverAnchor = el)}
          {t}
        />
      </div>
    {/if}

    <ColorPickerPopover
      open={highlightMenuState.showColorPicker}
      anchor={highlightMenuState.colorPickerAnchor}
      currentColor={highlightMenuState.highlightMenu.color}
      onSelect={handleColorPickerSelect}
      onClose={() => (highlightMenuState.showColorPicker = false)}
    />

    <TagPopover
      open={highlightMenuState.showTagPopover}
      anchor={highlightMenuState.tagPopoverAnchor}
      assignedTagIds={highlightMenuState.highlightMenu.assignedTags.map((tag) => tag.id)}
      allTags={highlightMenuState.allTags}
      onCreate={handleTagCreate}
      onToggle={handleTagToggle}
      onClose={() => (highlightMenuState.showTagPopover = false)}
      {t}
    />

    <NoteEditorModal
      open={highlightMenuState.showNoteModal}
      note={highlightMenuState.highlightMenu.highlightId
        ? (highlightsState.persistedHighlights.find((h) => h.id === highlightMenuState.highlightMenu.highlightId)?.note ?? null)
        : null}
      highlightText={highlightMenuState.highlightMenu.text}
      onSave={handleNoteSave}
      onClose={() => (highlightMenuState.showNoteModal = false)}
      {t}
    />
  </div>

  <ReaderFooter
    title={activeReadingBook?.title ?? ''}
    {bookProgress}
    {currentPdfPage}
    {totalPdfPages}
    {isPdf}
    {isFullscreen}
    {t}
  />

  <!-- Bookmark ribbon now owned by BookmarkSidebar (2200ms) -->

  <!-- Fullscreen side arrows — edge 80px rAF, opacity transition, disabled at boundaries (R2) -->
  {#if isFullscreen && activeReadingBook}
    {@const fmt = activeReadingBook.format?.toLowerCase()}
    {@const prevDisabled = fmt === 'pdf' ? currentPdfPage <= 1 : fmt === 'epub' ? currentEpubChapter <= 0 : false}
    {@const nextDisabled = fmt === 'pdf' ? currentPdfPage >= totalPdfPages : false}
    <button
      type="button"
      class="fixed left-4 top-1/2 -translate-y-1/2 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur hover:bg-black/60 transition-opacity duration-200 cursor-pointer {edgeNavVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'} {prevDisabled ? 'opacity-30 cursor-not-allowed' : ''}"
      aria-label={t('reader.prev_page')}
      onclick={goPrevPage}
      disabled={prevDisabled}
      aria-disabled={prevDisabled}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6" /></svg>
    </button>
    <button
      type="button"
      class="fixed right-4 top-1/2 -translate-y-1/2 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur hover:bg-black/60 transition-opacity duration-200 cursor-pointer {edgeNavVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'} {nextDisabled ? 'opacity-30 cursor-not-allowed' : ''}"
      aria-label={t('reader.next_page')}
      onclick={goNextPage}
      disabled={nextDisabled}
      aria-disabled={nextDisabled}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6" /></svg>
    </button>
    <!-- Share overlay button when text selected -->
    {#if selectedText}
      <button
        type="button"
        class="fixed bottom-20 left-1/2 -translate-x-1/2 z-40 flex items-center gap-2 rounded-full bg-(--color-accent-blue) px-4 py-2 text-sm font-medium text-white shadow-lg hover:opacity-90 cursor-pointer"
        onclick={() => void handleShareText()}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"/></svg>
        {t('reader.share')}
      </button>
    {/if}
  {/if}
</section>

<!-- Search Panel overlay -->
{#if searchPanelOpen && activeReadingBook}
  <SearchPanel
    bookId={activeReadingBook.id}
    disabledReason={searchUnavailableReason}
    {isSearching}
    response={searchResponse}
    onSearch={(query, page) => onSearch?.(query, page)}
    onJump={(target) => onSearchJump?.(target)}
    {t}
  />
{/if}

<!-- Text Settings Panel -->
<ReaderTextSettings
  open={showTextSettings}
  format={isPdf ? 'pdf' : isEpub ? 'epub' : 'pdf'}
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

<!-- Bookmarks Sidebar (extracted, 2200ms ribbon owned by panel) -->
<BookmarkSidebar
  bookmarksPanel={bookmarksPanel}
  activeReadingBook={activeReadingBook}
  currentPage={currentPdfPage}
  currentChapter={currentEpubChapter}
  {t}
/>
