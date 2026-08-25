<script lang="ts">
  import * as pdfjsLib from 'pdfjs-dist';
  import 'pdfjs-dist/web/pdf_viewer.css';
  import { onMount } from 'svelte';
  import {
    createPdfDocument,
    loadPdfOutline,
    clearDocumentCache,
    removeCachedDocument,
    setCachedDocument,
  } from '$lib/features/reader/viewer-pdf/pdfStreaming';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { PdfOutlineItem, ReaderSettings } from '$lib/shared/types';
  import {
    adjustPdfScaleForWheel,
    clampPdfScale,
    DEFAULT_PDF_SCALE,
    isPageWithinBounds,
    PDF_SCALE_STEP,
  } from '$lib/features/reader/viewer-pdf/pdfNavigation';
  import { resolveReaderArrowIntent } from '$lib/features/reader/viewer-epub/keyboardNav';
  import {
    TOOLBAR_OFFSET,
    TOOLBAR_WIDTH_ESTIMATE,
    TOOLBAR_EDGE_PADDING,
    VERTICAL_SCROLL_STEP_PX,
    ZOOM_EPSILON,
    resolveThemePalette,
    clamp,
    clampSelectionPoint,
    type SelectionOverlayRect,
  } from '$lib/features/reader/viewer-pdf/pdfState.svelte';
  import {
    buildSelectionOverlayRects,
    readProgressPercent,
    parseLocatorPage,
    captureScrollAnchor,
    restoreScrollAnchor,
    canScrollElementInDirection,
    isRefLike,
    flattenOutline,
  } from '$lib/features/reader/viewer-pdf/pdfSelection';
  import { SafeTextLayer } from '$lib/features/reader/viewer-pdf/safeTextLayer';

  import PdfControls from './PdfControls.svelte';
  import PdfSelectionOverlay from './PdfSelectionOverlay.svelte';
  import PdfLoadingOverlay from './PdfLoadingOverlay.svelte';
  import PdfTocSidebar from './PdfTocSidebar.svelte';

  import type { TocEntry } from '../chrome/ReaderTocPanel.svelte';
  import { debugState } from '$lib/shared/debug/debugState.svelte';
  import { setReaderError, clearReaderError } from '$lib/stores/readerErrorState.svelte';

  type PersistedHighlight = {
    id: string;
    color: string;
    pageNumber: number;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    text?: string;
  };

  type Props = {
    filePath: string;
    bookId?: string;
    initialPage?: number;
    onPageChange?: (page: number, total: number) => void;
    searchTargetLocator?: string | null;
    onSessionProgress?: (event: {
      startedAt: string;
      endedAt?: string;
      durationSeconds: number;
      startPercentage?: number;
      endPercentage?: number;
    }) => void;
    readerSettings?: ReaderSettings;
    isFullscreen?: boolean;
    onToggleFullscreen?: () => void;
    onselection?: (event: {
      text: string;
      bounds: { left: number; top: number; right: number; bottom: number };
      container: { left: number; top: number; width: number; height: number };
      placement: string;
      rects: Array<{ left: number; top: number; width: number; height: number }>;
      pageNumber: number;
    }) => void;
    onselectionclear?: () => void;
    onHighlightAction?: (
      action: import('$lib/shared/types/book').HighlightActionKind,
      id: string,
      opts?: import('$lib/shared/types/book').HighlightActionOpts,
    ) => void;
    onTocReady?: (entries: TocEntry[]) => void;
    externalTocNavigate?: TocEntry | null;
    persistedHighlights?: PersistedHighlight[];
    preloadedBytes?: Uint8Array | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  const DEFAULT_READER_SETTINGS: ReaderSettings = {
    themeMode: 'paper',
    brightness: 100,
    contrast: 100,
    epub: {
      fontSize: 100,
      fontFamily: 'serif',
    },
    selectionColor: 'var(--color-accent-blue)',
    lineHeight: 1.8,
    letterSpacing: 0,
    paragraphSpacing: 1,
    textAlign: 'left',
    direction: 'ltr',
    hyphenation: false,
    verticalScrolling: false,
    margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
    showHeader: true,
    showFooter: true,
    showPageNumbers: true,
    progressIndicator: 'percentage',
  };

  let {
    filePath,
    initialPage = 1,
    onPageChange,
    searchTargetLocator = null,
    onSessionProgress,
    readerSettings = DEFAULT_READER_SETTINGS,
    isFullscreen = false,
    onToggleFullscreen,
    onselection,
    onselectionclear,
    onHighlightAction,
    onTocReady,
    externalTocNavigate = null,
    persistedHighlights = [],
    preloadedBytes = null,
    t,
  }: Props = $props();

  // ── Reactive State ──────────────────────────────────────
  let canvas: HTMLCanvasElement | undefined = $state();
  let textLayer: HTMLDivElement | undefined = $state();
  let viewerRoot: HTMLDivElement | undefined = $state();
  let canvasContainer: HTMLDivElement | undefined = $state();
  let currentPage = $state(1);
  let totalPages = $state(0);
  let flashSearchResult = $state(false);
  let sessionStartAt = new Date().toISOString();
  let lastPercent = 0;
  let scale = $state(DEFAULT_PDF_SCALE);
  let isLoading = $state(true);
  let loadProgress = $state(0);
  let loadProgressMax = $state(0);
  let error = $state<string | null>(null);
  let navigationError = $state<string | null>(null);
  let showToc = $state(false);
  let outline = $state<PdfOutlineItem[]>([]);
  let tocLoading = $state(false);
  let tocError = $state<string | null>(null);
  let outlineDeferred = $state(false);
  let isViewerFocused = $state(false);

  let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null;
  let currentPageObj: pdfjsLib.PDFPageProxy | null = null;

  let selectionPlacement = $state<'above' | 'below'>('above');
  let activeHighlightId = $state<string | null>(null);

  let activeLoadRequestId = 0;
  let activeNavigationRequestId = 0;
  let activeLoadingTask: pdfjsLib.PDFDocumentLoadingTask | null = null;
  let activeRenderTask: pdfjsLib.RenderTask | null = null;
  let textLayerInstance: SafeTextLayer | null = null;
  let pendingWheelFrame: number | null = null;
  let pendingWheelDelta = 0;
  let lastLoadedFilePath: string | null = null;
  const outlinePageCache = new Map<string, number>();

  type PdfRefProxy = Parameters<pdfjsLib.PDFDocumentProxy['getPageIndex']>[0];

  const isStaleLoad = (requestId: number): boolean => requestId !== activeLoadRequestId;
  const isStaleNavigation = (requestId: number): boolean => requestId !== activeNavigationRequestId;

  const readerThemePalette = $derived(resolveThemePalette(readerSettings.themeMode));
  const visualFilterStyle = $derived(
    `brightness(${clamp(readerSettings.brightness, 50, 150)}%) contrast(${clamp(readerSettings.contrast, 50, 150)}%)`,
  );
  const canvasWrapperStyle = $derived(
    // Don't constrain wrapper to viewport — let the canvas dictate size.
    // overflow-auto on .pdf-canvas-container handles scrolling when zoomed in.
    ``, // empty: canvas's own style.width / style.height drive the layout
  );

  // PDF renders at its intrinsic size — EPUB text margins are inappropriate here.
  // Set padding to 0 so the PDF uses the full available space.
  const canvasContainerPaddingStyle = $derived('0');

  // Reactively apply letter-spacing to the text layer so changes take effect
  // immediately without requiring a page re-render. Line-height is NOT applied:
  // pdfjs positions every span absolutely (top = baseline - ascent, scaled), so
  // an inflated line-height only grows each span's BOX below the glyphs (it does
  // not move the text). That box inflation leaks into range.getClientRects() and
  // makes multi-line selection rects overshoot ~1 line below the last selected
  // line. Keeping the stylesheet's `line-height: 1` (pdf_viewer.css) fixes the
  // rects at the source for both the transient overlay and persisted highlights.
  $effect(() => {
    if (textLayer) {
      textLayer.style.letterSpacing = `${readerSettings.letterSpacing}px`;
    }
  });

  const flatOutline = $derived(flattenOutline(outline));

  // ── Emit TOC entries ────────────────────────────────────
  $effect(() => {
    if (flatOutline.length > 0) {
      onTocReady?.(
        flatOutline.map((entry) => ({
          id: entry.item.id,
          title: entry.item.title,
          depth: entry.depth,
        })),
      );
    }
  });

  $effect(() => {
    if (externalTocNavigate && externalTocNavigate.id) {
      const entry = flatOutline.find((e) => e.item.id === externalTocNavigate.id);
      if (entry) {
        navigateToOutlineItem(entry.item);
      }
    }
  });

  // ── Fullscreen API check ────────────────────────────────
  const canUseFullscreenApi = (): boolean => {
    if (typeof document === 'undefined') return false;
    return (
      typeof viewerRoot?.requestFullscreen === 'function' &&
      typeof document.exitFullscreen === 'function'
    );
  };

  // ── Text layer instance helper ──────────────────────────
  const cancelTextLayer = (): void => {
    textLayerInstance?.cancel();
    textLayerInstance = null;
  };

  const clearSelectionUi = (): void => {
    onselectionclear?.();
  };

  // ── Render cancellation helpers ─────────────────────────
  const cancelActiveRenderTask = async (): Promise<void> => {
    if (!activeRenderTask) return;
    const task = activeRenderTask;
    activeRenderTask = null;
    task.cancel();
    try {
      await task.promise;
    } catch {
      /* expected */
    }
  };

  const destroyActiveLoadingTask = (): void => {
    if (!activeLoadingTask) return;
    activeLoadingTask.destroy();
    activeLoadingTask = null;
  };

  const destroyCurrentDocument = async (): Promise<void> => {
    if (!pdfDoc) return;
    const current = pdfDoc;
    pdfDoc = null;
    currentPageObj = null;
    const cachedPath = lastLoadedFilePath ?? filePath;
    removeCachedDocument(cachedPath);
    try {
      await current.loadingTask.destroy();
    } catch {
      /* swallow */
    }
  };

  // ── onMount ──────────────────────────────────────────────
  onMount(() => {
    pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
      'pdfjs-dist/build/pdf.worker.min.mjs',
      import.meta.url,
    ).toString();

    const handleFullscreenError = (): void => {
      navigationError = t('pdf.fullscreenUnsupported');
    };

    // Close the selection toolbar when the browser selection is cleared by a
    // click outside the viewer / on the canvas (header, footer, empty areas).
    // clearSelectionUi only forwards onselectionclear -> dismissToolbar (no
    // removeAllRanges), so this cannot kill a nascent click-drag selection.
    const handleSelectionChange = (): void => {
      const selection = window.getSelection();
      if (!selection || !selection.toString().trim()) {
        clearSelectionUi();
      }
    };

    document.addEventListener('fullscreenerror', handleFullscreenError);
    document.addEventListener('selectionchange', handleSelectionChange);

    return () => {
      activeLoadRequestId += 1;
      activeNavigationRequestId += 1;
      cancelTextLayer();
      if (pendingWheelFrame !== null) {
        window.cancelAnimationFrame(pendingWheelFrame);
        pendingWheelFrame = null;
      }
      pendingWheelDelta = 0;
      destroyActiveLoadingTask();
      void cancelActiveRenderTask();
      void destroyCurrentDocument();
      clearDocumentCache();
      document.removeEventListener('fullscreenerror', handleFullscreenError);
      document.removeEventListener('selectionchange', handleSelectionChange);
    };
  });

  $effect(() => {
    if (filePath && filePath !== lastLoadedFilePath) {
      lastLoadedFilePath = filePath;
      loadPdf();
    }
  });

  // ── Session progress ────────────────────────────────────
  const emitSessionProgress = (nextPage: number, nextTotal: number): void => {
    const now = new Date();
    const nextPercent = readProgressPercent(nextPage, nextTotal);
    const startedAt = sessionStartAt;
    const endedAt = now.toISOString();
    const started = new Date(startedAt);
    const durationSeconds = Math.max(0, Math.round((now.getTime() - started.getTime()) / 1000));
    onSessionProgress?.({
      startedAt,
      endedAt,
      durationSeconds,
      startPercentage: lastPercent,
      endPercentage: nextPercent,
    });
    sessionStartAt = endedAt;
    lastPercent = nextPercent;
  };

  // ── Outline resolution ──────────────────────────────────
  const resolveDestinationPage = async (
    dest: string | unknown[] | null,
  ): Promise<number | null> => {
    if (!pdfDoc || !dest || totalPages <= 0) return null;
    try {
      const resolvedDest = typeof dest === 'string' ? await pdfDoc.getDestination(dest) : dest;
      if (!Array.isArray(resolvedDest) || resolvedDest.length === 0) return null;
      const target = resolvedDest[0];
      if (typeof target === 'number' && Number.isFinite(target)) {
        return isPageWithinBounds(target + 1, totalPages) ? target + 1 : null;
      }
      if (!isRefLike(target)) return null;
      const cacheKey = `${target.num}:${target.gen}`;
      const cachedPage = outlinePageCache.get(cacheKey);
      if (cachedPage && isPageWithinBounds(cachedPage, totalPages)) return cachedPage;
      const pageIndex = await pdfDoc.getPageIndex(target as PdfRefProxy);
      const pageNumber = pageIndex + 1;
      if (!isPageWithinBounds(pageNumber, totalPages)) return null;
      outlinePageCache.set(cacheKey, pageNumber);
      return pageNumber;
    } catch {
      return null;
    }
  };

  async function navigateToOutlineItem(item: PdfOutlineItem): Promise<void> {
    if (!item.dest) return;
    navigationError = null;
    const page = await resolveDestinationPage(item.dest);
    if (!page) {
      navigationError = t('pdf.tocNavigationFailed');
      return;
    }
    const didNavigate = await navigateToPage(page);
    if (!didNavigate) {
      navigationError = t('pdf.tocNavigationFailed');
      return;
    }
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 900px)').matches) {
      showToc = false;
    }
  }

  // ── Load PDF ─────────────────────────────────────────────
  async function loadPdf(): Promise<void> {
    if (!filePath) return;

    const loadRequestId = ++activeLoadRequestId;
    const navRequestId = ++activeNavigationRequestId;
    cancelTextLayer();
    destroyActiveLoadingTask();
    await cancelActiveRenderTask();
    await destroyCurrentDocument();

    isLoading = true;
    loadProgress = 0;
    loadProgressMax = 0;
    error = null;
    navigationError = null;
    scale = DEFAULT_PDF_SCALE;
    showToc = false;
    outline = [];
    tocLoading = false;
    tocError = null;
    outlineDeferred = false;
    outlinePageCache.clear();

    try {
      let loadedDoc: pdfjsLib.PDFDocumentProxy;

      const USE_PRELOAD_THRESHOLD = 5 * 1024 * 1024;
      if (
        preloadedBytes &&
        preloadedBytes.length > 0 &&
        preloadedBytes.length <= USE_PRELOAD_THRESHOLD
      ) {
        const loadingTask = pdfjsLib.getDocument({ data: new Uint8Array(preloadedBytes) });
        loadingTask.onProgress = (progress: { loaded: number; total: number }) => {
          loadProgress = progress.loaded;
          loadProgressMax = progress.total;
        };
        loadedDoc = await loadingTask.promise;
        setCachedDocument(filePath, { document: loadedDoc, outline: [], outlineLoaded: false });
      } else {
        const result = await createPdfDocument(filePath, {
          onProgress: (loaded, total) => {
            loadProgress = loaded;
            loadProgressMax = total;
          },
        });
        loadedDoc = result.document;
      }

      if (isStaleLoad(loadRequestId)) {
        await loadedDoc.loadingTask.destroy();
        return;
      }

      pdfDoc = loadedDoc;
      totalPages = loadedDoc.numPages;

      // Default zoom is 100% (DEFAULT_PDF_SCALE = 1.0). We intentionally do NOT
      // fit-to-page here: the reader card keeps a fixed area and the canvas
      // container scrolls internally, so the page opens at 100% and the user
      // can zoom with the controls / Ctrl+wheel without the card resizing.

      const requestedPage = Math.max(1, initialPage || 1);
      const targetPage = Math.min(requestedPage, totalPages);

      const rendered = await renderPage(targetPage, {
        requestId: navRequestId,
        renderScale: scale,
      });
      if (!rendered || isStaleLoad(loadRequestId) || isStaleNavigation(navRequestId)) return;

      currentPage = targetPage;
      onPageChange?.(targetPage, totalPages);
      lastPercent = readProgressPercent(targetPage, totalPages);
      sessionStartAt = new Date().toISOString();
      error = null;
      clearReaderError();
    } catch (err) {
      if (isStaleLoad(loadRequestId)) return;
      error = err instanceof Error ? err.message : 'Failed to load PDF';
      setReaderError(error);
    } finally {
      if (!isStaleLoad(loadRequestId)) {
        isLoading = false;
        activeLoadingTask = null;
      }
    }
  }

  // ── Render page ──────────────────────────────────────────
  async function renderPage(
    pageNum: number,
    options: { requestId?: number; renderScale?: number } = {},
  ): Promise<boolean | void> {
    if (!pdfDoc || !canvas || !textLayer) return;

    if (typeof document !== 'undefined') await document.fonts.ready;

    const requestId = options.requestId ?? activeNavigationRequestId;
    const renderScale = options.renderScale ?? scale;

    const page = await pdfDoc.getPage(pageNum);
    if (isStaleNavigation(requestId)) return false;

    currentPageObj = page;
    const viewport = page.getViewport({ scale: renderScale });

    const outputScale = window.devicePixelRatio || 1;
    canvas.width = Math.round(viewport.width * outputScale);
    canvas.height = Math.round(viewport.height * outputScale);
    canvas.style.width = `${viewport.width}px`;
    canvas.style.height = `${viewport.height}px`;

    if (debugState.enabled) {
      debugState.readerInfo = {
        format: 'pdf',
        isTocOpen: showToc,
        isSearchOpen: false,
        isFullscreen,
        pageInfo: `${currentPage} / ${totalPages}`,
        scale,
      };
    }

    const context = canvas.getContext('2d');
    if (!context) return;

    context.setTransform(1, 0, 0, 1, 0, 0);
    const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : undefined;
    const renderContext = { canvasContext: context, viewport, canvas, transform };

    await cancelActiveRenderTask();
    const renderTask = page.render(renderContext);
    activeRenderTask = renderTask;

    try {
      await renderTask.promise;
    } catch (err) {
      if (activeRenderTask === renderTask) activeRenderTask = null;
      const isCancelled =
        typeof err === 'object' &&
        err !== null &&
        'name' in err &&
        String((err as { name?: string }).name) === 'RenderingCancelledException';
      if (isCancelled) return false;
      throw err;
    }

    if (activeRenderTask === renderTask) activeRenderTask = null;
    if (isStaleNavigation(requestId)) return false;

    // Container dimensions + CSS vars are set by SafeTextLayer constructor

    const textContent = await page.getTextContent();
    if (isStaleNavigation(requestId)) return false;

    await renderTextLayer(
      textContent as {
        items: Array<{ str: string; transform: number[]; width: number; height: number }>;
      },
      viewport,
      requestId,
    );
    return true;
  }

  // ── Render text layer ────────────────────────────────────
  async function renderTextLayer(
    textContent: {
      items: Array<{ str: string; transform: number[]; width: number; height: number }>;
    },
    viewport: pdfjsLib.PageViewport,
    requestId = activeNavigationRequestId,
  ): Promise<void> {
    if (!textLayer) return;
    if (isStaleNavigation(requestId)) return;

    // Cancel previous text layer instance
    textLayerInstance?.cancel();
    textLayerInstance = null;

    textLayer.innerHTML = '';
    textLayer.style.pointerEvents = 'auto';
    // position/left/top and CSS vars are now set by SafeTextLayer constructor

    try {
      if (pdfjsLib.TextLayer) {
        textLayerInstance = new SafeTextLayer({
          container: textLayer,
          viewport,
          textContentSource:
            textContent as unknown as import('$lib/features/reader/viewer-pdf/safeTextLayer').SafeTextLayerParams['textContentSource'],
        });
        await textLayerInstance.render();
      } else {
        const task = (
          pdfjsLib as unknown as {
            renderTextLayer?: (opts: Record<string, unknown>) => { promise: Promise<void> };
          }
        ).renderTextLayer?.({
          container: textLayer,
          viewport,
          textDivs: [],
          enhanceTextSelection: true,
          textContentSource: textContent,
        });
        if (task?.promise) await task.promise;
      }

      // Apply layout settings from readerSettings. Letter-spacing only (see the
      // $effect above: line-height must stay at the pdfjs `line-height: 1` so
      // span boxes don't inflate selection getClientRects()).
      textLayer.style.letterSpacing = `${readerSettings.letterSpacing}px`;

      // pdfjs-dist v5 TextLayer creates spans with pointer-events:none (from
      // pdf_viewer.css). Override inline so click-and-drag selection works.
      for (const span of textLayer.querySelectorAll('span')) {
        span.style.pointerEvents = 'auto';
      }
    } catch (err) {
      console.error('Text layer render error:', err);
      textLayerInstance = null;
    }
  }

  // ── Selection handling ───────────────────────────────────
  function handleTextSelection(): void {
    window.setTimeout(updateSelectionState, 10);
  }

  function updateSelectionState(): void {
    const selection = window.getSelection();
    if (debugState.enabled) {
      console.debug('PDF Selection Update:', selection?.toString().trim());
    }

    if (!selection || selection.rangeCount === 0) {
      clearSelectionUi();
      return;
    }
    const text = selection.toString().trim();
    if (!text) return;

    const containerRect = textLayer?.getBoundingClientRect();

    let nextPosition: { x: number; y: number } | null = null;
    let selectionBounds = { left: 0, top: 0, right: 0, bottom: 0 };
    let overlayRects: SelectionOverlayRect[] = [];
    const unscaledWidth = containerRect ? containerRect.width : 0;

    try {
      const range = selection.getRangeAt(0);
      if (containerRect) overlayRects = buildSelectionOverlayRects(range, containerRect);

      if (overlayRects.length > 0 && containerRect) {
        const left = Math.min(...overlayRects.map((r) => r.left));
        const top = Math.min(...overlayRects.map((r) => r.top));
        const right = Math.max(...overlayRects.map((r) => r.left + r.width));
        const bottom = Math.max(...overlayRects.map((r) => r.top + r.height));

        const selectionCenter = left + (right - left) / 2;
        const anchorX = clampSelectionPoint(
          selectionCenter,
          TOOLBAR_EDGE_PADDING + TOOLBAR_WIDTH_ESTIMATE / 2,
          unscaledWidth - TOOLBAR_EDGE_PADDING - TOOLBAR_WIDTH_ESTIMATE / 2,
        );
        const canPlaceAbove = top > 100;

        selectionPlacement = canPlaceAbove ? 'above' : 'below';
        nextPosition = {
          x: anchorX,
          y: canPlaceAbove ? top - TOOLBAR_OFFSET : bottom + TOOLBAR_OFFSET,
        };
        selectionBounds = { left, top, right, bottom };
      }
    } catch (e) {
      console.error('Selection state update failed:', e);
      overlayRects = [];
    }

    if (!nextPosition && containerRect) {
      selectionPlacement = 'below';
      nextPosition = { x: unscaledWidth / 2, y: 20 };
    }

    if (nextPosition && containerRect) {
      let viewLeft = containerRect.left + selectionBounds.left * scale;
      let viewTop = containerRect.top + selectionBounds.top * scale;
      let viewRight = containerRect.left + selectionBounds.right * scale;
      let viewBottom = containerRect.top + selectionBounds.bottom * scale;

      try {
        const range = selection.getRangeAt(0);
        const rawRects = Array.from(range.getClientRects()).filter(
          (r) => r.width > 0 && r.height > 0,
        );
        if (rawRects.length === 0) {
          const fallbackRect = range.getBoundingClientRect();
          if (fallbackRect.width > 0 && fallbackRect.height > 0) rawRects.push(fallbackRect);
        }
        if (rawRects.length > 0) {
          viewLeft = Math.min(...rawRects.map((r) => r.left));
          viewTop = Math.min(...rawRects.map((r) => r.top));
          viewRight = Math.max(...rawRects.map((r) => r.right));
          viewBottom = Math.max(...rawRects.map((r) => r.bottom));
        }
      } catch (err) {
        console.error('Failed to read raw client rects:', err);
      }

      const normalizedRects = overlayRects.map((r) => ({
        left: r.left / scale,
        top: r.top / scale,
        width: r.width / scale,
        height: r.height / scale,
      }));

      onselection?.({
        text,
        bounds: {
          left: viewLeft - containerRect.left,
          top: viewTop - containerRect.top,
          right: viewRight - containerRect.left,
          bottom: viewBottom - containerRect.top,
        },
        container: {
          left: containerRect.left,
          top: containerRect.top,
          width: containerRect.width,
          height: containerRect.height,
        },
        placement: selectionPlacement,
        rects: normalizedRects,
        pageNumber: currentPage,
      });

      if (debugState.enabled) {
        if (text && overlayRects.length > 0) {
          const r = overlayRects[0];
          debugState.selection = {
            text,
            source: 'pdf',
            rectCount: overlayRects.length,
            firstRect: { top: r.top, left: r.left, width: r.width, height: r.height },
          };
        } else {
          debugState.selection = null;
        }
      }
    } else {
      if (debugState.enabled) debugState.selection = null;
      clearSelectionUi();
    }
  }

  function hideToolbar(): void {
    clearSelectionUi();
    window.getSelection()?.removeAllRanges();
  }

  function dismissHighlightManager(): void {
    activeHighlightId = null;
  }

  function handleHighlightClick(hl: PersistedHighlight, event: MouseEvent): void {
    event.stopPropagation();
    if (activeHighlightId === hl.id) {
      dismissHighlightManager();
      return;
    }
    activeHighlightId = hl.id;
    onHighlightAction?.('open', hl.id, {
      color: hl.color,
      text: hl.text ?? '',
      x: event.clientX,
      y: event.clientY,
    });
  }

  // ── Navigation (exposed via bind:this for Workspace refs) ──
  export const navigateToPage = async (
    targetPage: number,
    options?: { flash?: boolean },
  ): Promise<boolean> => {
    if (!pdfDoc || !isPageWithinBounds(targetPage, totalPages)) return false;
    hideToolbar();
    navigationError = null;
    const navRequestId = ++activeNavigationRequestId;
    cancelTextLayer();
    if (textLayer) textLayer.innerHTML = '';
    try {
      const rendered = await renderPage(targetPage, {
        requestId: navRequestId,
        renderScale: scale,
      });
      if (!rendered || isStaleNavigation(navRequestId)) {
        navigationError = t('pdf.navigationFailed');
        setReaderError(navigationError);
        return false;
      }
      currentPage = targetPage;
      onPageChange?.(currentPage, totalPages);
      emitSessionProgress(currentPage, totalPages);
      if (options?.flash) {
        flashSearchResult = true;
        window.setTimeout(() => {
          flashSearchResult = false;
        }, 900);
      }
      return true;
    } catch {
      navigationError = t('pdf.navigationFailed');
      setReaderError(navigationError);
      return false;
    }
  };
  function goToPrevPage(): void {
    void navigateToPage(currentPage - 1);
  }

  function goToNextPage(): void {
    void navigateToPage(currentPage + 1);
  }

  async function toggleFullscreen(): Promise<void> {
    if (onToggleFullscreen) {
      onToggleFullscreen();
      return;
    }
    if (!canUseFullscreenApi()) {
      navigationError = t('pdf.fullscreenUnsupported');
      return;
    }
    try {
      navigationError = null;
      if (document.fullscreenElement === viewerRoot) {
        await document.exitFullscreen();
      } else {
        await viewerRoot?.requestFullscreen();
      }
    } catch {
      navigationError = t('pdf.fullscreenUnsupported');
    }
  }

  // ── Lazy outline loading ─────────────────────────────────
  $effect(() => {
    if (!showToc || !pdfDoc) return;
    if (outlineDeferred) {
      if (outline.length > 0 || tocLoading) return;
    }
    outlineDeferred = true;
    tocLoading = true;
    tocError = null;
    loadPdfOutline(pdfDoc, filePath)
      .then((items) => {
        outline = items;
        tocError = null;
        outlineDeferred = true;
      })
      .catch(() => {
        outline = [];
        tocError = t('pdf.tocLoadFailed');
        outlineDeferred = false;
      })
      .finally(() => {
        tocLoading = false;
      });
  });

  // ── Search locator navigation ────────────────────────────
  $effect(() => {
    const targetPage = parseLocatorPage(searchTargetLocator);
    if (
      !targetPage ||
      !pdfDoc ||
      totalPages <= 0 ||
      targetPage > totalPages ||
      targetPage === currentPage
    )
      return;
    void navigateToPage(targetPage, { flash: true });
  });

  // ── Wheel zoom ───────────────────────────────────────────
  // Svelte 5's `onwheel={...}` binding registers the listener as passive,
  // which means `event.preventDefault()` is silently ignored and the browser
  // falls back to its native page zoom (Ctrl+wheel enlarges the whole window).
  // We register the listener manually with `{ passive: false }` so that
  // preventDefault() actually cancels the native zoom and only our PDF zoom runs.
  $effect(() => {
    const el = canvasContainer;
    if (!el) return;
    const handler: EventListener = (event) => {
      handleViewerWheel(event as WheelEvent);
    };
    el.addEventListener('wheel', handler, { passive: false });
    return () => el.removeEventListener('wheel', handler);
  });

  function handleViewerWheel(event: WheelEvent): void {
    if (!pdfDoc) return;
    if (!event.ctrlKey && !event.metaKey) return;
    if (event.deltaY === 0) return;
    event.preventDefault();
    pendingWheelDelta += event.deltaY;
    if (pendingWheelFrame !== null) return;
    pendingWheelFrame = window.requestAnimationFrame(() => {
      pendingWheelFrame = null;
      const nextScale = adjustPdfScaleForWheel(scale, pendingWheelDelta);
      pendingWheelDelta = 0;
      if (nextScale !== scale) setScale(nextScale);
    });
  }

  // ── Keyboard navigation ──────────────────────────────────
  function handleViewerKeydown(event: KeyboardEvent): void {
    // Ctrl/Cmd + = / + / − must work even when the viewer div is not focused,
    // otherwise the browser's native zoom fires and enlarges the whole window.
    // Bypass the isViewerFocused gate for these shortcuts only.
    if (
      (event.ctrlKey || event.metaKey) &&
      (event.key === '=' || event.key === '+' || event.key === '-')
    ) {
      event.preventDefault();
      const step = event.key === '-' ? -PDF_SCALE_STEP : PDF_SCALE_STEP;
      void setScale(scale + step);
      return;
    }
    if (!isViewerFocused) return;
    const intent = resolveReaderArrowIntent(event);
    if (!intent) return;
    if (intent === 'prevPage') {
      event.preventDefault();
      goToPrevPage();
      return;
    }
    if (intent === 'nextPage') {
      event.preventDefault();
      goToNextPage();
      return;
    }
    if (intent === 'scrollUp') {
      event.preventDefault();
      scrollByVerticalStep(-VERTICAL_SCROLL_STEP_PX);
      return;
    }
    if (intent === 'scrollDown') {
      event.preventDefault();
      scrollByVerticalStep(VERTICAL_SCROLL_STEP_PX);
    }
  }

  function scrollByVerticalStep(delta: number): void {
    const primaryHost = canvasContainer;
    if (primaryHost && canScrollElementInDirection(primaryHost, delta)) {
      primaryHost.scrollBy({ top: delta, behavior: 'auto' });
      return;
    }
    const fallbackHost = viewerRoot;
    if (fallbackHost && canScrollElementInDirection(fallbackHost, delta)) {
      fallbackHost.scrollBy({ top: delta, behavior: 'auto' });
      return;
    }
    if (typeof window !== 'undefined') window.scrollBy({ top: delta, behavior: 'auto' });
  }

  // ── Scale ────────────────────────────────────────────────
  export async function setScale(newScale: number): Promise<void> {
    const nextScale = clampPdfScale(newScale);
    if (Math.abs(nextScale - scale) <= ZOOM_EPSILON) return;
    scale = nextScale;
    hideToolbar();
    if (!pdfDoc || !canvas || !textLayer || !currentPageObj) return;
    const anchor = captureScrollAnchor(canvasContainer ?? null);
    const navRequestId = ++activeNavigationRequestId;

    try {
      if (textLayerInstance) {
        // ── Zoom quick-path: reuse text layer spans via update() ──
        const viewport = currentPageObj.getViewport({ scale: nextScale });
        const outputScale = window.devicePixelRatio || 1;

        // Update canvas buffer + CSS size
        canvas.width = Math.round(viewport.width * outputScale);
        canvas.height = Math.round(viewport.height * outputScale);
        canvas.style.width = `${viewport.width}px`;
        canvas.style.height = `${viewport.height}px`;

        const context = canvas.getContext('2d');
        if (!context) return;
        context.setTransform(1, 0, 0, 1, 0, 0);
        const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : undefined;
        const renderContext = { canvasContext: context, viewport, canvas, transform };

        await cancelActiveRenderTask();
        const renderTask = currentPageObj.render(renderContext);
        activeRenderTask = renderTask;
        try {
          await renderTask.promise;
        } catch (err) {
          if (activeRenderTask === renderTask) activeRenderTask = null;
          const isCancelled =
            typeof err === 'object' &&
            err !== null &&
            'name' in err &&
            String((err as { name?: string }).name) === 'RenderingCancelledException';
          if (isCancelled) return;
          throw err;
        }
        if (activeRenderTask === renderTask) activeRenderTask = null;
        if (isStaleNavigation(navRequestId)) return;

        // SafeTextLayer.update() re-sets CSS vars and dimensions
        textLayerInstance.update({ viewport });

        // Re-apply pointer-events to repositioned spans
        for (const span of textLayer.querySelectorAll('span')) {
          span.style.pointerEvents = 'auto';
        }

        if (!isStaleNavigation(navRequestId)) {
          restoreScrollAnchor(anchor, canvasContainer ?? null);
        }
      } else {
        // Fallback: full re-render (first render or after page change)
        const rendered = await renderPage(currentPage, {
          requestId: navRequestId,
          renderScale: nextScale,
        });
        if (rendered && !isStaleNavigation(navRequestId)) {
          restoreScrollAnchor(anchor, canvasContainer ?? null);
        }
      }
    } catch (err) {
      console.error('Error setting scale:', err);
      navigationError = t('pdf.navigationFailed');
    }
  }

  export function getCurrentPage(): number {
    return currentPage;
  }
  export function getCurrentFilePath(): string {
    return filePath;
  }

  const handleViewerKeydown_ = (event: KeyboardEvent): void => {
    if (event.key === 'ArrowLeft') goToPrevPage();
    else if (event.key === 'ArrowRight') goToNextPage();
  };
</script>

<svelte:window onkeydown={handleViewerKeydown} />

<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
<div
  class="pdf-viewer h-full flex flex-col min-h-0"
  bind:this={viewerRoot}
  tabindex="0"
  role="region"
  aria-label="PDF Viewer"
  onfocus={() => {
    isViewerFocused = true;
  }}
  onblur={() => {
    isViewerFocused = false;
  }}
  onkeydown={handleViewerKeydown_}
  onclick={(event) => {
    dismissHighlightManager();
    const target = event.target;
    if (
      target instanceof HTMLSelectElement ||
      target instanceof HTMLInputElement ||
      target instanceof HTMLButtonElement ||
      target instanceof HTMLTextAreaElement
    )
      return;
    if (textLayer && target instanceof Node && textLayer.contains(target)) {
      handleTextSelection();
      return;
    }
    viewerRoot?.focus();
  }}
  onmouseup={handleTextSelection}
  onpointerup={handleTextSelection}
  ontouchend={handleTextSelection}
  style={`--pdf-reader-root-bg: ${readerThemePalette.rootBackground}; --pdf-reader-surface-bg: ${readerThemePalette.surfaceBackground}; --pdf-reader-text: ${readerThemePalette.textColor};`}
>
  <PdfLoadingOverlay {isLoading} {error} {loadProgress} {loadProgressMax} {t} />

  <!-- The navigation controls (ReaderControls) auto-hide in fullscreen and
       float over the content, revealing on hover at the top of the viewport. -->
  <PdfControls
    {currentPage}
    {totalPages}
    {scale}
    {isFullscreen}
    {showToc}
    {isLoading}
    {error}
    {t}
    onPrevPage={goToPrevPage}
    onNextPage={goToNextPage}
    onGoToPage={navigateToPage}
    onSetScale={(s) => setScale(s)}
    onToggleFullscreen={toggleFullscreen}
    onToggleToc={() => {
      showToc = !showToc;
    }}
  />

  {#if navigationError}
    <p class="m-0 px-3 pt-2 text-red-600 text-xs" role="status" aria-live="polite">
      {navigationError}
    </p>
  {/if}

  <div
    class="flex flex-1 overflow-hidden"
    style:visibility={isLoading || error ? 'hidden' : 'visible'}
  >
    {#if showToc}
      <PdfTocSidebar
        {flatOutline}
        {tocLoading}
        {tocError}
        {t}
        onNavigate={(item) => navigateToOutlineItem(item)}
      />
    {/if}
    <div
      class="flex-1 min-h-0 overflow-auto bg-(--pdf-reader-root-bg,var(--color-background))"
      bind:this={canvasContainer}
      style="padding: {canvasContainerPaddingStyle};"
    >
      <div class="flex min-h-full items-center justify-center">
        <div
          class="relative inline-block"
          class:search-hit={flashSearchResult}
          style="isolation: isolate; {canvasWrapperStyle}"
        >
          <canvas bind:this={canvas} style="filter: {visualFilterStyle};"></canvas>

          <PdfSelectionOverlay
            {persistedHighlights}
            {currentPage}
            {scale}
            {activeHighlightId}
            onHighlightClick={handleHighlightClick}
          />

          <div
            bind:this={textLayer}
            class="textLayer"
            draggable="false"
            role="presentation"
            ondragstart={(e) => e.preventDefault()}
          ></div>
        </div>
      </div>
    </div>
  </div>
</div>

<style>
  canvas {
    display: block;
    position: relative;
    z-index: 0;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    background: var(--pdf-reader-surface-bg, #fff);
  }

  .search-hit {
    outline: 3px solid var(--color-accent-blue);
    outline-offset: 6px;
    border-radius: 4px;
  }
</style>
