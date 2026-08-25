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
    saveHighlight,
    deleteHighlight,
    updateHighlight,
    saveHighlightTags,
    createTag,
    listTags,
    listTagsForHighlight,
    addDictionaryWord,
    upsertReaderSettings,
    getDefaultReaderSettings,
    listHighlights,
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
  import { authState } from '$lib/stores/authState.svelte';
  import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
  import { invoke } from '@tauri-apps/api/core';
  import { normalizeHref } from '$lib/shared/sync/LocatorCodec';
  import { stripFragment } from '$lib/features/reader/viewer-epub/epubViewerHelpers';

  const outboxDao = new SyncOutboxDao();

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

  // Persisted highlights state. We carry the full HighlightDto for
  // EPUB (we need `cfi` to round-trip selections back to the
  // iframe's cfiToRange), and a slim shape for the in-memory PDF
  // overlay rendering. Both share the same key set we use to look
  // up "this highlight" inside the EPUB iframe.
  type PersistedHighlight = {
    id: string;
    color: string;
    pageNumber: number;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    cfi?: string | null;
    text?: string;
    note?: string | null;
  };
  let persistedHighlights = $state<PersistedHighlight[]>([]);
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

  // Load persisted highlights when the active book changes. We use the
  // slim PersistedHighlight shape (the EPUB viewer needs cfi; the PDF
  // viewer needs rects but ignores cfi). This is best-effort: on
  // failure we keep the in-memory list empty and log.
  // BUG1 FIX: guard against overwriting optimistic push if listHighlights
  // races after saveHighlight. Merge any highlight IDs already in
  // persistedHighlights that aren't yet in DB rows.
  // FIX highlights stale epoch — 2026-08-23: removed aggressive epoch guard
  // that discarded valid reloads when highlightsVersion bump + highlights:changed
  // fired two concurrent reloadHighlights in the same microtask (myEpoch 1 vs
  // 2: first was marked stale even though second still returned stale 0 from
  // a DB read that started before the 17-row upsert committed). Replaced with
  // debounced single-flight coalescing: duplicate triggers within 32 ms collapse
  // to ONE DB read, and a load already in flight queues exactly one follow-up.
  let highlightReloadTimer: ReturnType<typeof setTimeout> | null = null;
  let highlightReloadInFlight = false;
  let highlightReloadQueued = false;

  // Spine cache for readium: href -> spine index mapping
  let epubSpineHrefs = $state<string[]>([]);
  let epubSpineLoadedFor = $state<string | null>(null);

  function getSpineIndexForHref(href: string, spine: string[]): number | null {
    const raw = href.trim();
    const withoutPrefix = raw.startsWith('readium:') ? raw.slice('readium:'.length) : raw;
    const fragStripped = stripFragment(withoutPrefix);
    // normalize: backslash -> /, collapse //
    let norm = normalizeHref(fragStripped);
    // collapse // iteratively (preserve :// not needed here)
    while (norm.includes('//')) norm = norm.replace('//', '/');
    norm = norm.trim();
    if (!norm) return null;
    let idx = spine.findIndex((h) => normalizeHref(h) === norm);
    if (idx !== -1) return idx;
    // suffix / filename fallback (handles OEBPS/Text/cap1.xhtml vs Text/cap1.xhtml)
    const fileName = norm.split('/').pop() ?? '';
    if (fileName) {
      idx = spine.findIndex(
        (h) => normalizeHref(h).endsWith('/' + fileName) || normalizeHref(h).split('/').pop() === fileName,
      );
      if (idx !== -1) return idx;
    }
    idx = spine.findIndex((h) => {
      const nh = normalizeHref(h);
      return nh.endsWith(norm) || norm.endsWith(nh);
    });
    return idx !== -1 ? idx : null;
  }

  async function ensureSpineHrefs(bookId: string, filePath: string): Promise<void> {
    if (epubSpineLoadedFor === bookId && epubSpineHrefs.length > 0) return;
    try {
      const meta = await invoke<{
        spineHrefs?: string[];
        spine_hrefs?: string[];
        toc?: Array<{ href: string }>;
        chapters?: Array<{ href: string }>;
        totalChapters: number;
      }>('parse_epub', { filePath, bookId });
      const raw = meta.spineHrefs ?? meta.spine_hrefs ?? [];
      if (Array.isArray(raw) && raw.length > 0) {
        epubSpineHrefs = raw.map((h) => normalizeHref(h));
        epubSpineLoadedFor = bookId;
        console.warn(
          'RW: spine loaded',
          epubSpineHrefs.length,
          'for',
          bookId.slice(0, 4),
          epubSpineHrefs.slice(0, 3).join(','),
        );
      }
    } catch (e) {
      console.warn('RW: failed to load spine for readium fix', e);
    }
  }

  function reloadHighlights(): void {
    if (highlightReloadTimer) clearTimeout(highlightReloadTimer);
    highlightReloadTimer = setTimeout(() => {
      highlightReloadTimer = null;
      void runReloadHighlights();
    }, 32);
  }

  async function runReloadHighlights(): Promise<void> {
    if (highlightReloadInFlight) {
      highlightReloadQueued = true;
      return;
    }
    highlightReloadInFlight = true;
    try {
      do {
        highlightReloadQueued = false;
        const book = untrack(() => activeReadingBook);
        if (!book) {
          persistedHighlights = [];
          break;
        }
        const bookId = book.id;
        try {
          const rows: HighlightDto[] = await listHighlights(bookId);
          if (untrack(() => activeReadingBook?.id) !== bookId) {
            console.debug('RW: listHighlights stale bookId ignored', bookId.slice(0, 4));
            continue;
          }
          console.warn(
            'RW: listHighlights loaded',
            rows.length,
            rows.map((r) => `${r.pageNumber}:${r.id.slice(0, 4)}`).join(','),
            'bookId',
            bookId.slice(0, 4),
          );
          const rowIds = new Set(rows.map((r) => r.id));
          const optimistic = untrack(() => persistedHighlights).filter((h) => !rowIds.has(h.id));
          // Ensure spine is available for readium: href -> spine mapping (fixes cap1.xhtml -> 6)
          if (book.format?.toLowerCase() === 'epub' && book.filePath) {
            await ensureSpineHrefs(bookId, book.filePath);
          }
          let merged: PersistedHighlight[] = rows.map((r) => {
            let pageNumber = r.pageNumber;
            if (r.cfi) {
              const cfiTrim = r.cfi.trim();
              const m = /epubcfi\(\/6\/(\d+)!/.exec(cfiTrim);
              if (m) {
                const idx = parseInt(m[1], 10) - 1;
                if (idx >= 0 && idx !== pageNumber) {
                  console.warn(
                    'RW: fixing page mismatch',
                    r.id.slice(0, 4),
                    `page ${r.pageNumber} -> ${idx}`,
                  );
                  pageNumber = idx;
                  void updateHighlight({ id: r.id, pageNumber }).catch(() => {});
                }
              } else if (cfiTrim.startsWith('readium:')) {
                const spineIdx = getSpineIndexForHref(cfiTrim, epubSpineHrefs);
                if (spineIdx !== null && spineIdx >= 0 && spineIdx !== pageNumber) {
                  console.warn(
                    'RW: fixing page mismatch (readium)',
                    r.id.slice(0, 4),
                    `page ${r.pageNumber} -> ${spineIdx}`,
                    `href ${cfiTrim.slice(0, 40)}`,
                  );
                  pageNumber = spineIdx;
                  void updateHighlight({ id: r.id, pageNumber }).catch(() => {});
                } else if (spineIdx === null) {
                  console.warn(
                    'RW: readium href not in spine',
                    r.id.slice(0, 4),
                    cfiTrim.slice(0, 60),
                    'spineLen',
                    epubSpineHrefs.length,
                  );
                }
              }
            }
            return {
              id: r.id,
              color: r.color,
              pageNumber,
              rects: [],
              cfi: r.cfi ?? null,
              text: r.text,
              note: r.note ?? null,
            };
          });
          if (optimistic.length > 0) {
            console.warn(
              'RW: preserving',
              optimistic.length,
              'optimistic highlights not yet in DB',
              optimistic.map((o) => `${o.pageNumber}:${o.id.slice(0, 4)}`).join(','),
            );
            merged = [...merged, ...optimistic];
          }
          persistedHighlights = merged;
        } catch (err) {
          console.error('Failed to load highlights:', err);
        }
      } while (highlightReloadQueued);
    } finally {
      highlightReloadInFlight = false;
    }
  }

  // Cleanup debounced timer on unmount
  $effect(() => {
    return () => {
      if (highlightReloadTimer) clearTimeout(highlightReloadTimer);
    };
  });

  // Initial load on book change
  $effect(() => {
    if (activeReadingBook) {
      void reloadHighlights();
    } else {
      if (highlightReloadTimer) {
        clearTimeout(highlightReloadTimer);
        highlightReloadTimer = null;
      }
      persistedHighlights = [];
    }
  });

  // Reload when remote sync finishes (fetchAndApplyBookState bump)
  $effect(() => {
    const v = readerState.highlightsVersion;
    if (v > 0 && activeReadingBook) {
      console.warn(
        'RW: highlightsVersion changed',
        v,
        'reloading highlights for',
        activeReadingBook.id.slice(0, 4),
      );
      void reloadHighlights();
    }
  });

  // Fallback: window event emitted by ReaderDomainState after sync
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
      void reloadHighlights();
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
  let isRotated = $state(false);
  let workspaceRoot: HTMLElement | null = $state(null);

  function handleRotate(): void {
    isRotated = !isRotated;
  }

  $effect(() => {
    return () => {
      isRotated = false;
    };
  });

  // TOC data from active viewer
  let tocEntries = $state<TocEntry[]>([]);
  let tocNavigate = $state<TocEntry | null>(null);

  // Bookmarks state
  let bookmarksState = createBookmarksState();
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

  // Fullscreen arrows + share helpers
  function goPrevPage(): void {
    // For PDF: decrement, for EPUB: previous chapter via keyboard intent handled in viewers
    if (isPdf && currentPdfPage > 1) {
      const next = currentPdfPage - 1;
      handlePdfPageChange(next, totalPdfPages);
    } else if (isEpub && currentEpubChapter > 0) {
      const prev = currentEpubChapter - 1;
      // EPUB navigation via TOC-like jump handled externally; emit via window event for viewer
      window.dispatchEvent(new CustomEvent('reader:navigate', { detail: { direction: 'prev', chapter: prev } }));
    }
  }

  function goNextPage(): void {
    if (isPdf && currentPdfPage < totalPdfPages) {
      const next = currentPdfPage + 1;
      handlePdfPageChange(next, totalPdfPages);
    } else if (isEpub) {
      const next = currentEpubChapter + 1;
      window.dispatchEvent(new CustomEvent('reader:navigate', { detail: { direction: 'next', chapter: next } }));
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

  // Fullscreen toggle using Tauri Window API (reliable in Tauri webview)
  async function toggleFullscreen(): Promise<void> {
    try {
      await appWindow.setFullscreen(!isFullscreen);
      isFullscreen = !isFullscreen;
      readerState.isFullscreen = isFullscreen;
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

  const isPdf = $derived(activeReadingBook?.format?.toLowerCase() === 'pdf');
  const isEpub = $derived(activeReadingBook?.format?.toLowerCase() === 'epub');
  const bookProgress = $derived(
    isPdf && activeReadingBook?.currentPage && activeReadingBook?.totalPages
      ? Math.round((activeReadingBook.currentPage / activeReadingBook.totalPages) * 100)
      : Math.round(percentage),
  );

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
    debugState.epub.persistedHighlightsCount = persistedHighlights.length;
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
    debugState.epub.colorPickCount++;
    debugState.epub.lastPickedColor = color;
    console.warn('RW: handleColorSelect data.pageNumber', data.pageNumber, 'cfi', data.cfi?.slice(0, 40) ?? '(null)', 'text', data.text.slice(0, 30));

    // Use the data passed by the toolbar (captured at mount time) rather than
    // the global `lastSelectionData`. This is the fix for the race condition:
    // the browser's selectionchange can fire before our click handler runs
    // and clear the global state, but the data we need to persist the
    // highlight is already in this argument.
    if (data && activeReadingBook) {
      const highlightId = crypto.randomUUID();
      const bounds = data.bounds;
      const pageNumber = data.pageNumber ?? (isEpub ? 0 : 1);
      const cfi = data.cfi ?? null;
      console.warn(
        'RW: push highlight pageNumber=',
        pageNumber,
        'cfi',
        cfi?.slice(0, 60) ?? '(null)',
        'id',
        highlightId.slice(0, 4),
        'text',
        data.text.slice(0, 30),
      );

      // Persist visually immediately
      persistedHighlights = [
        ...persistedHighlights,
        {
          id: highlightId,
          color,
          pageNumber,
          rects: data.rects,
          cfi,
          text: data.text,
          note: null,
        },
      ];

      // Save to backend (async, don't block UI)
      try {
        debugState.epub.saveHighlightCallCount++;
        await saveHighlight({
          id: highlightId,
          bookId: activeReadingBook.id,
          text: data.text,
          color,
          pageNumber,
          rectLeft: bounds.left,
          rectRight: bounds.right,
          rectTop: bounds.top,
          rectBottom: bounds.bottom,
          cfi,
        });
        if (authState.userId) {
          void outboxDao.add(
            'HIGHLIGHT',
            highlightId,
            'UPSERT',
            JSON.stringify({
              userId: authState.userId,
              bookId: activeReadingBook.id,
              cfiRange: cfi ?? '',
              textContent: data.text,
              color,
              page: pageNumber,
              locatorJson: readerState.locatorJson,
              updatedAt: new Date().toISOString(),
            }),
          );
        }
      } catch (err) {
        debugState.epub.saveHighlightLastError = String(err);
        // Mirror the failed highlight id into the debug state for the
        // epub-highlight-bugfix observability layer. Set-like dedup;
        // the same id won't be added twice.
        if (!debugState.epub.failedHighlightIds.includes(highlightId)) {
          debugState.epub.failedHighlightIds.push(highlightId);
        }
        console.warn('Failed to save highlight:', err);
      }
    }

    // Close the toolbar AND clear the browser's text selection on a small
    // delay so the Svelte out:scale transition plays out cleanly. We delay
    // the removeAllRanges call to avoid it firing a fresh selectionchange
    // while we're still rendering.
    setTimeout(() => {
      dismissToolbar();
      window.getSelection()?.removeAllRanges();
    }, 220);
  }

  function openHighlightMenu(id: string, opts?: HighlightActionOpts): void {
    const hl = persistedHighlights.find((h) => h.id === id);
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
    persistedHighlights = persistedHighlights.map((h) => (h.id === id ? { ...h, color } : h));
    if (highlightMenu.highlightId === id) {
      highlightMenu.color = color;
    }
    updateHighlight({ id, color }).catch((err) => {
      console.error('Failed to update highlight color:', err);
    });
    enqueueHighlightUpdate(id, { color });
  }

  function updateHighlightNote(id: string, note: string | null): void {
    persistedHighlights = persistedHighlights.map((h) => (h.id === id ? { ...h, note } : h));
    updateHighlight({ id, note: note ?? undefined }).catch((err) => {
      console.error('Failed to update highlight note:', err);
    });
    enqueueHighlightUpdate(id, { note });
  }

  function deleteHighlightById(id: string): void {
    const highlight = persistedHighlights.find((item) => item.id === id);
    persistedHighlights = persistedHighlights.filter((h) => h.id !== id);
    closeHighlightMenu();
    deleteHighlight(id).catch((err) => console.error('Failed to delete highlight:', err));
    if (authState.userId && highlight) {
      const updatedAt = new Date().toISOString();
      void outboxDao.add(
        'HIGHLIGHT',
        id,
        'DELETE',
        JSON.stringify({
          userId: authState.userId,
          bookId: activeReadingBook?.id ?? id,
          cfiRange: highlight.cfi ?? '',
          textContent: highlight.text ?? '',
          color: highlight.color,
          page: highlight.pageNumber,
          locatorJson: readerState.locatorJson,
          deletedAt: updatedAt,
          updatedAt,
        }),
      );
    }
  }

  function enqueueHighlightUpdate(
    id: string,
    changes: { color?: string; note?: string | null },
  ): void {
    if (!authState.userId) return;
    const highlight = persistedHighlights.find((item) => item.id === id);
    if (!highlight) return;
    void outboxDao.add(
      'HIGHLIGHT',
      id,
      'UPSERT',
      JSON.stringify({
        userId: authState.userId,
        bookId: activeReadingBook?.id ?? id,
        cfiRange: highlight.cfi ?? '',
        textContent: highlight.text ?? '',
        color: changes.color ?? highlight.color,
        note: changes.note ?? highlight.note ?? null,
        page: highlight.pageNumber,
        locatorJson: readerState.locatorJson,
        updatedAt: new Date().toISOString(),
      }),
    );
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

<!-- Full viewport reader layout -->
<section class="flex h-screen flex-col bg-(--color-bg-deep) {isRotated ? 'rotate-1' : ''}" style={isRotated ? 'transform: rotate(0.5deg);' : ''} bind:this={workspaceRoot}>
  <ReaderHeader
    title={activeReadingBook?.title ?? ''}
    {showTocPanel}
    {searchPanelOpen}
    {showTextSettings}
    {showBookmarks}
    {isFullscreen}
    {isRotated}
    {t}
    {onBackToHome}
    onToggleToc={toggleTocPanel}
    onToggleSearch={toggleSearch}
    onToggleTextSettings={toggleTextSettings}
    onToggleBookmarks={toggleBookmarks}
    onToggleFullscreen={toggleFullscreen}
    onToggleRotate={handleRotate}
  />

  <!-- Reading area (centered, fill remaining space) -->
  <div
    class="flex flex-1 min-h-0 items-stretch justify-center"
    class:px-10={!isFullscreen}
    class:py-6={!isFullscreen}
    class:p-0={isFullscreen}
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
          {persistedHighlights}
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
          {persistedHighlights}
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
        ? (persistedHighlights.find((h) => h.id === highlightMenu.highlightId)?.note ?? null)
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

  <!-- Fullscreen side arrows (visible only in fullscreen) -->
  {#if isFullscreen && activeReadingBook}
    <button
      type="button"
      class="fixed left-4 top-1/2 -translate-y-1/2 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur hover:bg-black/60 transition-colors cursor-pointer"
      aria-label={t('reader.prev_page')}
      onclick={goPrevPage}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6" /></svg>
    </button>
    <button
      type="button"
      class="fixed right-4 top-1/2 -translate-y-1/2 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur hover:bg-black/60 transition-colors cursor-pointer"
      aria-label={t('reader.next_page')}
      onclick={goNextPage}
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
