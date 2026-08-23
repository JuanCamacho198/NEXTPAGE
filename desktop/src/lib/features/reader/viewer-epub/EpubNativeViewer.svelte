<script lang="ts">
  /**
   * EpubNativeViewer — spine-authoritative EPUB viewer (cache v3).
   * Cache version 3 (epub_reader.rs CACHE_VERSION=3): `spineHrefs.length === totalChapters`,
   * `spine.json` is authority for CFI/LocatorCodec; stale caches where
   * `spine.json.len() != metadata.totalChapters` are purged via `remove_dir_all`.
   * TOC (`toc`) is nav-only (may be subset of spine, e.g. Historia 20 vs 24 offset-2).
   */
  import { onMount, onDestroy, untrack } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import type { MessageKey } from '$lib/shared/i18n';
  import type {
    ReaderSettings,
    ReaderThemeMode,
    ReaderTextAlign,
    ReaderDirection,
  } from '$lib/shared/types';
  import EpubControls from './EpubControls.svelte';
  import { setReaderError, clearReaderError } from '$lib/stores/readerErrorState.svelte';
  import { debugState } from '$lib/shared/debug/debugState.svelte';
  import { IFRAME_CFI_BRIDGE_SCRIPT } from '$lib/features/reader/viewer-epub/cfiBridgeIframe';
  import { IFRAME_HIGHLIGHT_OVERLAY_SCRIPT } from '$lib/features/reader/viewer-epub/epubHighlightOverlayIframe';
  import {
    HIGHLIGHT_COLORS,
    highlightFillRgba,
    nearestHighlightHex,
  } from '$lib/features/reader/highlight/highlightColors';
  import type { HighlightActionKind, HighlightActionOpts } from '$lib/shared/types/book';
  import { locatorFromCfi, locatorToJson, normalizeHref } from '$lib/shared/sync/LocatorCodec';
  import {
    sanitizeEpubHtml,
    stripFragment,
    extractFragment,
    spineIndexForToc as pureSpineIndexForToc,
    tocIndexForSpine as pureTocIndexForSpine,
  } from '$lib/features/reader/viewer-epub/epubViewerHelpers';
  import { updateHighlight } from '$lib/shared/api/tauriClient';

  // ─── Types ───────────────────────────────────────────────
  interface EpubChapterMeta {
    index: number;
    id: string;
    label: string;
    href: string;
    depth?: number;
  }

  interface EpubMetadataExtract {
    title: string;
    author: string;
    language: string | null;
    publisher: string | null;
    /** TOC (nav) — author order, may be subset of spine (e.g. Historia 20 vs 24). */
    toc: EpubChapterMeta[];
    /** Spine hrefs in OPF order, linear=no filtered. Authority for CFI / LocatorCodec / render. */
    spineHrefs: string[];
    /**
     * Back-compat aliases from Rust serde (old caches): `chapters` → toc, `spine_hrefs` snake.
     * Kept for cache migration (version 2 → 3); stale caches are auto-purged so new code
     * always sees `toc`/`spineHrefs`, but alias prevents crash on leftover 2.x caches.
     * TODO: remove in next major after all users migrate (tracked in verifies).
     */
    chapters?: EpubChapterMeta[];
    spine_hrefs?: string[];
    totalChapters: number;
    total_chapters?: number;
    resourcesPath: string;
    resources_path?: string;
  }

  interface EpubChapterContent {
    index: number;
    html: string;
    mime: string;
    chapterBasePath: string;
    chapterPath: string;
  }

  type Props = {
    filePath: string;
    bookId: string;
    initialLocation?: string;
    initialPercentage?: number;
    /**
     * External navigation target (e.g. from "View in book" on a highlight
     * or from full-text search). When set to an EPUB CFI, the viewer
     * navigates to the owning chapter and scrolls the target into view.
     * The caller re-sets this value to trigger a fresh jump.
     */
    searchTargetLocator?: string | null;
    onLocationChange?: (cfiLocation: string, percentage: number) => void;
    onLocationContext?: (ctx: { locator: string; percentage: number }) => void;
    readerSettings?: ReaderSettings;
    onselection?: (event: {
      text: string;
      bounds: { left: number; top: number; right: number; bottom: number };
      container: { left: number; top: number; width: number; height: number };
      placement: string;
      rects: Array<{ left: number; top: number; width: number; height: number }>;
      pageNumber: number;
      cfi: string | null;
    }) => void;
    /**
     * All persisted highlights for the current book. The EPUB iframe
     * renders the highlights whose `pageNumber === currentSpineIndex`
     * (spine authority, not TOC index — offset-2) via the CSS Custom
     * Highlight API (zero DOM mutation). Typed as a slim shape (subset
     * of `HighlightDto`) so callers don't have to build the full DTO.
     * Cache version 3: `spineHrefs.length === totalChapters`; stale
     * caches (spine.json len != totalChapters) are purged in Rust
     * `epub_reader.rs` (`CACHE_VERSION = 3`, `is_cache_stale`).
     */
    persistedHighlights?: Array<{
      id: string;
      color: string;
      pageNumber: number;
      cfi?: string | null;
      text?: string | null;
    }>;
    /**
     * Called when the user clicks a persisted highlight inside the EPUB
     * iframe. The parent uses this to render the Menu 2 toolbar
     * (color picker + delete + close) at the click point in
     * parent-viewport coordinates.
     */
    onHighlightAction?: (
      action: HighlightActionKind,
      id: string,
      opts?: HighlightActionOpts,
    ) => void;
    onselectionclear?: () => void;
    isFullscreen?: boolean;
    onToggleFullscreen?: () => void;
    onTocReady?: (entries: Array<{ id: string; title: string; depth: number }>) => void;
    externalTocNavigate?: { id: string } | null;
    showToc?: boolean;
    onToggleToc?: () => void;
    onSettingsChange?: (settings: ReaderSettings) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  // ─── Props ───────────────────────────────────────────────
  let {
    filePath,
    bookId,
    initialLocation = '',
    initialPercentage = 0,
    searchTargetLocator = null,
    onLocationChange,
    onLocationContext,
    readerSettings = {
      themeMode: 'paper' as ReaderThemeMode,
      brightness: 100,
      contrast: 100,
      epub: { fontSize: 100, fontFamily: 'serif' },
      selectionColor: '#33bbff',
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: 'left' as ReaderTextAlign,
      direction: 'ltr' as ReaderDirection,
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: 'percentage',
    },
    onselection,
    onselectionclear,
    persistedHighlights = [],
    onHighlightAction,
    isFullscreen = false,
    onTocReady,
    externalTocNavigate = null,
    onToggleFullscreen,
    onToggleToc,
    onSettingsChange,
    t,
  }: Props = $props();

  // ─── State ───────────────────────────────────────────────
  let metadata = $state<EpubMetadataExtract | null>(null);
  let currentChapterIndex = $state(0);
  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let iframeEl = $state<HTMLIFrameElement | null>(null);
  let totalChapters = $state(0);
  let zoomLevel = $state(100);
  let zoomContainerEl = $state<HTMLDivElement | null>(null);
  let iframeContentHeight = $state(0);
  /** CFI que se debe mostrar una vez que el capítulo objetivo cargue. */
  let pendingCfiScroll = $state<string | null>(null);
  /** Fragment (#id) que se debe scrollear una vez que el capítulo objetivo cargue. */
  let pendingFragment = $state<string | null>(null);

  // ─── Reader settings cache (synced from prop for reactivity) ─
  let fontSize = $state(100);
  let fontFamily = $state('serif');
  let themeMode = $state<ReaderThemeMode>('paper');
  let lineHeight = $state(1.8);
  let letterSpacing = $state(0);
  let paragraphSpacing = $state(1);
  let textAlign = $state<ReaderTextAlign>('left');
  let direction = $state<ReaderDirection>('ltr');
  let hyphenation = $state(false);
  let margins = $state<ReaderSettings['margins']>({ top: 1.5, bottom: 1.5, left: 2, right: 2 });

  $effect(() => {
    fontSize = readerSettings.epub.fontSize ?? 100;
    fontFamily = readerSettings.epub.fontFamily?.trim() || 'serif';
    themeMode = readerSettings.themeMode;
    lineHeight = readerSettings.lineHeight;
    letterSpacing = readerSettings.letterSpacing;
    paragraphSpacing = readerSettings.paragraphSpacing;
    textAlign = readerSettings.textAlign;
    direction = readerSettings.direction;
    hyphenation = readerSettings.hyphenation;
    margins = readerSettings.margins;
  });

  // ─── Spine / TOC helpers (authoritative: spineHrefs, nav: toc) ──────────
  function getToc(): EpubChapterMeta[] {
    if (!metadata) return [];
    const t = (metadata as EpubMetadataExtract).toc;
    if (Array.isArray(t) && t.length >= 0) return t;
    const ch = (metadata as EpubMetadataExtract).chapters;
    if (Array.isArray(ch)) return ch;
    return [];
  }

  function getSpineHrefs(): string[] {
    if (!metadata) return [];
    const sh = (metadata as EpubMetadataExtract).spineHrefs;
    if (Array.isArray(sh) && sh.length > 0) return sh.map((h) => normalizeHref(h));
    const sh2 = (metadata as EpubMetadataExtract).spine_hrefs;
    if (Array.isArray(sh2) && sh2.length > 0) return sh2.map((h) => normalizeHref(h));
    // Fallback: derive from toc hrefs — spine authority preferred. TOC-derived hrefs
    // may be misaligned (Odisea offset) if cache is stale; log warning so stale
    // caches are purged via CACHE_VERSION bump (epub_reader.rs v4).
    const toc = getToc();
    if (toc.length > 0) {
      console.warn('epub-spine: falling back to TOC-derived hrefs (spine empty, tocLen', toc.length, ') — may be misaligned if offset-2');
      return toc.map((c) => normalizeHref(stripFragment(c.href)));
    }
    return [];
  }

  /** Derive spine index for the current TOC position — used for CFI / highlights. */
  let currentSpineIndex = $derived(spineIndexForToc(currentChapterIndex));

  // ─── TOC ↔ Spine mapping helpers ─────────────────────────
  /** Resolve spine index (0..spineLen-1) for a TOC position (0..tocLen-1). */
  function spineIndexForToc(tocIndex: number): number {
    if (!metadata) return tocIndex;
    const toc = getToc();
    const entry = toc[tocIndex];
    if (!entry || typeof entry.index !== 'number') {
      console.warn(
        'epub-toc: spineIndexForToc missing entry for tocIndex',
        tocIndex,
        'fallback to',
        tocIndex,
        'tocLen',
        toc.length,
      );
      return tocIndex;
    }
    const spineLen = getSpineHrefs().length;
    const resolved = pureSpineIndexForToc(toc, tocIndex);
    if (spineLen > 0 && (resolved < 0 || resolved >= spineLen)) {
      console.warn('epub-toc: spineIndexForToc resolved index out-of-bounds', resolved, 'spineLen', spineLen, 'tocIndex', tocIndex);
    }
    return resolved;
  }

  /** Resolve TOC position for a 0-based spine index. Returns null when not in TOC. */
  function tocIndexForSpine(spineIndex: number, spineHref?: string): number | null {
    if (!metadata) return null;
    const toc = getToc();
    const res = pureTocIndexForSpine(toc, spineIndex, spineHref);
    if (res !== null) return res;
    console.warn(
      'epub-toc: tocIndexForSpine no TOC entry for spineIndex',
      spineIndex,
      'spineHref',
      spineHref ?? '(none)',
      'fallback will use spineIndex',
    );
    return null;
  }

  /** Scroll iframe to fragment anchor (preserved #frag in toc.href). 3×rAF ensures layout. */
  function scrollToFragment(fragment: string | null): void {
    if (!fragment || !iframeEl?.contentDocument) return;
    const doc = iframeEl.contentDocument;
    const target = doc.getElementById(fragment);
    if (!target) {
      console.warn('epub-frag: fragment not found', fragment);
      return;
    }
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          target.scrollIntoView({ block: 'start', behavior: 'smooth' });
          // Also sync outer container scroll if needed
          if (zoomContainerEl) {
            const rect = target.getBoundingClientRect();
            const containerRect = zoomContainerEl.getBoundingClientRect();
            if (rect.top < containerRect.top || rect.bottom > containerRect.bottom) {
              target.scrollIntoView({ block: 'center', behavior: 'auto' });
            }
          }
        });
      });
    });
    pendingFragment = null;
  }

  // ─── Lifecycle ───────────────────────────────────────────
  function handleIframeMessage(event: MessageEvent): void {
    if (!event.data || typeof event.data !== 'object') return;

    // Debug: capture every postMessage so the panel can show what arrived.
    if (debugState.enabled) {
      debugState.epub.postMessageCount++;
      debugState.epub.lastRawMessage = {
        type: String(event.data.type ?? ''),
        pageNumber: typeof event.data.pageNumber === 'number' ? event.data.pageNumber : null,
        hasText: typeof event.data.text === 'string' && event.data.text.length > 0,
        textPreview: typeof event.data.text === 'string' ? event.data.text.slice(0, 60) : '',
        hasBounds: !!event.data.bounds,
        hasContainer: !!event.data.container,
        hasCfi: typeof event.data.cfi === 'string' && event.data.cfi.length > 0,
        cfiPreview: typeof event.data.cfi === 'string' ? event.data.cfi.slice(0, 60) : '',
      };
      debugState.epub.guardResult = 'none';
    }

    if (event.data.type === 'epub-srcdoc-error') {
      console.warn('SRC DOC ERROR', event.data.msg, 'line', event.data.line, 'col', event.data.col, 'url', event.data.url);
      return;
    }

    // SEL-4: drop any in-flight postMessage from a chapter that is no
    // longer current. The chapter-change $effect can run between the
    // mouseup and the postMessage delivery, so we always sanity-check
    // the pageNumber. (The epub-resize message has no pageNumber and
    // is treated as a global signal -- but the resize handler reads
    // the current iframe state, so it is also safe to drop stale
    // resize events. We keep it for now since resizes are cheap.)
    if (event.data.type === 'epub-resize') {
      syncIframeHeight();
      return;
    }

    if (event.data.type === 'epub-highlight-click') {
      handleEpubHighlightClick(event.data);
      return;
    }

    if (event.data.type === 'epub-hl-failed') {
      handleEpubHighlightFailed(event.data);
      return;
    }

    if (event.data.type === 'epub-hl-placed') {
      handleEpubHighlightPlaced(event.data);
      return;
    }

    if (event.data.type !== 'epub-selection') {
      if (debugState.enabled) debugState.epub.guardResult = 'drop-unknown-type';
      return;
    }

    {
      const cfiPreview = typeof event.data.cfi === 'string' ? event.data.cfi.slice(0, 40) : '(null)';
      console.warn('epub-sel: received page', event.data.pageNumber, 'currentSpine', currentSpineIndex, 'toc', currentChapterIndex, 'cfi', cfiPreview);
    }

    if (
      typeof event.data.pageNumber === 'number' &&
      event.data.pageNumber !== currentSpineIndex
    ) {
      // Stale message from a chapter that is no longer current (spine authority).
      if (debugState.enabled) debugState.epub.guardResult = 'drop-chapter-mismatch';
      return;
    }

    if (!event.data.text) {
      if (debugState.enabled) {
        debugState.epub.guardResult = 'drop-empty-text';
        debugState.epub.emptyTextMessageCount++;
      }
      debugState.epub.onselectionclearCalled++;
      onselectionclear?.();
      return;
    }

    if (!onselection) {
      if (debugState.enabled) debugState.epub.guardResult = 'drop-no-handler';
      return;
    }

    if (debugState.enabled) debugState.epub.guardResult = 'pass';
    debugState.epub.onselectionCalled++;
    debugState.epub.rectCount = Array.isArray(event.data.rects) ? event.data.rects.length : 0;

    const resolvedPageNumber =
      typeof event.data.pageNumber === 'number' ? event.data.pageNumber : currentSpineIndex;
    const resolvedCfi = typeof event.data.cfi === 'string' ? event.data.cfi : null;
    onselection({
      text: event.data.text,
      bounds: event.data.bounds,
      container: event.data.container,
      placement: 'epub-chapter',
      rects: event.data.rects ?? [],
      pageNumber: resolvedPageNumber,
      cfi: resolvedCfi,
    });
  }

  // ─── Menu 2 (highlight click) ──────────────────────────────
  function handleEpubHighlightClick(msg: {
    id: string;
    x: number;
    y: number;
    color: string;
    text?: string;
    pageNumber: number;
  }): void {
    if (msg.pageNumber !== currentSpineIndex) return;
    if (!iframeEl || !onHighlightAction) return;
    // Translate iframe-local click point to parent-viewport coords
    // using the iframe element's bounding rect.
    const frameRect = iframeEl.getBoundingClientRect();
    onHighlightAction('open', msg.id, {
      color: msg.color,
      text: msg.text,
      x: msg.x + frameRect.left,
      y: msg.y + frameRect.top,
    });
  }

  // ─── epub-hl-failed: apply failure observability ──────────────
  // The iframe overlay posts this when cfiToRange returns null (cfi-
  // unresolved) or hexToRgba returns null (invalid-color). We mirror
  // the id to debugState with Set-like dedup and log a warning. Bounded
  // growth: n < 100 in practice. See design Decision 2.
  function handleEpubHighlightFailed(msg: {
    id: string;
    reason: string;
    pageNumber: number;
    cfi?: string;
    color?: string;
  }): void {
    if (msg.id && !debugState.epub.failedHighlightIds.includes(msg.id)) {
      debugState.epub.failedHighlightIds.push(msg.id);
    }
    console.warn('epub-hl: highlight failed to apply', {
      id: msg.id,
      reason: msg.reason,
      pageNumber: msg.pageNumber,
    });
  }

  // ─── epub-hl-placed: persist text-anchor derived page ─────────
  // When the overlay places a highlight by its stored text (Android
  // highlights whose locator is missing or not an epubcfi), it reports
  // the spine index where the text was actually found. Persist that
  // derived pageNumber (fire-and-forget) and patch the local
  // persistedHighlights entry in place so subsequent renders match the
  // spine gate directly instead of redoing the fallback.
  function handleEpubHighlightPlaced(msg: { id: string; pageNumber: number }): void {
    if (!msg?.id || typeof msg.pageNumber !== 'number') return;
    const idx = persistedHighlights.findIndex((h) => h.id === msg.id);
    if (idx >= 0 && persistedHighlights[idx].pageNumber !== msg.pageNumber) {
      persistedHighlights[idx] = { ...persistedHighlights[idx], pageNumber: msg.pageNumber };
    }
    void updateHighlight({ id: msg.id, pageNumber: msg.pageNumber }).catch(() => {});
  }

  function syncIframeHeight(): void {
    if (!iframeEl?.contentDocument) return;

    const doc = iframeEl.contentDocument;
    // `documentElement` and `body` can be null while the iframe is still
    // mid-load (e.g. when an `epub-resize` postMessage arrives before the
    // chapter DOM is ready). Bail out instead of throwing.
    const docEl = doc.documentElement;
    const body = doc.body;
    if (!docEl || !body) return;

    const height = Math.max(docEl.scrollHeight, body.scrollHeight);
    iframeContentHeight = height > 0 ? height : 0;
  }

  onMount(() => {
    console.warn('epub-hl: VIEWER BUILD v4 epoch-guard active');
    console.warn(
      'epub-hl: onMount bookId=',
      bookId.slice(0, 8),
      'initialLocation=',
      (initialLocation ?? '').slice(0, 80),
      'chapter',
      currentChapterIndex,
    );
    initReader();
    window.addEventListener('message', handleIframeMessage);
    return () => {
      window.removeEventListener('message', handleIframeMessage);
    };
  });

  // ─── Debug: track iframe rect so the panel can show where the iframe sits ──
  $effect(() => {
    const el = iframeEl;
    if (!el || !debugState.enabled) return;
    const update = (): void => {
      const r = el.getBoundingClientRect();
      debugState.epub.iframeRect = { left: r.left, top: r.top, width: r.width, height: r.height };
    };
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      ro.disconnect();
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  });

  $effect(() => {
    debugState.epub.currentChapterIndex = currentChapterIndex;
    // Expose spine index for debug panel as well
    (debugState.epub as unknown as Record<string, unknown>).currentSpineIndex = currentSpineIndex;
  });

  let lastRenderedChapter = $state(-1);
  // Guard against duplicate highlight renders (same chapter + same highlight ids)
  let lastHighlightRenderKey = $state('');
  // Track last processed initialLocation to avoid loops for continue bug
  let lastContinueLocation = $state<string | null>(null);

  // ─── Render epoch guard (ping-pong fix) ────────────────────
  // Plain vars (not $state) — reading/writing them does NOT retrigger effects.
  let renderEpoch = 0;
  let currentRenderIndex: number | null = null;

  // ─── Lifecycle logging for remount detection (Bug 1) ──
  onDestroy(() => {
    console.warn(
      'epub-hl: onDestroy bookId=',
      bookId.slice(0, 8),
      'chapter',
      untrack(() => currentChapterIndex),
      'lastRendered',
      untrack(() => lastRenderedChapter),
    );
  });

  // ─── Render chapter or refresh styles when settings change ──
  $effect(() => {
    if (!metadata || isLoading || !iframeEl) return;

    void fontSize;
    void fontFamily;
    void themeMode;
    void lineHeight;
    void letterSpacing;
    void paragraphSpacing;
    void textAlign;
    void direction;
    void hyphenation;
    void margins.top;
    void margins.right;
    void margins.bottom;
    void margins.left;
    void zoomLevel;

    if (lastRenderedChapter !== currentChapterIndex) {
      // Epoch guard: if we're already rendering this index, don't re-trigger.
      // Prevents ping-pong when stale markReady flips lastRendered.
      if (currentRenderIndex === currentChapterIndex && renderEpoch > 0) return;
      renderChapter(currentChapterIndex);
      return;
    }

    refreshReaderStyles();
  });

  // ─── Re-render persisted highlights inside the iframe ──────
  // Runs after every chapter change (when the iframe document is
  // fresh) and whenever the parent's `persistedHighlights` reference
  // changes (e.g. after a color update or delete). The overlay is
  // idempotent -- it clears existing wraps before re-applying.
  // BUG1 FIX: `lastRenderedChapter` is read via `untrack()` so the
  // effect does NOT re-trigger when markReady flips it. The polling
  // inside attemptRender waits for lastRendered without creating a
  // reactive loop (the previous version read lastRendered at top
  // level, causing -1↔5 bounce).
  type EpubHighlightShape = {
    id: string;
    color: string;
    pageNumber: number;
    cfi?: string | null;
    text?: string | null;
  };
  $effect(() => {
    if (!metadata || isLoading || !iframeEl) return;
    // Track persistedHighlights reactively (triggers on array reference changes)
    void persistedHighlights;
    // Capture both TOC index and spine index reactively; highlights are spine-authoritative
    const currentIdx = currentChapterIndex;
    void currentSpineIndex;
    const spineHref = normalizeHref(getSpineHrefs()[currentSpineIndex] ?? '');
    const tocHrefRaw = getToc()[currentIdx]?.href ?? '';
    const chapterHref = spineHref || normalizeHref(stripFragment(tocHrefRaw));
    const metaHref = normalizeHref(stripFragment(tocHrefRaw));
    const highlightsSnapshot = persistedHighlights;
    const lastRenderedSnapshot = untrack(() => lastRenderedChapter);

    console.warn(
      'epub-hl: effect triggered with',
      highlightsSnapshot.length,
      'highlights, toc',
      currentIdx,
      'spine',
      currentSpineIndex,
      'lastRendered',
      lastRenderedSnapshot,
      'highlightsMap',
      highlightsSnapshot.map((h) => `${h.pageNumber}:${h.id.slice(0, 4)}`).join(','),
      'chapterHref',
      chapterHref,
      'metaHref',
      metaHref,
    );

    // Deduplicate: spine-authoritative; include both toc and spine in key
    const renderKey = `${currentIdx}|${currentSpineIndex}|${chapterHref}|${highlightsSnapshot.map((h) => h.id + ':' + h.pageNumber).join(',')}`;
    if (renderKey === untrack(() => lastHighlightRenderKey)) {
      console.warn('epub-hl: skip duplicate render', renderKey.slice(0, 120));
      return;
    }

    const MAX_RETRIES = 25; // ~500ms at 20ms interval
    const RETRY_INTERVAL = 20;
    let retries = 0;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let cancelled = false;

    function attemptRender(): void {
      if (cancelled) return;
      // Fresh window reference each attempt (avoid stale closure)
      const win = iframeEl?.contentWindow as
        | (Window & {
            __epubHighlightOverlay?: {
              render: (h: EpubHighlightShape[], chapterHref: string, idx: number) => void;
              isReady?: () => boolean;
            };
            __cfiBridge?: { cfiToRange: (...args: unknown[]) => unknown };
          })
        | null;

      // Wait for chapter DOM to be confirmed rendered (untracked to avoid loop)
      const currentLastRendered = untrack(() => lastRenderedChapter);
      if (currentLastRendered !== currentIdx) {
        if (retries++ < MAX_RETRIES) {
          timer = setTimeout(attemptRender, RETRY_INTERVAL);
          if (retries === 1) {
            console.warn(
              'epub-hl: render deferred (lastRenderedChapter',
              currentLastRendered,
              '!== current',
              currentIdx,
              ') - retrying',
            );
          }
        } else {
          console.warn(
            'epub-hl: render aborted (lastRenderedChapter',
            currentLastRendered,
            '!== current',
            currentIdx,
            ') after',
            MAX_RETRIES,
            'retries',
          );
        }
        return;
      }

      // Ensure overlay is mounted; if not, poll
      if (!win || !win.__epubHighlightOverlay) {
        if (retries++ < MAX_RETRIES) {
          timer = setTimeout(attemptRender, RETRY_INTERVAL);
          if (retries === 1) {
            console.warn('epub-hl: render deferred (overlay not mounted on iframe window) - retrying');
          }
        } else {
          console.warn(
            'epub-hl: render aborted (overlay not mounted on iframe window) after',
            MAX_RETRIES,
            'retries',
          );
        }
        return;
      }

      // Overlay is mounted - always call render. The overlay's pendingRender
      // queue will defer if the CFI bridge is not ready yet.
      console.warn(
        'epub-hl: render called with',
        highlightsSnapshot.length,
        'highlights, toc',
        currentIdx,
        'spine',
        currentSpineIndex,
        'chapterHref',
        chapterHref,
      );
      try {
        win.__epubHighlightOverlay.render(highlightsSnapshot, chapterHref, currentSpineIndex);
        // Mark as successfully rendered to deduplicate
        lastHighlightRenderKey = renderKey;
      } catch (err) {
        console.warn('epub-hl: render failed', err);
      }
    }

    attemptRender();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  });

  // ─── External TOC navigation ─────────────────────────────
  $effect(() => {
    if (externalTocNavigate && externalTocNavigate.id && metadata) {
      const toc = getToc();
      const chapterIdx = toc.findIndex((c) => c.id === externalTocNavigate!.id);
      if (chapterIdx >= 0) {
        const href = toc[chapterIdx]?.href ?? '';
        const frag = extractFragment(href);
        if (frag) pendingFragment = frag;
        goToChapter(chapterIdx);
      }
    }
  });

  // ─── Search / "View in book" target navigation ───────────
  // When `searchTargetLocator` carries an EPUB CFI, parse the owning
  // chapter from the CFI's spine index (`epubcfi(/6/N!...)`), navigate
  // there, and once the chapter has rendered, scroll the CFI range into
  // view (via the iframe bridge). Re-setting the prop re-triggers the jump.
  $effect(() => {
    const target = searchTargetLocator;
    if (!target || !target.startsWith('epubcfi(') || !metadata) return;

    const spineMatch = /epubcfi\(\/6\/(\d+)!/.exec(target);
    if (!spineMatch) return;

    const spineOneBased = Number.parseInt(spineMatch[1], 10); // 1-based
    const spineIdx = spineOneBased - 1;
    if (spineIdx < 0) return;
    // currentChapterIndex is TOC position (0..chapters.length-1); map spine -> TOC
    const mapped = tocIndexForSpine(spineIdx);
    const chapterIdx = mapped !== null ? mapped : spineIdx;
    if (mapped === null) {
      console.warn(
        'epub-toc: searchTargetLocator spine',
        spineIdx,
        'not in TOC, fallback to',
        chapterIdx,
        'totalChapters',
        totalChapters,
        'tocLen',
        getToc().length,
      );
    }
    if (mapped !== null) {
      if (chapterIdx < 0 || chapterIdx >= getToc().length) return;
    } else {
      if (chapterIdx < 0 || chapterIdx >= totalChapters) return;
    }

    if (chapterIdx !== currentChapterIndex) {
      pendingCfiScroll = target;
      goToChapter(chapterIdx);
    } else {
      scrollToCfi(target);
    }
  });

  // ─── ContinueReading: handle late initialLocation (Bug 2) ───
  // Observes `initialLocation` AFTER metadata loaded. If it arrives late
  // (mount with '' then CFI of chapter 5), navigates via goToChapter +
  // pendingCfiScroll. Uses CFI comparison to avoid loops.
  $effect(() => {
    const loc = initialLocation;
    if (!loc || !loc.startsWith('epubcfi(') || !metadata || isLoading) return;
    if (loc === untrack(() => lastContinueLocation)) return;
    const spineMatch = /epubcfi\(\/6\/(\d+)!/.exec(loc);
    if (!spineMatch) return;
    const spineOneBased = Number.parseInt(spineMatch[1], 10);
    const spineIdx = spineOneBased - 1;
    if (spineIdx < 0) return;
    const mapped = tocIndexForSpine(spineIdx);
    const chapterIdx = mapped !== null ? mapped : spineIdx;
    if (mapped === null) {
      console.warn(
        'epub-toc: continue spine',
        spineIdx,
        'not in TOC, fallback to',
        chapterIdx,
        'tocLen',
        getToc().length,
      );
    }
    if (mapped !== null) {
      if (chapterIdx < 0 || chapterIdx >= getToc().length) return;
    } else {
      if (chapterIdx < 0 || chapterIdx >= totalChapters) return;
    }
    const currentIdx = untrack(() => currentChapterIndex);
    const pending = untrack(() => pendingCfiScroll);
    console.warn(
      'continue: initialLocation changed to',
      loc.slice(0, 80),
      'spineIdx',
      spineIdx,
      'chapterIdx(toc)',
      chapterIdx,
      'current',
      currentIdx,
    );
    lastContinueLocation = loc;
    if (chapterIdx !== currentIdx) {
      console.warn('continue: navigating to chapter', chapterIdx, 'from', currentIdx);
      pendingCfiScroll = loc;
      goToChapter(chapterIdx);
    } else {
      if (pending !== loc) {
        console.warn('continue: already at chapter, scrolling to', loc.slice(0, 60));
        pendingCfiScroll = loc;
        if (untrack(() => lastRenderedChapter) === chapterIdx) {
          scrollToCfi(loc);
        }
      } else {
        console.warn('continue: already at chapter with same pendingCfi, ignoring');
      }
    }
  });

  // ─── Init ────────────────────────────────────────────────
  async function initReader(): Promise<void> {
    isLoading = true;
    error = null;

    try {
      const meta = await invoke<EpubMetadataExtract>('parse_epub', {
        filePath,
        bookId,
      });

      metadata = meta;
      totalChapters = meta.totalChapters;

      // Resume by the exact saved CFI when available (the source of
      // truth), falling back to a percentage-based chapter estimate only
      // when no CFI exists. Estimating from `initialPercentage` with
      // Math.floor is imprecise — it routinely lands on the chapter
      // BEFORE the reader actually was (and at 0% on the first chapter),
      // which is why "Continue Reading" sometimes jumped back or to the
      // start.
      // Capture loc at init to avoid racing with late continue update.
      // If empty and no percentage, leave currentChapterIndex=0 default and
      // let the `continue` effect drive navigation once CFI arrives (epoch guard prevents ping-pong).
      const locAtInit = initialLocation;
      const initialCfi = locAtInit;
      if (!locAtInit || locAtInit === '') {
        console.warn(
          'epub-hl: initReader with empty initialLocation, deferring to continue effect if any, chapter',
          currentChapterIndex,
        );
      }
      const tocForInit = (meta as EpubMetadataExtract).toc ?? (meta as EpubMetadataExtract).chapters ?? [];
      if (initialCfi && initialCfi.startsWith('epubcfi(') && tocForInit.length > 0) {
        const spineMatch = /epubcfi\(\/6\/(\d+)!/.exec(initialCfi);
        if (spineMatch) {
          const spineOneBased = Number.parseInt(spineMatch[1], 10); // 1-based
          const spineIdx = spineOneBased - 1;
          if (spineIdx >= 0) {
            // Map spine -> TOC; currentChapterIndex is always TOC position 0..toc.length-1
            const mapped = (() => {
              const byIndex = tocForInit.findIndex((c) => c.index === spineIdx);
              if (byIndex !== -1) return byIndex;
              console.warn(
                'epub-toc: initReader spine',
                spineIdx,
                'not in TOC, fallback to',
                spineIdx,
                'tocLen',
                tocForInit.length,
              );
              return null;
            })();
            const tocIdx = mapped !== null ? mapped : spineIdx;
            if (tocIdx >= 0 && tocIdx < tocForInit.length) {
              currentChapterIndex = tocIdx;
              pendingCfiScroll = initialCfi;
            } else if (mapped === null && tocIdx >= 0 && tocIdx < totalChapters) {
              // Fallback case: spine doc not in TOC, keep spine index to avoid blank screen
              console.warn('epub-toc: initReader fallback spineIdx', spineIdx, 'as tocIdx', tocIdx);
              currentChapterIndex = tocIdx;
              pendingCfiScroll = initialCfi;
            }
          }
        }
      } else if (initialPercentage > 0 && initialPercentage < 100) {
        const chapterGuess = Math.floor((initialPercentage / 100) * totalChapters);
        currentChapterIndex = Math.min(chapterGuess, totalChapters - 1);
      }

      if (onTocReady) {
        const entries = (tocForInit).map((ch) => ({
          id: ch.id,
          title: ch.label,
          depth: ch.depth ?? 0,
        }));
        onTocReady(entries);
      }

      // Index EPUB text for full-text search.
      // The Rust command `index_epub_text` (camelCase: `indexEpubText`) reads the
      // cached chapter files and indexes into FTS5.
      // NOTE: hidden behind debug flag since the command is not yet implemented in Rust.
      if (debugState.enabled) {
        invoke('indexEpubText', { bookId }).catch((err: unknown) => {
          console.warn('Failed to index EPUB text for search', err);
        });
      }
      clearReaderError();
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      setReaderError(error);
    } finally {
      isLoading = false;
    }
  }

  function resolveResourcePath(chapterPath: string, href: string): string {
    const chapterDir = chapterPath.includes('/')
      ? chapterPath.slice(0, chapterPath.lastIndexOf('/') + 1)
      : '';
    const parts = `${chapterDir}${href}`.split('/');
    const resolved: string[] = [];

    for (const part of parts) {
      if (part === '..') {
        resolved.pop();
      } else if (part !== '.' && part !== '') {
        resolved.push(part);
      }
    }

    return resolved.join('/');
  }

  function toAssetUrl(resourcesPath: string, resourcePath: string): string {
    const normalized = resourcePath.replace(/\\/g, '/');
    const base = resourcesPath.replace(/\\/g, '/').replace(/\/$/, '');
    return `${convertFileSrc(`${base}/${normalized}`)}`;
  }

  /**
   * Scan the chapter HTML for `@font-face` rules whose `src:` points to a
   * local font file (the EPUB declares them but the editor forgot to embed
   * the .ttf/.otf/.woff/.woff2 inside the archive). Returns the list of
   * resolved `asset://` URLs so the caller can probe them with a HEAD
   * request and drop the ones that 404/403.
   *
   * EPUBs in the wild routinely reference Neutraface, Felt Tip Roman and
   * other commercial fonts in their CSS without bundling the file. Without
   * this filter, the iframe fires a 403 per missing font on every chapter.
   */
  function collectFontFaceAssetUrls(
    html: string,
    chapterPath: string,
    resourcesPath: string,
  ): string[] {
    const urls = new Set<string>();
    const fontExt = /\.(ttf|otf|woff2?|eot)(\?|$|#)/i;
    const urlPattern = /url\(\s*(['"]?)([^'")]+)\1\s*\)/gi;

    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    const normalizedChapter = chapterPath.replace(/\\/g, '/');

    const collectFromCss = (cssText: string): void => {
      // Only consider rules that look like @font-face (a heuristic: contain
      // `font-face`). This avoids touching unrelated background-image urls.
      if (!/font-face/i.test(cssText)) return;
      let match: RegExpExecArray | null;
      urlPattern.lastIndex = 0;
      while ((match = urlPattern.exec(cssText)) !== null) {
        const rawUrl = match[2];
        if (
          !rawUrl ||
          rawUrl.startsWith('http') ||
          rawUrl.startsWith('data:') ||
          rawUrl.startsWith('asset:') ||
          rawUrl.startsWith('#') ||
          rawUrl.startsWith('local(')
        ) {
          continue;
        }
        if (!fontExt.test(rawUrl)) continue;
        const resourcePath = resolveResourcePath(normalizedChapter, rawUrl);
        urls.add(toAssetUrl(resourcesPath, resourcePath));
      }
    };

    for (const styleEl of doc.querySelectorAll('style')) {
      collectFromCss(styleEl.textContent ?? '');
    }
    // We don't probe linked stylesheets (they're loaded async by the iframe
    // and their body isn't in the HTML string we get from Rust). The 403s
    // for those are handled by the same `missingFonts` filter applied to
    // their `@font-face` rules once they're parsed by the browser.
    return Array.from(urls);
  }

  /**
   * HEAD-probe a batch of asset URLs in parallel and return the set of
   * URLs that didn't respond OK. Any error (network, 4xx, 5xx) is treated
   * as "missing" — the iframe would 403 anyway, so it's safe to drop the
   * corresponding `@font-face` rule.
   */
  async function probeMissingFontUrls(urls: string[]): Promise<Set<string>> {
    const missing = new Set<string>();
    if (urls.length === 0) return missing;
    const results = await Promise.allSettled(
      urls.map(async (url) => {
        try {
          const res = await fetch(url, { method: 'HEAD' });
          if (!res.ok) throw new Error(`status ${res.status}`);
        } catch {
          throw new Error('missing');
        }
      }),
    );
    urls.forEach((url, i) => {
      if (results[i].status === 'rejected') {
        missing.add(url);
      }
    });
    return missing;
  }

  /**
   * Drop `@font-face` rules from a CSS string whose rewritten `src:` URL
   * appears in `missingFonts`. We keep the rest of the CSS intact so the
   * cascade falls back to the system font instead of a broken custom one.
   */
  function stripMissingFontFaces(cssText: string, missingFonts: Set<string>): string {
    if (missingFonts.size === 0) return cssText;
    return cssText.replace(/@font-face\s*\{[^{}]*\}/gi, (rule) => {
      const urlMatch = rule.match(/url\(\s*(['"]?)([^'")]+)\1\s*\)/i);
      if (!urlMatch) return rule;
      if (missingFonts.has(urlMatch[2])) return '';
      return rule;
    });
  }

  function buildReaderOverrideCss(): string {
    const hyphensValue = hyphenation ? 'auto' : 'none';
    const effectiveFontSize = (fontSize * zoomLevel) / 100;
    const themeCss = getThemeStyles();

    return `
      ${themeCss}
      html {
        height: auto !important;
        max-height: none !important;
        overflow-x: hidden !important;
        overflow-y: visible !important;
      }
      body {
        height: auto !important;
        min-height: 100% !important;
        max-height: none !important;
        overflow: visible !important;
        font-size: ${effectiveFontSize}% !important;
        line-height: ${lineHeight} !important;
        letter-spacing: ${letterSpacing}px !important;
        text-align: ${textAlign} !important;
        direction: ${direction} !important;
        hyphens: ${hyphensValue} !important;
        padding: ${margins.top}rem ${margins.right}rem ${margins.bottom}rem ${margins.left}rem !important;
        max-width: 38rem !important;
        margin: 0 auto !important;
        box-sizing: border-box !important;
      }
      body p {
        margin-bottom: ${paragraphSpacing}em;
      }
      body img {
        max-width: 100%;
        height: auto;
      }
    `;
  }

  function buildChapterSrcdoc(
    chapterData: EpubChapterContent,
    resourcesPath: string,
    spineHrefs: string[],
    currentChapterHref: string,
    chapterIndex: number,
    missingFonts: Set<string> = new Set(),
  ): string {
    console.warn('build srcdoc CHAPTER_INDEX', chapterIndex, 'href', currentChapterHref);
    // 3.4 sanitize before rewrite and srcdoc cache — guarantee zero chrome-extension://
    const sanitizedHtml = sanitizeEpubHtml(chapterData.html);
    const parser = new DOMParser();
    const doc = parser.parseFromString(sanitizedHtml, 'text/html');
    // Defense-in-depth: also strip any lingering DOM nodes that slipped through
    for (const el of doc.querySelectorAll('[id="floatBarImgId"]')) el.remove();
    for (const el of doc.querySelectorAll('[src^="chrome-extension://"]')) el.remove();
    const chapterPath = chapterData.chapterPath.replace(/\\/g, '/');

    const baseUrl = chapterData.chapterBasePath
      ? toAssetUrl(resourcesPath, `${chapterData.chapterBasePath}/`)
      : toAssetUrl(resourcesPath, '');

    let baseEl = doc.querySelector('base');
    if (!baseEl) {
      baseEl = doc.createElement('base');
      doc.head.prepend(baseEl);
    }
    baseEl.setAttribute('href', baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`);

    for (const link of doc.querySelectorAll('link[rel="stylesheet"]')) {
      const href = link.getAttribute('href');
      if (!href || href.startsWith('http') || href.startsWith('data:')) continue;

      const resourcePath = resolveResourcePath(chapterPath, href);
      link.setAttribute('href', toAssetUrl(resourcesPath, resourcePath));
    }

    // Process images, SVG images, and media elements — convert relative/absolute paths to asset URLs.
    // SVG `<image>` elements may reference their source via `xlink:href` (namespaced) or `href`;
    // both must be rewritten or the cover art 403s inside the iframe.
    for (const el of doc.querySelectorAll('img, image, video, audio, source, object')) {
      const isSvgImage = el.tagName.toLowerCase() === 'image';
      const attrs = isSvgImage
        ? [el.getAttribute('href'), el.getAttribute('xlink:href')]
        : [el.getAttribute('src')];
      const src = attrs.find(
        (s) =>
          s &&
          !s.startsWith('http') &&
          !s.startsWith('data:') &&
          !s.startsWith('asset:') &&
          !s.startsWith('chrome-extension:') &&
          !s.startsWith('blob:') &&
          !s.startsWith('chrome:'),
      );
      if (!src) continue;

      const resourcePath = resolveResourcePath(chapterPath, src);
      const assetUrl = toAssetUrl(resourcesPath, resourcePath);
      if (isSvgImage) {
        // Prefer writing `href`; fall back to `xlink:href` when only that exists.
        if (el.getAttribute('href') === src) {
          el.setAttribute('href', assetUrl);
        } else {
          el.setAttribute('xlink:href', assetUrl);
        }
      } else {
        el.setAttribute('src', assetUrl);
      }
    }

    for (const styleEl of doc.querySelectorAll('style')) {
      let cssText = styleEl.textContent ?? '';
      if (!cssText.includes('url(')) continue;

      cssText = cssText.replace(
        /url\(\s*(['"]?)([^'")]+)\1\s*\)/gi,
        (_match, quote: string, rawUrl: string) => {
          if (
            rawUrl.startsWith('http') ||
            rawUrl.startsWith('data:') ||
            rawUrl.startsWith('asset:') ||
            rawUrl.startsWith('#')
          ) {
            return `url(${quote}${rawUrl}${quote})`;
          }

          const resourcePath = resolveResourcePath(chapterPath, rawUrl);
          return `url(${quote}${toAssetUrl(resourcesPath, resourcePath)}${quote})`;
        },
      );
      // After rewriting, drop @font-face rules whose URL was probed and
      // found missing — they'd 403 the iframe and pollute the console.
      if (missingFonts.size > 0) {
        cssText = stripMissingFontFaces(cssText, missingFonts);
      }
      styleEl.textContent = cssText;
    }

    let readerStyle = doc.getElementById('nextpage-reader-overrides');
    if (!readerStyle) {
      readerStyle = doc.createElement('style');
      readerStyle.id = 'nextpage-reader-overrides';
      doc.head.appendChild(readerStyle);
    }
    readerStyle.textContent = buildReaderOverrideCss();

    // ::highlight() rules for the CSS Custom Highlight API must live in
    // their OWN style element — NEVER inside nextpage-reader-overrides,
    // because refreshReaderStyles() rewrites that element's textContent
    // on every settings change and would wipe the rules. One rule per
    // canonical color (the overlay maps every highlight to a canonical
    // color before registering, so static rules suffice). Each hex is
    // resolved through nearestHighlightHex before the rgba conversion so
    // the fill always derives from the canonical palette (HPU-2).
    // Injecting here means the rules survive settings changes and chapter
    // reloads.
    const highlightStyle = doc.createElement('style');
    highlightStyle.id = 'nextpage-highlight-styles';
    highlightStyle.textContent = HIGHLIGHT_COLORS.map(
      (color) =>
        `::highlight(epub-hl-${color.label}) { background-color: ${highlightFillRgba(nearestHighlightHex(color.hex), 0.4)}; }`,
    ).join('\n');
    doc.head.appendChild(highlightStyle);

    // Inlined CFI bridge (see cfiBridgeIframe.ts). Mounts as
    // `window.__cfiBridge` for the selection script to call. Must run
    // before the selection script and before the spine-init script.
    const errorScript = doc.createElement('script');
    errorScript.textContent = `window.onerror = function(msg, url, line, col, err){ try{ window.parent.postMessage({type:'epub-srcdoc-error', msg: String(msg), line: line, col: col, url: String(url)}, '*'); }catch(e){} };`;
    doc.head.prepend(errorScript);
    const bridgeScript = doc.createElement('script');
    bridgeScript.textContent = IFRAME_CFI_BRIDGE_SCRIPT;

    // Spine init: register the ordered chapter hrefs so the bridge can
    // compute the spine index for the current chapter's CFI. The
    // hrefs are JSON-serialised; we trust them because they come from
    // the parent (which got them from the Rust `parse_epub` command,
    // a trusted source).
    const spineScript = doc.createElement('script');
    spineScript.textContent = `
      (function() {
        try {
          window.__cfiBridge.setSpine(${JSON.stringify(spineHrefs)});
        } catch (e) {
          console.warn('epub-cfi: failed to set spine', e);
        }
      })();
    `;

    const resizeScript = doc.createElement('script');
    resizeScript.textContent = `
      (function() {
        function notifyResize() {
          window.parent.postMessage({ type: 'epub-resize' }, '*');
        }
        window.addEventListener('load', notifyResize);
        if (document.readyState === 'complete') notifyResize();
        if (typeof ResizeObserver !== 'undefined') {
          new ResizeObserver(notifyResize).observe(document.body);
        }
      })();
    `;

    // The highlight overlay registers persisted highlights via the CSS
    // Custom Highlight API (CSS.highlights + ::highlight(), zero DOM
    // mutation). It must run after the bridge (which it calls via
    // __cfiBridge.cfiToRange) and before the selection script (which
    // calls its hitTest/rangeOverlapsHighlight helpers).
    const highlightOverlayScript = doc.createElement('script');
    highlightOverlayScript.textContent = IFRAME_HIGHLIGHT_OVERLAY_SCRIPT;

    const selectionScript = doc.createElement('script');
    selectionScript.textContent = `
      (function() {
        var timer = null;
        var mouseupDebounceTimer = null;
        var CHAPTER_HREF = ${JSON.stringify(normalizeHref(currentChapterHref))};
        var CHAPTER_INDEX = ${chapterIndex};

        // HM-3 / HM-4: click handler via overlay hit-testing (caretRangeFromPoint
        // + inclusive boundary math against the overlay's per-id side-table).
        // The DOM is never mutated, so there are no .epub-hl spans to walk.
        // The iframe script owns this because the parent can't access the
        // iframe's DOM directly via postMessage alone (no Range object). We
        // stopPropagation to prevent the document-level mouseup from
        // triggering Menu 1.
        document.addEventListener('click', function(ev) {
          var hit = null;
          try {
            if (window.__epubHighlightOverlay &&
                typeof window.__epubHighlightOverlay.hitTest === 'function') {
              hit = window.__epubHighlightOverlay.hitTest(ev.clientX, ev.clientY);
            }
          } catch (e) {
            hit = null;
          }
          if (!hit) return;
          ev.stopPropagation();
          if (ev.preventDefault) ev.preventDefault();
          if (mouseupDebounceTimer) {
            clearTimeout(mouseupDebounceTimer);
            mouseupDebounceTimer = null;
          }
          if (timer) {
            clearTimeout(timer);
            timer = null;
          }
          var sel = window.getSelection();
          if (sel) sel.removeAllRanges();
          window.parent.postMessage({
            type: 'epub-highlight-click',
            id: hit.id,
            x: ev.clientX,
            y: ev.clientY,
            color: hit.color,
            text: hit.text,
            pageNumber: CHAPTER_INDEX
          }, '*');
        }, true);

        document.addEventListener('mouseup', function() {
          if (timer) clearTimeout(timer);
          if (mouseupDebounceTimer) clearTimeout(mouseupDebounceTimer);
          mouseupDebounceTimer = setTimeout(function() {
            mouseupDebounceTimer = null;
            var sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.toString().trim()) return;
            var text = sel.toString().trim();
            var range = sel.getRangeAt(0);
            // If the selection overlaps a registered highlight, skip
            // Menu 1 -- Menu 2 owns that gesture. The overlay tests the
            // live selection Range against its per-id side-table.
            var overlapsHl = false;
            try {
              if (window.__epubHighlightOverlay &&
                  typeof window.__epubHighlightOverlay.rangeOverlapsHighlight === 'function') {
                overlapsHl = window.__epubHighlightOverlay.rangeOverlapsHighlight(range);
              }
            } catch (e) {
              overlapsHl = false;
            }
            if (overlapsHl) return;
            // SEL-1 / SEL-3: SelectionToolbar treats bounds as
            // CONTAINER-relative (the iframe is the container here) and
            // container as PARENT-viewport. So we must NOT add frameRect
            // to bounds -- the iframe-local rect IS already in container-
            // relative coords. We only add frameRect to container.
            var frameRect = (window.frameElement && window.frameElement.getBoundingClientRect)
              ? window.frameElement.getBoundingClientRect()
              : { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 };
            var rect = range.getBoundingClientRect();
            var bounds = {
              left: rect.left,
              top: rect.top,
              right: rect.right,
              bottom: rect.bottom
            };
            var containerRect = {
              left: frameRect.left,
              top: frameRect.top,
              width: frameRect.width,
              height: frameRect.height
            };
            var clientRects = range.getClientRects();
            var rects = [];
            for (var i = 0; i < clientRects.length; i++) {
              var r = clientRects[i];
              rects.push({
                left: r.left,
                top: r.top,
                width: r.width,
                height: r.height
              });
            }
            // CFI-1: compute the CFI for the selection in this chapter.
            // Done in the iframe because the bridge needs the chapter's
            // Document, which only exists here. The DOM is never
            // mutated by highlight rendering (CSS Highlights never
            // touch the document), so the live DOM IS the pristine DOM
            // -- capture and resolution always see identical structure.
            // The direct bridge call is therefore sufficient; the
            // pristine-copy computeCFI fallback is gone.
            var cfi = null;
            try {
              if (window.__cfiBridge &&
                  typeof window.__cfiBridge.rangeToCFI === 'function') {
                cfi = window.__cfiBridge.rangeToCFI(range, CHAPTER_HREF, document);
              }
            } catch (e) {
              console.warn('epub-cfi: rangeToCFI threw', e);
            }
            window.parent.postMessage({
              type: 'epub-selection',
              text: text,
              bounds: bounds,
              container: containerRect,
              rects: rects,
              pageNumber: CHAPTER_INDEX,
              cfi: cfi
            }, '*');
          }, 100);
        });
        document.addEventListener('selectionchange', function() {
          var sel = window.getSelection();
          if (!sel || sel.isCollapsed) {
            if (timer) clearTimeout(timer);
            timer = setTimeout(function() {
              window.parent.postMessage({ type: 'epub-selection', text: '', pageNumber: CHAPTER_INDEX }, '*');
            }, 200);
          }
        });
        // Scroll dismiss: when the user scrolls the chapter, the selection
        // is still in the DOM but the user can't see it -- dismiss the
        // Menu 1 toolbar so it stops "floating" over unrelated content.
        // Browsers don't fire selectionchange on scroll, so we need this.
        var scrollDismissTimer = null;
        function dismissOnScroll() {
          if (scrollDismissTimer) clearTimeout(scrollDismissTimer);
          scrollDismissTimer = setTimeout(function() {
            window.parent.postMessage({ type: 'epub-selection', text: '', pageNumber: CHAPTER_INDEX }, '*');
          }, 50);
        }
        window.addEventListener('scroll', dismissOnScroll, true);
        document.addEventListener('scroll', dismissOnScroll, true);
      })();
    `;

    // CRITICAL ORDER: strip dangerous EPUB scripts BEFORE appending our own.
    // A previous version of this code stripped scripts AFTER appending, which
    // also stripped our injected bridge/spine/overlay/resize/selection scripts
    // and left the iframe with zero JavaScript — selection events fired but no
    // postMessage ever reached the parent (see git history for the bug).
    for (const script of doc.querySelectorAll('script')) {
      script.remove();
    }

    // Scripts run in document order. The bridge must mount before the
    // selection script (which calls window.__cfiBridge.rangeToCFI) and
    // before the spine init (which calls setSpine). The highlight
    // overlay must mount after the bridge (it calls __cfiBridge.cfiToRange)
    // but before the selection script (so the click handler is in place
    // when the user clicks a rendered highlight).
    doc.body.appendChild(bridgeScript);
    doc.body.appendChild(spineScript);
    doc.body.appendChild(highlightOverlayScript);
    doc.body.appendChild(resizeScript);
    doc.body.appendChild(selectionScript);

    // Use outerHTML instead of XMLSerializer for HTML5-compliant serialization
    // XMLSerializer produces XHTML self-closing tags that break HTML5 parsing
    const serialized = doc.documentElement.outerHTML;
    if (currentChapterHref.includes('HM-colombia-5')) {
      console.warn('SERIALIZED SNIPPET HM5 START', serialized.slice(0, 6000));
      console.warn('SERIALIZED SNIPPET HM5 END', serialized.slice(-8000));
      console.warn('BRIDGE SNIPPET', IFRAME_CFI_BRIDGE_SCRIPT.slice(1800, 2400));
    }
    return `<!DOCTYPE html>\n${serialized}`;
  }

  function refreshReaderStyles(): void {
    if (!iframeEl?.contentDocument) return;

    const doc = iframeEl.contentDocument;
    // `head` can be null while the iframe is still mid-load (e.g. about:srcdoc
    // before the chapter DOM is parsed). Bail out instead of throwing — the
    // next settings tick or chapter render will apply the styles.
    const head = doc.head;
    if (!head) return;
    let readerStyle = doc.getElementById('nextpage-reader-overrides');
    if (!readerStyle) {
      readerStyle = doc.createElement('style');
      readerStyle.id = 'nextpage-reader-overrides';
      head.appendChild(readerStyle);
    }

    let css = buildReaderOverrideCss();
    const userFont = fontFamily.trim();
    if (userFont && userFont !== 'serif') {
      css += `
        body {
          font-family: ${userFont}, serif !important;
        }
      `;
    }
    readerStyle.textContent = css;
    syncIframeHeight();
  }

  /**
   * Scroll a resolved CFI range into view inside the chapter iframe.
   * Uses the in-iframe CFI bridge (`cfiToRange`) to resolve the CFI to a
   * DOM range, then scrolls the first element of the range into view.
   */
  function scrollToCfi(cfi: string): void {
    if (!metadata || !iframeEl?.contentDocument || !iframeEl.contentWindow) return;
    const doc = iframeEl.contentDocument;
    const bridge = (
      iframeEl.contentWindow as Window & {
        __cfiBridge?: { cfiToRange: (cfi: string, href: string, doc: Document) => Range | null };
      }
    ).__cfiBridge;
    const rawChapterHref =
      getSpineHrefs()[currentSpineIndex] ?? stripFragment(getToc()[currentChapterIndex]?.href ?? '');
    const chapterHref = normalizeHref(rawChapterHref);
    if (!bridge || !chapterHref) return;

    try {
      const range = bridge.cfiToRange(cfi, chapterHref, doc);
      if (!range || !range.startContainer) return;
      const targetNode =
        range.startContainer.nodeType === Node.TEXT_NODE
          ? range.startContainer.parentElement
          : (range.startContainer as Element);
      if (targetNode) {
        (targetNode as Element).scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
      pendingCfiScroll = null;
    } catch (err) {
      console.warn('epub-cfi: scrollToCfi failed', err);
    }
  }

  /** Emit the precise CFI at the top visible text node in the chapter iframe. */
  function emitPreciseLocation(): void {
    if (!metadata || !iframeEl?.contentDocument || !iframeEl.contentWindow) return;

    const doc = iframeEl.contentDocument;
    const bridge = (
      iframeEl.contentWindow as Window & {
        __cfiBridge?: {
          rangeToCFI: (range: Range, href: string, document: Document) => string | null;
        };
      }
    ).__cfiBridge;
    const rawChapterHref =
      getSpineHrefs()[currentSpineIndex] ?? stripFragment(getToc()[currentChapterIndex]?.href ?? '');
    const chapterHref = normalizeHref(rawChapterHref);
    if (!bridge || !chapterHref) return;

    const nodes: Text[] = [];
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
      if ((node.nodeValue ?? '').trim().length > 0) nodes.push(node as Text);
      node = walker.nextNode();
    }
    const chapterChars = nodes.reduce((total, textNode) => total + (textNode.data?.length ?? 0), 0);
    if (chapterChars <= 0) return;

    const visibleNode = nodes.find((textNode) => {
      const range = doc.createRange();
      range.selectNodeContents(textNode);
      const rect = range.getBoundingClientRect();
      return rect.bottom >= 0 && rect.top <= (iframeEl?.clientHeight ?? window.innerHeight);
    });
    if (!visibleNode) return;

    const range = doc.createRange();
    range.setStart(visibleNode, 0);
    range.setEnd(visibleNode, Math.min(1, visibleNode.data.length));
    const preciseCfi = bridge.rangeToCFI(range, chapterHref, doc);
    if (!preciseCfi) return;

    const charOffset = nodes
      .slice(0, nodes.indexOf(visibleNode))
      .reduce((total, textNode) => total + textNode.data.length, 0);
    const locator = locatorFromCfi(
      getSpineHrefs(),
      preciseCfi,
      {
        chapterChars,
        charOffset,
      },
    );
    if (!locator) return;

    const progression = locator.locations.progression ?? 0;
    const percentage = ((currentSpineIndex + progression) / totalChapters) * 100;
    onLocationChange?.(preciseCfi, percentage);
    onLocationContext?.({ locator: locatorToJson(locator), percentage });
  }

  // ─── Render Chapter ──────────────────────────────────────
  // lastRenderedChapter is ONLY written inside markReady after bridge+overlay
  // confirmed. Never reset to -1 outside initReader (remount resets via $state).
  async function renderChapter(index: number): Promise<void> {
    if (!metadata || !iframeEl) return;
    const myEpoch = ++renderEpoch;
    currentRenderIndex = index;
    // currentChapterIndex is always TOC position (0..toc.length-1);
    // resolve the real spine index for the Rust cache (authority: spineHrefs).
    const spineIndex = spineIndexForToc(index);
    const tocEntry = getToc()[index];
    const tocHrefForLog = tocEntry?.href ?? '';
    const spineHrefForLog = getSpineHrefs()[spineIndex] ?? `(spine ${spineIndex})`;
    // Preserve fragment for scrollToFragment
    const frag = extractFragment(tocHrefForLog);
    if (frag) pendingFragment = frag;
    console.warn(
      'epub-hl: renderChapter called tocIndex=',
      index,
      'spineIndex=',
      spineIndex,
      'epoch=',
      myEpoch,
      'srcdoc CHAPTER_INDEX(spine)=',
      spineIndex,
      'spineHrefs',
      getSpineHrefs().length,
      'chapterHref(toc)',
      tocHrefForLog,
      'spineHref',
      spineHrefForLog,
      'fragment',
      frag ?? '(none)',
    );
    if (spineIndex !== index) {
      console.warn('epub-toc: renderChapter toc', index, '-> spine', spineIndex, 'href', tocHrefForLog);
    }

    try {
      const chapterData = await invoke<EpubChapterContent>('get_epub_chapter', {
        bookId,
        chapterIndex: spineIndex,
      });

      // Stale-epoch guard: abort before mutating DOM if a newer render started while awaiting.
      if (myEpoch !== renderEpoch || currentRenderIndex !== index) {
        console.warn(
          'epub-hl: renderChapter stale abort before srcdoc index',
          index,
          'epoch',
          myEpoch,
          'current',
          renderEpoch,
        );
        return;
      }

      currentChapterIndex = index;
      iframeContentHeight = 0;

      const spineHrefs = getSpineHrefs();
      const tocHrefRaw = getToc()[index]?.href ?? '';
      const chapterHref = normalizeHref(stripFragment(tocHrefRaw) || spineHrefs[spineIndex] || '');
      // Probe every @font-face URL the chapter declares. Those that 404/403
      // (typically because the EPUB references commercial fonts like
      // Neutraface or Felt Tip Roman without bundling the file) get their
      // rule stripped from the CSS so the iframe doesn't 403 on every load.
      const fontUrls = collectFontFaceAssetUrls(
        chapterData.html,
        chapterData.chapterPath,
        metadata.resourcesPath,
      );
      const missingFonts = await probeMissingFontUrls(fontUrls);
      const srcdoc = buildChapterSrcdoc(
        chapterData,
        metadata.resourcesPath,
        spineHrefs,
        chapterHref,
        spineIndex,
        missingFonts,
      );

      iframeEl.onload = () => {
        syncIframeHeight();
        if (zoomContainerEl) {
          zoomContainerEl.scrollTop = 0;
        }
        // Wait for bridge + overlay to mount before marking chapter as rendered.
        // This eliminates the race where lastRenderedChapter === index but
        // win.__epubHighlightOverlay is still undefined, and also avoids
        // capturing a stale/empty persistedHighlights snapshot inside onload.
        // The reactive highlights effect owns the actual render.
        const maxRetries = 25;
        const interval = 20;
        let attempt = 0;
        function markReady(): void {
          // Stale-epoch guard: if a newer renderChapter started, this onload is obsolete.
          if (myEpoch !== renderEpoch || currentRenderIndex !== index) {
            console.warn(
              'epub-hl: markReady stale epoch',
              myEpoch,
              'current',
              renderEpoch,
              'index',
              index,
              'currentRenderIndex',
              currentRenderIndex,
            );
            return;
          }
          const win = iframeEl?.contentWindow as
            | (Window & {
                __cfiBridge?: { setSpine: (h: string[]) => void };
                __epubHighlightOverlay?: {
                  render: (h: unknown[], chapterHref: string, idx: number) => void;
                  isReady?: () => boolean;
                };
              })
            | null;
          const bridgeReady = !!win?.__cfiBridge && typeof win.__cfiBridge.setSpine === 'function';
          const overlayReady = !!win?.__epubHighlightOverlay;
          if (bridgeReady && overlayReady) {
            try {
              win!.__cfiBridge!.setSpine(spineHrefs);
            } catch (e) {
              console.warn('epub-cfi: failed to re-init iframe on load', e);
            }
            // Mark chapter as rendered ONLY after bridge+overlay confirmed.
            // The reactive highlights effect (which tracks persistedHighlights)
            // will then perform the actual render with the current value.
            lastRenderedChapter = index;
            requestAnimationFrame(emitPreciseLocation);
            if (pendingCfiScroll) {
              const cfi = pendingCfiScroll;
              requestAnimationFrame(() => scrollToCfi(cfi));
            }
            if (pendingFragment) {
              const frag = pendingFragment;
              requestAnimationFrame(() => scrollToFragment(frag));
            }
            return;
          }
          if (attempt++ < maxRetries) {
            setTimeout(markReady, interval);
          } else {
            console.warn(
              'epub-hl: onload markReady timed out bridgeReady=',
              bridgeReady,
              'overlayReady=',
              overlayReady,
            );
            try {
              if (bridgeReady) win!.__cfiBridge!.setSpine(spineHrefs);
            } catch {}
            // Avoid blocking forever: mark as rendered so highlights effect
            // can retry overlay-not-ready case itself.
            lastRenderedChapter = index;
            requestAnimationFrame(emitPreciseLocation);
            if (pendingCfiScroll) {
              const cfi = pendingCfiScroll;
              requestAnimationFrame(() => scrollToCfi(cfi));
            }
            if (pendingFragment) {
              const frag = pendingFragment;
              requestAnimationFrame(() => scrollToFragment(frag));
            }
          }
        }
        markReady();
      };
      iframeEl.srcdoc = srcdoc;
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      setReaderError(error);
    }
  }

  // ─── Navigation ──────────────────────────────────────────
  function goToPrev(): void {
    if (currentChapterIndex > 0) {
      const prevIdx = currentChapterIndex - 1;
      const frag = extractFragment(getToc()[prevIdx]?.href ?? '');
      if (frag) pendingFragment = frag;
      currentChapterIndex = prevIdx;
    }
  }

  function goToNext(): void {
    const tocLen = getToc().length;
    const nextIdx = currentChapterIndex + 1;
    // Prefer TOC navigation; fallback to spine length for edge docs not in TOC
    const limit = tocLen > 0 ? tocLen : totalChapters;
    if (nextIdx < limit) {
      const frag = extractFragment(getToc()[nextIdx]?.href ?? '');
      if (frag) pendingFragment = frag;
      currentChapterIndex = nextIdx;
    } else if (nextIdx < totalChapters) {
      currentChapterIndex = nextIdx;
    }
  }

  function goToChapter(index: number): void {
    const tocLen = getToc().length;
    const limit = tocLen > 0 ? tocLen : totalChapters;
    if (index >= 0 && index < limit) {
      const frag = extractFragment(getToc()[index]?.href ?? '');
      if (frag) pendingFragment = frag;
      else pendingFragment = null;
      currentChapterIndex = index;
    } else if (index >= 0 && index < totalChapters) {
      // Spine doc not in TOC (e.g., cover) — allow direct spine navigation via toc index fallback
      const frag2 = extractFragment(getToc()[index]?.href ?? '');
      if (frag2) pendingFragment = frag2;
      currentChapterIndex = index;
    }
  }

  async function handleGoToPage(page: number): Promise<boolean> {
    const spineIdx = page - 1;
    if (spineIdx < 0 || spineIdx >= totalChapters) return false;
    // Map spine page to TOC position when possible (Historia offset-2)
    const tocIdx = tocIndexForSpine(spineIdx, getSpineHrefs()[spineIdx]);
    if (tocIdx !== null && tocIdx >= 0 && tocIdx < getToc().length) {
      goToChapter(tocIdx);
      return true;
    }
    // Fallback: treat page as TOC index when spine not mapped (e.g., linear cover)
    if (spineIdx >= 0 && spineIdx < getToc().length) {
      goToChapter(spineIdx);
      return true;
    }
    // Last resort: direct spine fallback (may render blank if toc length smaller)
    if (spineIdx >= 0 && spineIdx < totalChapters) {
      goToChapter(Math.min(spineIdx, getToc().length - 1));
      return true;
    }
    return false;
  }

  // ─── Zoom ─────────────────────────────────────────────────
  function changeZoom(delta: number): void {
    const newZoom = Math.max(50, Math.min(200, zoomLevel + delta));
    if (newZoom !== zoomLevel) {
      zoomLevel = newZoom;
    }
  }

  function handleWheel(e: WheelEvent): void {
    if (!e.ctrlKey && !e.metaKey) return;
    e.preventDefault();
    const delta = e.deltaY > 0 ? -5 : 5;
    changeZoom(delta);
  }

  // ─── Theme ────────────────────────────────────────────────
  function getThemeStyles(): string {
    const themes: Record<string, string> = {
      paper: `
        body { background: #faf8f5; color: #333; }
        a { color: #3366cc; }
      `,
      sepia: `
        body { background: #f5eedd; color: #5b4636; }
        a { color: #8b6914; }
      `,
      night: `
        body { background: #0f1320; color: #c8ccd8; }
        a { color: #7bb8ff; }
      `,
      dark: `
        body { background: #1a1a2e; color: #e0e0e0; }
        a { color: #66bbff; }
      `,
      blue: `
        body { background: #1e3a5f; color: #d6e4f0; }
        a { color: #88ccff; }
      `,
    };

    return themes[themeMode] || themes.paper;
  }

  function getThemeBgColor(): string {
    const bgs: Record<string, string> = {
      paper: '#faf8f5',
      sepia: '#f5eedd',
      night: '#0f1320',
      dark: '#1a1a2e',
      blue: '#1e3a5f',
    };
    return bgs[themeMode] || bgs.paper;
  }

  function handleKeydown(e: KeyboardEvent): void {
    if (e.key === 'ArrowLeft') goToPrev();
    if (e.key === 'ArrowRight') goToNext();
    if ((e.ctrlKey || e.metaKey) && (e.key === '=' || e.key === '+' || e.key === '-')) {
      e.preventDefault();
      const step = e.key === '-' ? -10 : 10;
      changeZoom(step);
    }
  }
</script>

<svelte:window onkeydown={handleKeydown} />

<div
  class="flex flex-col h-full w-full min-h-0 outline-none relative"
  tabindex="-1"
  role="presentation"
  class:w-full={isFullscreen}
  class:h-full={isFullscreen}
>
  {#if isLoading}
    <div class="flex flex-col items-center justify-center h-full gap-4">
      <div class="h-1 w-full max-w-48 bg-(--color-border)/20 overflow-hidden rounded-full">
        <div class="h-full w-1/3 bg-(--color-accent) animate-pulse rounded-full"></div>
      </div>
      <span class="text-sm opacity-60">{t('epub.loading')}</span>
    </div>
  {:else if error}
    <div class="flex items-center justify-center h-full text-sm text-red-600 px-4 text-center">
      {t('epub.error')}: {error}
    </div>
  {:else}
    <!-- EpubControls top bar — spine-authoritative pagination -->
    <EpubControls
      currentPage={currentSpineIndex + 1}
      totalPages={totalChapters}
      currentPercentage={((currentSpineIndex + 0.5) / totalChapters) * 100}
      {fontSize}
      {isFullscreen}
      {t}
      onPrev={goToPrev}
      onNext={goToNext}
      onGoToPage={handleGoToPage}
      onFontSizeChange={(size: number) => {
        fontSize = size;
        const updated: ReaderSettings = {
          ...readerSettings,
          epub: { ...readerSettings.epub, fontSize: size },
        };
        onSettingsChange?.(updated);
      }}
      onToggleFullscreen={() => onToggleFullscreen?.()}
      onToggleToc={() => onToggleToc?.()}
    />

    <!-- Chapter iframe — parent scrolls, iframe grows to content height -->
    <div
      class="flex-1 w-full min-h-0 overflow-y-auto pb-16"
      style="background: {getThemeBgColor()};"
      bind:this={zoomContainerEl}
      onwheel={handleWheel}
      onscroll={emitPreciseLocation}
    >
      <iframe
        bind:this={iframeEl}
        class="w-full border-none block"
        class:outline-2={debugState.enabled}
        class:outline-dashed={debugState.enabled}
        class:outline-red-500={debugState.enabled}
        style:height={iframeContentHeight > 0 ? `${iframeContentHeight}px` : 'auto'}
        title="chapter"
      ></iframe>
    </div>
  {/if}
</div>
