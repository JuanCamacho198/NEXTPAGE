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
  import type {
    HighlightActionKind,
    HighlightActionOpts,
    HighlightDto,
  } from '$lib/shared/types/book';
  import { HIGHLIGHT_COLORS } from '$lib/features/reader/highlight/highlightColors';
  import { debugState } from '$lib/shared/debug/debugState.svelte';
  import {
    saveHighlightTags,
    createTag,
    listTags,
    listTagsForHighlight,
    addDictionaryWord,
    upsertReaderSettings,
    getDefaultReaderSettings,
  } from '$lib/shared/api/tauriClient';
  import HighlightContextMenu from '../highlight/HighlightContextMenu.svelte';
  import ColorPickerPopover from '../highlight/ColorPickerPopover.svelte';
  import TagPopover from '../highlight/TagPopover.svelte';
  import NoteEditorModal from '../highlight/NoteEditorModal.svelte';
  import { createFocusTrap } from '$lib/shared/utils/focusTrap';
  import { createBookmarksState } from './bookmarksState.svelte';
  import { getCurrentWebviewWindow } from '@tauri-apps/api/webviewWindow';
  import { getReaderError } from '$lib/stores/readerErrorState.svelte';
  import { readerState } from '$lib/shared/stores/ReaderDomainState.svelte';
  import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
  import { hasEditableContext } from '$lib/features/reader/viewer-epub/keyboardNav';
  import { clampZoomPercent } from '$lib/features/reader/viewer-pdf/pdfNavigation';
  import { createSpineResolver } from './useSpineResolver.svelte';
  import { createHighlights } from './useHighlights.svelte';

  const outboxDao = new SyncOutboxDao();
  const spineResolver = createSpineResolver();
  const highlightsState = createHighlights({
    getBook: () => activeReadingBook,
    spine: spineResolver,
    outbox: outboxDao,
  });

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

  // Menu 2 (existing-highlight context menu) state
  type HighlightMenuState = {
    open: boolean;
    highlightId: string | null;
    color: string;
    text: string;
    position: { x: number; y: number } | null;
    assignedTags: import('$lib/shared/types/book').TagDto[];
  };
  let highlightMenu = $state<HighlightMenuState>({
    open: false,
    highlightId: null,
    color: HIGHLIGHT_COLORS[0].hex,
    text: '',
    position: null,
    assignedTags: [],
  });

  let allTags = $state<import('$lib/shared/types/book').TagDto[]>([]);
  let showColorPicker = $state(false);
  let showTagPopover = $state(false);
  let showNoteModal = $state(false);
  let colorPickerAnchor = $state<HTMLElement | null>(null);
  let tagPopoverAnchor = $state<HTMLElement | null>(null);

  async function refreshTags(): Promise<void> {
    try {
      allTags = await listTags();
    } catch (err) {
      console.error('Failed to load tags:', err);
    }
  }

  async function refreshHighlightTags(highlightId: string): Promise<void> {
    try {
      const tags = await listTagsForHighlight(highlightId);
      highlightMenu.assignedTags = tags;
    } catch (err) {
      console.error('Failed to load highlight tags:', err);
    }
  }

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
  let showBookmarks = $state(false);
  let isFullscreen = $state(false);
  let workspaceRoot: HTMLElement | null = $state(null);

  // ── Immersive chrome state (R1, R2) ───────────────────────
  let headerVisible = $state(true);
  let idleTimer: ReturnType<typeof setTimeout> | null = null;
  let mouseX = $state(0);
  let mouseY = $state(0);
  let pendingFrame: number | null = null;
  let hoverTop = $derived(mouseY < 72);
  let panelOpen = $derived(showTextSettings || showTocPanel || showBookmarks || searchPanelOpen);
  let edgeNavVisible = $state(false);
  // Viewer refs for direct navigation (R2)
  let epubRef: EpubNativeViewer | null = $state(null);
  let pdfRef: PdfViewer | null = $state(null);
  let pendingWheelDelta = 0;
  let pendingWheelFrame: number | null = null;



  // ── Immersive helpers ─────────────────────────────────────
  function resetIdleTimer(): void {
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = null;
    if (!isFullscreen || hoverTop || panelOpen) {
      headerVisible = true;
      return;
    }
    idleTimer = setTimeout(() => {
      if (isFullscreen && !hoverTop && !panelOpen) headerVisible = false;
    }, 2500);
  }

  function updateEdgeNav(x: number): void {
    if (!isFullscreen) {
      edgeNavVisible = false;
      return;
    }
    const w = typeof window !== 'undefined' ? window.innerWidth : 9999;
    const nearEdge = x < 80 || x > w - 80;
    // Hide when mouse is in center zone (80..w-80) — ensures arrows disappear promptly
    edgeNavVisible = nearEdge;
  }

  function handleWorkspaceMouseMove(e: MouseEvent): void {
    if (pendingFrame !== null) return;
    const x = e.clientX;
    const y = e.clientY;
    pendingFrame = requestAnimationFrame(() => {
      pendingFrame = null;
      mouseX = x;
      mouseY = y;
      updateEdgeNav(x);
      // Hover top 72px shows unified header in immersive only
      if (isFullscreen && y < 72) headerVisible = true;
      resetIdleTimer();
    });
  }

  function handleWorkspaceMouseLeave(): void {
    if (pendingFrame !== null) {
      cancelAnimationFrame(pendingFrame);
      pendingFrame = null;
    }
    edgeNavVisible = false;
    // Do not force headerVisible false here — idle timer owns hiding
  }

  function adjustZoom(delta: number): void {
    const current = clampZoomPercent(localReaderSettings.epub.fontSize ?? 100);
    const next = clampZoomPercent(current + delta);
    if (next === current) return;
    const updated: ReaderSettings = {
      ...localReaderSettings,
      epub: { ...localReaderSettings.epub, fontSize: next },
    };
    handleTextSettingsChange(updated);
    const fmt = activeReadingBook?.format?.toLowerCase();
    if (pdfRef && fmt === 'pdf') {
      void pdfRef.setScale(next / 100);
    }
    if (epubRef && fmt === 'epub') {
      void epubRef.setZoom(next);
    }
  }

  function handleGlobalWheel(e: WheelEvent): void {
    if (!e.ctrlKey && !e.metaKey) return;
    e.preventDefault();
    pendingWheelDelta += e.deltaY;
    if (pendingWheelFrame !== null) return;
    pendingWheelFrame = requestAnimationFrame(() => {
      pendingWheelFrame = null;
      const delta = pendingWheelDelta > 0 ? -10 : 10;
      pendingWheelDelta = 0;
      adjustZoom(delta);
    });
  }

  function handleGlobalKeydown(e: KeyboardEvent): void {
    // Escape exits immersive
    if (e.key === 'Escape' && isFullscreen) {
      // Don't swallow if a panel/modal is open — they handle Escape themselves
      if (!panelOpen) {
        e.preventDefault();
        toggleFullscreen();
        return;
      }
    }
    // Ctrl+Shift+F = Tauri window fullscreen (optional, immersive + window)
    if (e.key.toLowerCase() === 'f' && (e.ctrlKey || e.metaKey) && e.shiftKey && !hasEditableContext(e.target as Element | null)) {
      e.preventDefault();
      void toggleWindowFullscreen();
      return;
    }
    // F toggle immersive CSS fullscreen outside editable context
    if (e.key.toLowerCase() === 'f' && !hasEditableContext(e.target as Element | null)) {
      if (e.ctrlKey || e.metaKey || e.altKey || e.shiftKey) return;
      e.preventDefault();
      toggleFullscreen();
      return;
    }
    // Ctrl/Cmd + +/- zoom
    if ((e.ctrlKey || e.metaKey) && (e.key === '=' || e.key === '+' || e.key === '-' || e.key === '_' )) {
      e.preventDefault();
      const step = e.key === '-' || e.key === '_' ? -10 : 10;
      adjustZoom(step);
    }
  }

  $effect(() => {
    resetIdleTimer();
    // react to isFullscreen / panelOpen changes
    void isFullscreen;
    void panelOpen;
    void hoverTop;
    if (!isFullscreen) edgeNavVisible = false;
    else updateEdgeNav(mouseX);
  });

  $effect(() => {
    const root = workspaceRoot;
    if (!root) return;
    root.addEventListener('mousemove', handleWorkspaceMouseMove);
    window.addEventListener('wheel', handleGlobalWheel as EventListener, { capture: true, passive: false });
    window.addEventListener('keydown', handleGlobalKeydown as EventListener);
    return () => {
      root.removeEventListener('mousemove', handleWorkspaceMouseMove);
      window.removeEventListener('wheel', handleGlobalWheel as EventListener, { capture: true } as AddEventListenerOptions);
      window.removeEventListener('keydown', handleGlobalKeydown as EventListener);
      if (idleTimer) clearTimeout(idleTimer);
      if (pendingFrame !== null) cancelAnimationFrame(pendingFrame);
      if (pendingWheelFrame !== null) cancelAnimationFrame(pendingWheelFrame);
    };
  });

  // TOC data from active viewer
  let tocEntries = $state<TocEntry[]>([]);
  let tocNavigate = $state<TocEntry | null>(null);

  // Bookmarks state — single outboxDao injected (PR1)
  let bookmarksState = createBookmarksState({ outboxDao });
  let bookmarksPanelEl: HTMLDivElement | undefined = $state();

  // Bookmark ribbon overlay (2200ms)
  let showBookmarkRibbon = $state(false);
  let ribbonTimer: ReturnType<typeof setTimeout> | null = null;

  function triggerBookmarkRibbon(): void {
    showBookmarkRibbon = true;
    if (ribbonTimer) clearTimeout(ribbonTimer);
    ribbonTimer = setTimeout(() => {
      showBookmarkRibbon = false;
    }, 2200);
  }

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
        format: isPdf ? 'pdf' : isEpub ? 'epub' : null,
        isTocOpen: showTocPanel,
        isSearchOpen: searchPanelOpen,
        isFullscreen,
        pageInfo: `${bookProgress}%`,
        scale: 0,
      };
    }
  }

  // Immersive CSS fullscreen (F) — no Tauri window decorations change
  function toggleFullscreen(): void {
    const next = !isFullscreen;
    isFullscreen = next;
    readerState.isFullscreen = next;
    if (next) {
      // entering immersive: hide header unless mouse is at top
      headerVisible = hoverTop;
      updateEdgeNav(mouseX);
      resetIdleTimer();
    } else {
      headerVisible = true;
      edgeNavVisible = false;
      if (idleTimer) {
        clearTimeout(idleTimer);
        idleTimer = null;
      }
    }
    syncDebugReaderInfo();
  }

  // Tauri window fullscreen (Ctrl+Shift+F) — optional, independent of CSS immersive
  async function toggleWindowFullscreen(): Promise<void> {
    try {
      const cur = await appWindow.isFullscreen();
      await appWindow.setFullscreen(!cur);
    } catch {
      console.warn('Tauri window fullscreen API not available');
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

  function handleHeaderFontSizeChange(size: number): void {
    const clamped = clampZoomPercent(size);
    const updated: ReaderSettings = {
      ...localReaderSettings,
      epub: { ...localReaderSettings.epub, fontSize: clamped },
    };
    handleTextSettingsChange(updated);
    const fmt = activeReadingBook?.format?.toLowerCase();
    if (pdfRef && fmt === 'pdf') {
      void pdfRef.setScale(clamped / 100);
    }
    if (epubRef && fmt === 'epub') {
      void epubRef.setZoom(clamped);
    }
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
    // Delegate to highlightsState — preserves 220ms race: captured data is
    // passed explicitly, not global lastSelectionData which may be nulled by
    // selectionchange before click handler runs.
    await highlightsState.handleColorSelect(color, data as Parameters<typeof highlightsState.handleColorSelect>[1]);
    // Dismiss toolbar 220ms after color pick (matches highlightsState's
    // removeAllRanges delay) to preserve out:scale transition.
    setTimeout(() => {
      dismissToolbar();
    }, 220);
  }

  function openHighlightMenu(id: string, opts?: HighlightActionOpts): void {
    const hl = highlightsState.persistedHighlights.find((h) => h.id === id);
    highlightMenu = {
      open: true,
      highlightId: id,
      color: opts?.color ?? hl?.color ?? HIGHLIGHT_COLORS[0].hex,
      text: opts?.text ?? hl?.text ?? '',
      position:
        opts?.x !== undefined && opts?.y !== undefined
          ? { x: opts.x, y: opts.y }
          : { x: window.innerWidth / 2, y: window.innerHeight / 2 },
      assignedTags: [],
    };
    void refreshTags();
    if (hl) {
      refreshHighlightTags(id);
    }
  }

  function closeHighlightMenu(): void {
    highlightMenu = {
      open: false,
      highlightId: null,
      color: HIGHLIGHT_COLORS[0].hex,
      text: '',
      position: null,
      assignedTags: [],
    };
    showColorPicker = false;
    showTagPopover = false;
    showNoteModal = false;
  }

  function handleHighlightAction(
    action: HighlightActionKind,
    id: string,
    opts?: HighlightActionOpts,
  ): void {
    if (action === 'open') {
      openHighlightMenu(id, opts);
      return;
    }
    if (action === 'close') {
      closeHighlightMenu();
      return;
    }
    if (action === 'updateColor' && opts?.color) {
      updateHighlightColor(id, opts.color);
      return;
    }
    if (action === 'delete') {
      deleteHighlightById(id);
      return;
    }
  }

  function updateHighlightColor(id: string, color: string): void {
    highlightsState.updateHighlightColor(id, color);
    if (highlightMenu.highlightId === id) {
      highlightMenu.color = color;
    }
  }

  function updateHighlightNote(id: string, note: string | null): void {
    highlightsState.updateHighlightNote(id, note);
  }

  function deleteHighlightById(id: string): void {
    highlightsState.deleteHighlightById(id);
    closeHighlightMenu();
  }

  function enqueueHighlightUpdate(id: string, changes: { color?: string; note?: string | null }): void {
    highlightsState.enqueueHighlightUpdate(id, changes);
  }

  function handleMenuCustomColor(): void {
    showColorPicker = !showColorPicker;
  }

  function handleMenuCopy(): void {
    if (highlightMenu.text) {
      navigator.clipboard.writeText(highlightMenu.text);
    }
    closeHighlightMenu();
  }

  function handleMenuTag(): void {
    showTagPopover = !showTagPopover;
  }

  function handleMenuNote(): void {
    showNoteModal = true;
  }

  function handleMenuDelete(): void {
    if (!highlightMenu.highlightId) return;
    deleteHighlightById(highlightMenu.highlightId);
  }

  function handleNoteSave(note: string | null): void {
    if (!highlightMenu.highlightId) return;
    updateHighlightNote(highlightMenu.highlightId, note);
    showNoteModal = false;
  }

  async function handleTagCreate(name: string, color?: string): Promise<void> {
    try {
      const tag = await createTag({ name, color });
      allTags = [...allTags, tag];
      if (highlightMenu.highlightId) {
        const currentIds = highlightMenu.assignedTags.map((t) => t.id);
        const updated = await saveHighlightTags({
          highlightId: highlightMenu.highlightId,
          tagIds: [...currentIds, tag.id],
        });
        highlightMenu.assignedTags = updated;
      }
    } catch (err) {
      console.error('Failed to create tag:', err);
    }
  }

  async function handleTagToggle(tagId: string): Promise<void> {
    if (!highlightMenu.highlightId) return;
    const currentIds = new Set(highlightMenu.assignedTags.map((t) => t.id));
    if (currentIds.has(tagId)) {
      currentIds.delete(tagId);
    } else {
      currentIds.add(tagId);
    }
    try {
      const updated = await saveHighlightTags({
        highlightId: highlightMenu.highlightId,
        tagIds: Array.from(currentIds),
      });
      highlightMenu.assignedTags = updated;
    } catch (err) {
      console.error('Failed to save highlight tags:', err);
    }
  }

  function handleColorPickerSelect(color: string): void {
    if (!highlightMenu.highlightId) return;
    updateHighlightColor(highlightMenu.highlightId, color);
    showColorPicker = false;
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
    {showBookmarks}
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
    {#if highlightMenu.open && highlightMenu.highlightId && highlightMenu.position}
      <div
        class="fixed inset-0 z-[99]"
        role="presentation"
        onclick={closeHighlightMenu}
        onkeydown={(e) => {
          if (e.key === 'Escape') closeHighlightMenu();
        }}
      >
        <HighlightContextMenu
          highlightId={highlightMenu.highlightId}
          position={highlightMenu.position}
          assignedTags={highlightMenu.assignedTags}
          onCustomColor={handleMenuCustomColor}
          onCopy={handleMenuCopy}
          onTag={handleMenuTag}
          onNote={handleMenuNote}
          onDelete={handleMenuDelete}
          onClose={closeHighlightMenu}
          setColorPickerAnchor={(el) => (colorPickerAnchor = el)}
          setTagPopoverAnchor={(el) => (tagPopoverAnchor = el)}
          {t}
        />
      </div>
    {/if}

    <ColorPickerPopover
      open={showColorPicker}
      anchor={colorPickerAnchor}
      currentColor={highlightMenu.color}
      onSelect={handleColorPickerSelect}
      onClose={() => (showColorPicker = false)}
    />

    <TagPopover
      open={showTagPopover}
      anchor={tagPopoverAnchor}
      assignedTagIds={highlightMenu.assignedTags.map((tag) => tag.id)}
      {allTags}
      onCreate={handleTagCreate}
      onToggle={handleTagToggle}
      onClose={() => (showTagPopover = false)}
      {t}
    />

    <NoteEditorModal
      open={showNoteModal}
      note={highlightMenu.highlightId
        ? (highlightsState.persistedHighlights.find((h) => h.id === highlightMenu.highlightId)?.note ?? null)
        : null}
      highlightText={highlightMenu.text}
      onSave={handleNoteSave}
      onClose={() => (showNoteModal = false)}
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

  <!-- Bookmark ribbon overlay (2200ms) -->
  {#if showBookmarkRibbon}
    <div class="pointer-events-none fixed top-20 right-8 z-50 animate-[bookmarkRibbon_2200ms_ease-out] flex items-center gap-2 rounded-xl border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-medium text-amber-900 shadow-lg">
      <span class="text-amber-600">🔖</span>
      {t('reader.bookmarkAdded')}
    </div>
  {/if}

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

<style>
  @keyframes bookmarkRibbon {
    0% { opacity: 0; transform: translateY(-12px) scale(0.9); }
    15% { opacity: 1; transform: translateY(0) scale(1); }
    85% { opacity: 1; transform: translateY(0) scale(1); }
    100% { opacity: 0; transform: translateY(-12px) scale(0.9); }
  }
</style>

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

<!-- Bookmarks Sidebar Panel -->
{#if showBookmarks && activeReadingBook}
  <div
    class="fixed inset-0 z-40"
    onclick={(e) => {
      if (e.target === e.currentTarget) showBookmarks = false;
    }}
    onkeydown={(e) => e.key === 'Escape' && (showBookmarks = false)}
    role="presentation"
  >
    <div class="absolute inset-0 bg-(--color-surface)/70"></div>
    <div
      bind:this={bookmarksPanelEl}
      class="absolute right-0 top-0 flex h-full w-65 flex-col border-l border-(--color-border-deep) bg-(--color-surface)/70 pt-15 text-(--color-text-muted) backdrop-blur-sm"
      onkeydown={(e) => e.key === 'Escape' && (showBookmarks = false)}
      role="dialog"
      aria-label={t('reader.bookmark')}
      tabindex="0"
    >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-(--color-border)/5 px-5 py-4">
        <h2 class="text-base font-bold text-(--color-primary)">{t('reader.bookmark')}</h2>
        <div class="flex items-center gap-2">
          <button
            type="button"
            onclick={() => {
              void bookmarksState.addBookmark(
                activeReadingBook.id,
                isEpub ? currentEpubChapter + 1 : currentPdfPage || 1,
                { cfiLocation: readerState.cfiLocation, locatorJson: readerState.locatorJson },
              );
              triggerBookmarkRibbon();
            }}
            class="flex h-6 w-6 cursor-pointer items-center justify-center rounded-md bg-(--color-accent-blue) text-xs font-bold text-(--color-bg-deep) transition-colors hover:bg-(--color-accent-sky)"
            title={t('reader.bookmark')}
          >
            +
          </button>
          <button
            type="button"
            onclick={() => (showBookmarks = false)}
            class="cursor-pointer text-(--color-text-muted) hover:text-(--color-text-inverse)"
            aria-label={t('settings.close')}
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto p-4">
        {#if bookmarksState.bookmarksLoading}
          <p class="text-center text-sm italic text-(--color-text-muted)/60">
            {t('settings.loadingBookmarks')}
          </p>
        {:else if bookmarksState.bookmarksList.length === 0}
          <p class="text-center text-sm italic text-(--color-text-muted)/60">
            {t('settings.noBookmarks')}
          </p>
        {:else}
          <ul class="flex flex-col gap-2">
            {#each bookmarksState.bookmarksList as bookmark (bookmark.id)}
              <li
                class="flex items-center gap-2 rounded-lg border border-(--color-border-deep) bg-(--color-text-inverse)/2 px-3 py-2 transition-colors hover:bg-(--color-text-inverse)/5"
              >
                <button
                  type="button"
                  class="flex flex-1 flex-col items-start gap-0.5 text-left"
                  onclick={() => {
                    showBookmarks = false;
                  }}
                >
                  <span class="text-sm font-medium text-(--color-primary)"
                    >Page {bookmark.pageNumber}</span
                  >
                  {#if bookmark.title}
                    <span class="text-xs text-(--color-text-muted)/60">{bookmark.title}</span>
                  {/if}
                </button>
                <button
                  type="button"
                  onclick={() => bookmarksState.removeBookmark(bookmark.id, activeReadingBook.id)}
                  class="flex h-6 w-6 cursor-pointer items-center justify-center rounded text-sm text-(--color-text-muted) transition-colors hover:bg-red-500/20 hover:text-red-400"
                  title={t('settings.deleteBookmark')}
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path
                      d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"
                    ></path>
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
