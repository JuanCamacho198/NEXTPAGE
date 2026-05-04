<script lang="ts">
  import * as pdfjsLib from "pdfjs-dist";
  import { onMount } from "svelte";
  import { getFileBytes } from "$lib/api/tauriClient";
  import HighlightToolbar from "./HighlightToolbar.svelte";
  import ErrorBoundary from "$lib/components/ui/feedback/ErrorBoundary.svelte";
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import type { MessageKey } from "$lib/i18n";
  import type { PdfOutlineItem, ReaderSettings, ReaderThemeMode } from "$lib/types";
  import {
    adjustPdfScaleForWheel,
    clampPdfScale,
    DEFAULT_PDF_SCALE,
    isPageWithinBounds,
    PDF_SCALE_STEP,
  } from "./pdf/pdfNavigation";
  import { resolveReaderArrowIntent } from "./epub/keyboardNav";
  import {
    TOOLBAR_OFFSET,
    TOOLBAR_WIDTH_ESTIMATE,
    TOOLBAR_EDGE_PADDING,
    VERTICAL_SCROLL_STEP_PX,
    ZOOM_COMMIT_DELAY_MS,
    ZOOM_EPSILON,
    SELECTION_X_PADDING_PX,
    SELECTION_Y_INSET_PX,
    SELECTION_LINE_TOLERANCE_PX,
    resolveThemePalette,
    clamp,
    clampSelectionPoint,
    scaleOptions,
    type SelectionOverlayRect,
  } from "./pdfState.svelte";

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
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  const DEFAULT_READER_SETTINGS: ReaderSettings = {
    themeMode: "paper",
    brightness: 100,
    contrast: 100,
    epub: {
      fontSize: 100,
      fontFamily: "serif",
    },
    selectionColor: "#33bbff",
  };

  let {
    filePath,
    bookId,
    initialPage = 1,
    onPageChange,
    searchTargetLocator = null,
    onSessionProgress,
    readerSettings = DEFAULT_READER_SETTINGS,
    t,
  }: Props = $props();

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
  let error = $state<string | null>(null);
  let navigationError = $state<string | null>(null);
  let showToc = $state(false);
  let outline = $state<PdfOutlineItem[]>([]);
  let tocLoading = $state(false);
  let tocError = $state<string | null>(null);
  let isFullscreen = $state(false);
  let fullscreenSupported = $state(true);
  let isViewerFocused = $state(false);

  let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null;
  let currentPageObj: pdfjsLib.PDFPageProxy | null = null;

  let selectedText = $state("");
  let selectedCfi = $state<string | null>(null);
  let selectionHasAnchor = $state(false);
  let selectionPosition = $state<{ x: number; y: number } | null>(null);
  let selectionPlacement = $state<"above" | "below">("above");
  let lastSelectionBounds = $state({ left: 0, top: 0, right: 0, bottom: 0 });
  let selectionOverlayRects = $state<Array<{ left: number; top: number; width: number; height: number }>>([]);
  let showToolbar = $state(false);

  let activeLoadRequestId = 0;
  let activeNavigationRequestId = 0;
  let activeZoomRequestId = 0;
  let activeLoadingTask: pdfjsLib.PDFDocumentLoadingTask | null = null;
  let activeRenderTask: pdfjsLib.RenderTask | null = null;
  let activeTextLayerTask: { cancel?: () => void; promise?: Promise<void> } | null = null;
  let pendingZoomCommitTimer: number | null = null;
  let pendingWheelFrame: number | null = null;
  let pendingWheelDelta = 0;
  let textLayerMouseupTarget: HTMLDivElement | null = null;
  let lastLoadedFilePath: string | null = null;
  let committedScale = $state(DEFAULT_PDF_SCALE);
  const outlinePageCache = new Map<string, number>();

  type RefLike = { num: number; gen: number };
  type PdfRefProxy = Parameters<pdfjsLib.PDFDocumentProxy["getPageIndex"]>[0];

  type FlatOutlineItem = {
    item: PdfOutlineItem;
    depth: number;
  };

  const isStaleLoad = (requestId: number) => requestId !== activeLoadRequestId;

  const isStaleNavigation = (requestId: number) => requestId !== activeNavigationRequestId;

  const readerThemePalette = $derived(resolveThemePalette(readerSettings.themeMode));
  const visualFilterStyle = $derived(
    `brightness(${clamp(readerSettings.brightness, 50, 150)}%) contrast(${clamp(readerSettings.contrast, 50, 150)}%)`,
  );
  const zoomPreviewRatio = $derived.by(() => {
    if (committedScale <= 0) {
      return 1;
    }

    return scale / committedScale;
  });
  const canvasWrapperStyle = $derived.by(() => {
    const styles = [`filter: ${visualFilterStyle};`];
    if (Math.abs(zoomPreviewRatio - 1) > ZOOM_EPSILON) {
      styles.push(`transform: scale(${zoomPreviewRatio});`);
      styles.push("transform-origin: top center;");
      styles.push("will-change: transform;");
    }

    return styles.join(" ");
  });

  const canUseFullscreenApi = () => {
    if (typeof document === "undefined") {
      return false;
    }

    return (
      typeof viewerRoot?.requestFullscreen === "function" &&
      typeof document.exitFullscreen === "function"
    );
  };

  const clearTextLayerListener = () => {
    if (!textLayerMouseupTarget) {
      return;
    }

    textLayerMouseupTarget.removeEventListener("mouseup", handleTextSelection);
    textLayerMouseupTarget.removeEventListener("touchend", handleTextSelection);
    textLayerMouseupTarget.removeEventListener("pointerup", handleTextSelection);
    textLayerMouseupTarget = null;
  };

  const clearSelectionUi = () => {
    showToolbar = false;
    selectedText = "";
    selectedCfi = null;
    selectionHasAnchor = false;
    selectionPosition = null;
    selectionOverlayRects = [];
  };

  const buildSelectionOverlayRects = (range: Range, containerRect: DOMRect) => {
    const rawRects = Array.from(range.getClientRects()).filter((rect) => rect.width > 0 && rect.height > 0);
    if (rawRects.length === 0) {
      const fallbackRect = range.getBoundingClientRect();
      if (fallbackRect.width <= 0 || fallbackRect.height <= 0) {
        return [] as SelectionOverlayRect[];
      }

      rawRects.push(fallbackRect);
    }

    const normalizedRects = rawRects
      .map((rect) => {
        const left = clampSelectionPoint(rect.left - containerRect.left, 0, containerRect.width);
        const right = clampSelectionPoint(rect.right - containerRect.left, 0, containerRect.width);
        const top = clampSelectionPoint(rect.top - containerRect.top, 0, containerRect.height);
        const bottom = clampSelectionPoint(rect.bottom - containerRect.top, 0, containerRect.height);
        return { left, right, top, bottom };
      })
      .filter((rect) => rect.right - rect.left > 0 && rect.bottom - rect.top > 0)
      .sort((left, right) => {
        if (Math.abs(left.top - right.top) <= SELECTION_LINE_TOLERANCE_PX) {
          return left.left - right.left;
        }

        return left.top - right.top;
      });

    const mergedLines: Array<{ left: number; right: number; top: number; bottom: number }> = [];

    normalizedRects.forEach((rect) => {
      const previous = mergedLines[mergedLines.length - 1];
      if (!previous) {
        mergedLines.push({ ...rect });
        return;
      }

      const sameLine =
        Math.abs(rect.top - previous.top) <= SELECTION_LINE_TOLERANCE_PX &&
        Math.abs(rect.bottom - previous.bottom) <= Math.max(SELECTION_LINE_TOLERANCE_PX, rect.bottom - rect.top);

      if (!sameLine) {
        mergedLines.push({ ...rect });
        return;
      }

      previous.left = Math.min(previous.left, rect.left);
      previous.right = Math.max(previous.right, rect.right);
      previous.top = Math.min(previous.top, rect.top);
      previous.bottom = Math.max(previous.bottom, rect.bottom);
    });

    return mergedLines.map((rect) => {
      const left = clampSelectionPoint(rect.left - SELECTION_X_PADDING_PX, 0, containerRect.width);
      const right = clampSelectionPoint(rect.right + SELECTION_X_PADDING_PX, 0, containerRect.width);
      const top = clampSelectionPoint(rect.top + SELECTION_Y_INSET_PX, 0, containerRect.height);
      const bottom = clampSelectionPoint(rect.bottom - SELECTION_Y_INSET_PX, top, containerRect.height);

      return {
        left,
        top,
        width: Math.max(1, right - left),
        height: Math.max(1, bottom - top),
      };
    });
  };

  const clearPendingZoomCommit = () => {
    if (pendingZoomCommitTimer === null) {
      return;
    }

    window.clearTimeout(pendingZoomCommitTimer);
    pendingZoomCommitTimer = null;
  };

  const cancelActiveRenderTask = async () => {
    if (!activeRenderTask) {
      return;
    }

    const task = activeRenderTask;
    activeRenderTask = null;

    task.cancel();
    try {
      await task.promise;
    } catch {
      // render cancellation is expected during document/page switches
    }
  };

  const cancelActiveTextLayerTask = async () => {
    if (!activeTextLayerTask) {
      return;
    }

    const task = activeTextLayerTask;
    activeTextLayerTask = null;

    try {
      task.cancel?.();
      await task.promise;
    } catch {
      // text-layer cancellation is expected during navigation/zoom
    }
  };

  const destroyActiveLoadingTask = () => {
    if (!activeLoadingTask) {
      return;
    }

    activeLoadingTask.destroy();
    activeLoadingTask = null;
  };

  const destroyCurrentDocument = async () => {
    if (!pdfDoc) {
      return;
    }

    const current = pdfDoc;
    pdfDoc = null;
    currentPageObj = null;

    try {
      await current.destroy();
    } catch {
      // swallow teardown errors to keep viewer recovery path stable
    }
  };

  onMount(() => {
    pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
      "pdfjs-dist/build/pdf.worker.min.mjs",
      import.meta.url
    ).toString();

    const syncFullscreenState = () => {
      isFullscreen = document.fullscreenElement === viewerRoot;
    };

    const handleFullscreenError = () => {
      navigationError = t("pdf.fullscreenUnsupported");
      fullscreenSupported = canUseFullscreenApi();
      isFullscreen = document.fullscreenElement === viewerRoot;
    };

    const handleSelectionChange = () => {
      const selection = window.getSelection();
      const text = selection?.toString().trim();

      if (!text) {
        clearSelectionUi();
      }
    };

    document.addEventListener("fullscreenchange", syncFullscreenState);
    document.addEventListener("fullscreenerror", handleFullscreenError);
    document.addEventListener("selectionchange", handleSelectionChange);
    fullscreenSupported = canUseFullscreenApi();

    return () => {
      activeLoadRequestId += 1;
      activeNavigationRequestId += 1;
      activeZoomRequestId += 1;
      clearPendingZoomCommit();
      clearTextLayerListener();
      if (pendingWheelFrame !== null) {
        window.cancelAnimationFrame(pendingWheelFrame);
        pendingWheelFrame = null;
      }
      pendingWheelDelta = 0;
      destroyActiveLoadingTask();
      void cancelActiveRenderTask();
      void cancelActiveTextLayerTask();
      void destroyCurrentDocument();
      document.removeEventListener("fullscreenchange", syncFullscreenState);
      document.removeEventListener("fullscreenerror", handleFullscreenError);
      document.removeEventListener("selectionchange", handleSelectionChange);
    };
  });

  $effect(() => {
    viewerRoot;
    fullscreenSupported = canUseFullscreenApi();
  });

  $effect(() => {
    if (filePath && filePath !== lastLoadedFilePath) {
      lastLoadedFilePath = filePath;
      loadPdf();
    }
  });

  const readProgressPercent = (page: number, total: number) => {
    if (total <= 0) {
      return 0;
    }

    return Math.max(0, Math.min(100, (page / total) * 100));
  };

  const emitSessionProgress = (nextPage: number, nextTotal: number) => {
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

  const parseLocatorPage = (locator: string | null | undefined): number | null => {
    if (!locator) {
      return null;
    }

    const match = locator.match(/(\d+)$/);
    if (!match) {
      return null;
    }

    const parsed = Number.parseInt(match[1], 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return null;
    }

    return parsed;
  };

  const isRefLike = (value: unknown): value is RefLike => {
    if (!value || typeof value !== "object") {
      return false;
    }

    const candidate = value as { num?: unknown; gen?: unknown };
    return typeof candidate.num === "number" && typeof candidate.gen === "number";
  };

  const toOutlineTitle = (title: unknown) => {
    if (typeof title !== "string") {
      return t("pdf.untitledSection");
    }

    const normalized = title.trim();
    return normalized.length > 0 ? normalized : t("pdf.untitledSection");
  };

  const normalizeOutlineItems = (items: unknown[], parentId = "outline"): PdfOutlineItem[] => {
    const normalized: PdfOutlineItem[] = [];

    items.forEach((rawItem, index) => {
      if (!rawItem || typeof rawItem !== "object") {
        return;
      }

      const item = rawItem as {
        title?: unknown;
        dest?: unknown;
        items?: unknown;
      };
      const children = Array.isArray(item.items)
        ? normalizeOutlineItems(item.items, `${parentId}-${index}`)
        : [];
      const destination =
        typeof item.dest === "string" || Array.isArray(item.dest) ? item.dest : null;

      normalized.push({
        id: `${parentId}-${index}`,
        title: toOutlineTitle(item.title),
        dest: destination,
        items: children,
      });
    });

    return normalized;
  };

  const flattenOutline = (items: PdfOutlineItem[], depth = 0): FlatOutlineItem[] => {
    const flattened: FlatOutlineItem[] = [];

    for (const item of items) {
      flattened.push({ item, depth });
      if (item.items.length > 0) {
        flattened.push(...flattenOutline(item.items, depth + 1));
      }
    }

    return flattened;
  };

  const flatOutline = $derived(flattenOutline(outline));

  const loadOutline = async (doc: pdfjsLib.PDFDocumentProxy, requestId: number) => {
    tocLoading = true;
    tocError = null;
    outline = [];
    outlinePageCache.clear();

    try {
      const rawOutline = await doc.getOutline();
      if (isStaleLoad(requestId)) {
        return;
      }

      outline = Array.isArray(rawOutline) ? normalizeOutlineItems(rawOutline) : [];
      tocError = null;
    } catch {
      if (isStaleLoad(requestId)) {
        return;
      }

      outline = [];
      tocError = t("pdf.tocLoadFailed");
    } finally {
      if (!isStaleLoad(requestId)) {
        tocLoading = false;
      }
    }
  };

  const resolveDestinationPage = async (dest: string | unknown[] | null): Promise<number | null> => {
    if (!pdfDoc || !dest || totalPages <= 0) {
      return null;
    }

    try {
      const resolvedDest = typeof dest === "string" ? await pdfDoc.getDestination(dest) : dest;
      if (!Array.isArray(resolvedDest) || resolvedDest.length === 0) {
        return null;
      }

      const target = resolvedDest[0];
      if (typeof target === "number" && Number.isFinite(target)) {
        return isPageWithinBounds(target + 1, totalPages) ? target + 1 : null;
      }

      if (!isRefLike(target)) {
        return null;
      }

      const cacheKey = `${target.num}:${target.gen}`;
      const cachedPage = outlinePageCache.get(cacheKey);
      if (cachedPage && isPageWithinBounds(cachedPage, totalPages)) {
        return cachedPage;
      }

      const pageIndex = await pdfDoc.getPageIndex(target as PdfRefProxy);
      const pageNumber = pageIndex + 1;
      if (!isPageWithinBounds(pageNumber, totalPages)) {
        return null;
      }

      outlinePageCache.set(cacheKey, pageNumber);
      return pageNumber;
    } catch {
      return null;
    }
  };

  async function navigateToOutlineItem(item: PdfOutlineItem) {
    if (!item.dest) {
      return;
    }

    navigationError = null;
    const page = await resolveDestinationPage(item.dest);
    if (!page) {
      navigationError = t("pdf.tocNavigationFailed");
      return;
    }

    const didNavigate = await navigateToPage(page);
    if (!didNavigate) {
      navigationError = t("pdf.tocNavigationFailed");
      return;
    }

    if (typeof window !== "undefined" && window.matchMedia("(max-width: 900px)").matches) {
      showToc = false;
    }
  }

  async function loadPdf() {
    if (!filePath) return;

    const requestId = ++activeLoadRequestId;
    activeNavigationRequestId += 1;
    clearTextLayerListener();
    destroyActiveLoadingTask();
    await cancelActiveRenderTask();
    await cancelActiveTextLayerTask();
    await destroyCurrentDocument();

    isLoading = true;
    error = null;
    navigationError = null;
    clearPendingZoomCommit();
    activeZoomRequestId += 1;
    scale = DEFAULT_PDF_SCALE;
    committedScale = DEFAULT_PDF_SCALE;
    showToc = false;
    outline = [];
    tocLoading = false;
    tocError = null;
    outlinePageCache.clear();

    try {
      const fileData = await getFileBytes(filePath);
      if (isStaleLoad(requestId)) {
        return;
      }

      const loadingTask = pdfjsLib.getDocument({
        data: new Uint8Array(fileData),
      });
      activeLoadingTask = loadingTask;
      const loadedDoc = await loadingTask.promise;

      if (isStaleLoad(requestId)) {
        await loadedDoc.destroy();
        return;
      }

      pdfDoc = loadedDoc;
      totalPages = loadedDoc.numPages;
      await loadOutline(loadedDoc, requestId);
      if (isStaleLoad(requestId)) {
        return;
      }
      const requestedPage = Math.max(1, initialPage || 1);
      const targetPage = Math.min(requestedPage, totalPages);

      const rendered = await renderPage(targetPage, {
        requestId,
        renderScale: scale,
      });
      if (!rendered || isStaleLoad(requestId)) {
        return;
      }

      currentPage = targetPage;
      onPageChange?.(targetPage, totalPages);
      lastPercent = readProgressPercent(targetPage, totalPages);
      sessionStartAt = new Date().toISOString();
      error = null;
    } catch (err) {
      if (isStaleLoad(requestId)) {
        return;
      }

      error = err instanceof Error ? err.message : "Failed to load PDF";
    } finally {
      // Always clear loading state when done
      isLoading = false;
      activeLoadingTask = null;
    }
  }

  async function renderPage(
    pageNum: number,
    options: { requestId?: number; renderScale?: number } = {},
  ) {
    if (!pdfDoc || !canvas || !textLayer) return;

    const requestId = options.requestId ?? activeLoadRequestId;
    const renderScale = options.renderScale ?? scale;

    const page = await pdfDoc.getPage(pageNum);
    if (isStaleLoad(requestId)) {
      return false;
    }

    currentPageObj = page;
    const viewport = page.getViewport({ scale: renderScale });

    canvas.height = viewport.height;
    canvas.width = viewport.width;

    const context = canvas.getContext("2d");
    if (!context) return;

    const renderContext = {
      canvasContext: context,
      viewport,
      canvas,
    };

    await cancelActiveRenderTask();
    const renderTask = page.render(renderContext);
    activeRenderTask = renderTask;

    try {
      await renderTask.promise;
    } catch (err) {
      if (activeRenderTask === renderTask) {
        activeRenderTask = null;
      }

      const isCancelled =
        typeof err === "object" &&
        err !== null &&
        "name" in err &&
        String((err as { name?: string }).name) === "RenderingCancelledException";
      if (isCancelled) {
        return false;
      }

      throw err;
    }

    if (activeRenderTask === renderTask) {
      activeRenderTask = null;
    }

    if (isStaleLoad(requestId)) {
      return false;
    }

    textLayer.style.width = `${viewport.width}px`;
    textLayer.style.height = `${viewport.height}px`;

    const textContent = await page.getTextContent();
    if (isStaleLoad(requestId)) {
      return false;
    }

    await renderTextLayer(
      textContent as { items: Array<{ str: string; transform: number[]; width: number; height: number }> },
      viewport,
      requestId,
    );
    return true;
  }

  async function renderTextLayer(
    textContent: { items: Array<{ str: string; transform: number[]; width: number; height: number }> },
    viewport: pdfjsLib.PageViewport,
    requestId = activeLoadRequestId,
  ) {
    if (!textLayer) return;
    if (isStaleLoad(requestId)) {
      return;
    }

    await cancelActiveTextLayerTask();
    clearTextLayerListener();

    textLayer.innerHTML = "";
    textLayer.style.position = "absolute";
    textLayer.style.left = "0";
    textLayer.style.top = "0";
    textLayer.style.pointerEvents = "auto";

    try {
      const pdfLibWithTextLayer = pdfjsLib as any;
      if (pdfjsLib.TextLayer) {
        const textLayerInstance = new (pdfjsLib as any).TextLayer({
          container: textLayer,
          viewport,
          textContentSource: textContent as any,
        });
        await textLayerInstance.render();
      } else {
        const task = pdfLibWithTextLayer.renderTextLayer?.({
          container: textLayer,
          viewport,
          textDivs: [],
          enhanceTextSelection: true,
          textContentSource: textContent as any,
        });
        if (task?.promise) await task.promise;
      }
    } catch (err) {
      console.error("Text layer render error:", err);
    }

    // Events are now handled at the viewerRoot level for better reliability
    textLayerMouseupTarget = textLayer;
  }

  function handleTextSelection() {
    // Small delay to let the browser settle the selection
    window.setTimeout(updateSelectionState, 10);
  }

  function updateSelectionState() {
    const selection = window.getSelection();
    console.log("PDF Selection Update:", selection?.toString().trim());
    
    if (!selection || selection.rangeCount === 0) {
      clearSelectionUi();
      return;
    }

    const text = selection.toString().trim();
    if (!text) {
      // Don't clear immediately to allow clicking toolbar buttons
      // clearSelectionUi();
      return;
    }

    selectedText = text;
    const containerRect = textLayer?.getBoundingClientRect();

    selectedCfi = null;
    let hasAnchor = false;
    let nextPosition: { x: number; y: number } | null = null;

    // Store selection bounds for highlight persistence
    let selectionBounds = { left: 0, top: 0, right: 0, bottom: 0 };
    let overlayRects: SelectionOverlayRect[] = [];

    try {
      const range = selection.getRangeAt(0);
      if (containerRect) {
        overlayRects = buildSelectionOverlayRects(range, containerRect);
      }

      if (overlayRects.length > 0 && containerRect) {
        const left = Math.min(...overlayRects.map((rect) => rect.left));
        const top = Math.min(...overlayRects.map((rect) => rect.top));
        const right = Math.max(...overlayRects.map((rect) => rect.left + rect.width));
        const bottom = Math.max(...overlayRects.map((rect) => rect.top + rect.height));
        
        const selectionCenter = left + (right - left) / 2;
        const anchorX = clampSelectionPoint(
          selectionCenter,
          TOOLBAR_EDGE_PADDING + TOOLBAR_WIDTH_ESTIMATE / 2,
          containerRect.width - TOOLBAR_EDGE_PADDING - TOOLBAR_WIDTH_ESTIMATE / 2,
        );
        
        const canPlaceAbove = top > 100; // Increased threshold for safety

        hasAnchor = true;
        selectionPlacement = canPlaceAbove ? "above" : "below";
        nextPosition = {
          x: anchorX,
          y: canPlaceAbove
            ? top - TOOLBAR_OFFSET
            : bottom + TOOLBAR_OFFSET,
        };
        selectionBounds = {
          left,
          top,
          right,
          bottom,
        };
      }
    } catch (e) {
      console.error("Selection state update failed:", e);
      hasAnchor = false;
      overlayRects = [];
    }

    // Store selection bounds
    lastSelectionBounds = selectionBounds;
    selectionOverlayRects = overlayRects;

    if (!nextPosition && containerRect) {
      selectionPlacement = "below";
      nextPosition = {
        x: containerRect.width / 2,
        y: 20,
      };
    }

    if (nextPosition) {
      selectionPosition = {
        x: nextPosition.x,
        y: nextPosition.y,
      };
      selectionHasAnchor = hasAnchor;
      showToolbar = true;
    } else {
      clearSelectionUi();
    }
  }

  function hideToolbar() {
    clearSelectionUi();
    window.getSelection()?.removeAllRanges();
  }

  const navigateToPage = async (targetPage: number, options?: { flash?: boolean }) => {
    if (!pdfDoc || !isPageWithinBounds(targetPage, totalPages)) {
      return false;
    }

    hideToolbar();
    navigationError = null;

    try {
      clearPendingZoomCommit();
      const rendered = await renderPage(targetPage, {
        renderScale: scale,
      });
      if (!rendered) {
        navigationError = t("pdf.navigationFailed");
        return false;
      }

      currentPage = targetPage;
      committedScale = scale;
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
      navigationError = t("pdf.navigationFailed");
      return false;
    }
  };

  function goToPrevPage() {
    if (currentPage <= 1 || !pdfDoc) return;
    navigateToPage(currentPage - 1);
  }

  function goToNextPage() {
    if (currentPage >= totalPages || !pdfDoc) return;
    navigateToPage(currentPage + 1);
  }

  async function goToPage(event: Event) {
    const target = event.target as HTMLInputElement;
    const page = Number.parseInt(target.value, 10);
    if (!isPageWithinBounds(page, totalPages)) {
      target.value = String(currentPage);
      return;
    }

    const didCommit = await navigateToPage(page);
    if (!didCommit) {
      target.value = String(currentPage);
    }
  }

  async function toggleFullscreen() {
    if (!canUseFullscreenApi()) {
      fullscreenSupported = false;
      navigationError = t("pdf.fullscreenUnsupported");
      return;
    }

    try {
      navigationError = null;
      if (document.fullscreenElement === viewerRoot) {
        await document.exitFullscreen();
      } else {
        await viewerRoot?.requestFullscreen();
      }
      isFullscreen = document.fullscreenElement === viewerRoot;
    } catch {
      navigationError = t("pdf.fullscreenUnsupported");
      fullscreenSupported = false;
    }
  }

  $effect(() => {
    const targetPage = parseLocatorPage(searchTargetLocator);
    if (!targetPage || !pdfDoc || totalPages <= 0 || targetPage > totalPages || targetPage === currentPage) {
      return;
    }

    void navigateToPage(targetPage, { flash: true });
  });

  function handleViewerWheel(event: WheelEvent) {
    if (!pdfDoc) {
      return;
    }

    if (!event.ctrlKey && !event.metaKey) {
      return;
    }

    if (event.deltaY === 0) {
      return;
    }

    event.preventDefault();
    pendingWheelDelta += event.deltaY;

    if (pendingWheelFrame !== null) {
      return;
    }

    pendingWheelFrame = window.requestAnimationFrame(() => {
      pendingWheelFrame = null;
      const nextScale = adjustPdfScaleForWheel(scale, pendingWheelDelta);
      pendingWheelDelta = 0;

      if (nextScale !== scale) {
        setScale(nextScale);
      }
    });
  }

  function captureScrollAnchor() {
    const host = canvasContainer;
    if (!host) {
      return null;
    }

    return {
      host,
      previousScrollTop: host.scrollTop,
      previousHeight: host.scrollHeight,
      viewportHeight: host.clientHeight,
    };
  }

  function restoreScrollAnchor(
    anchor: {
      host: HTMLDivElement;
      previousScrollTop: number;
      previousHeight: number;
      viewportHeight: number;
    } | null,
  ) {
    if (!anchor) {
      return;
    }

    const { host, previousScrollTop, previousHeight, viewportHeight } = anchor;
    const previousCenter = previousScrollTop + viewportHeight / 2;
    const nextHeight = host.scrollHeight;
    if (previousHeight <= 0 || nextHeight <= 0) {
      return;
    }

    const centerRatio = previousCenter / previousHeight;
    const nextCenter = centerRatio * nextHeight;
    const nextScrollTop = Math.max(0, nextCenter - host.clientHeight / 2);
    host.scrollTop = nextScrollTop;
  }

  function queueZoomCommit() {
    if (!pdfDoc) {
      return;
    }

    clearPendingZoomCommit();
    const requestId = ++activeZoomRequestId;
    const targetScale = scale;

    pendingZoomCommitTimer = window.setTimeout(async () => {
      pendingZoomCommitTimer = null;
      if (requestId !== activeZoomRequestId || !pdfDoc) {
        return;
      }

      const anchor = captureScrollAnchor();
      try {
        const rendered = await renderPage(currentPage, {
          renderScale: targetScale,
        });
        if (!rendered || requestId !== activeZoomRequestId) {
          return;
        }

        committedScale = targetScale;
        restoreScrollAnchor(anchor);
      } catch {
        navigationError = t("pdf.navigationFailed");
      }
    }, ZOOM_COMMIT_DELAY_MS);
  }

  function handleViewerKeydown(event: KeyboardEvent) {
    if (!isViewerFocused) {
      return;
    }

    if ((event.ctrlKey || event.metaKey) && (event.key === "=" || event.key === "+" || event.key === "-")) {
      event.preventDefault();
      const step = event.key === "-" ? -PDF_SCALE_STEP : PDF_SCALE_STEP;
      setScale(scale + step);
      return;
    }

    const intent = resolveReaderArrowIntent(event);
    if (!intent) {
      return;
    }

    if (intent === "prevPage") {
      event.preventDefault();
      goToPrevPage();
      return;
    }

    if (intent === "nextPage") {
      event.preventDefault();
      goToNextPage();
      return;
    }

    if (intent === "scrollUp") {
      event.preventDefault();
      scrollByVerticalStep(-VERTICAL_SCROLL_STEP_PX);
      return;
    }

    if (intent === "scrollDown") {
      event.preventDefault();
      scrollByVerticalStep(VERTICAL_SCROLL_STEP_PX);
    }
  }

  function canScrollElementInDirection(element: HTMLElement, delta: number) {
    if (element.scrollHeight <= element.clientHeight + 1) {
      return false;
    }

    if (delta < 0) {
      return element.scrollTop > 0;
    }

    return element.scrollTop + element.clientHeight < element.scrollHeight - 1;
  }

  function scrollByVerticalStep(delta: number) {
    const primaryHost = canvasContainer;
    if (primaryHost && canScrollElementInDirection(primaryHost, delta)) {
      primaryHost.scrollBy({ top: delta, behavior: "auto" });
      return;
    }

    const fallbackHost = viewerRoot;
    if (fallbackHost && canScrollElementInDirection(fallbackHost, delta)) {
      fallbackHost.scrollBy({ top: delta, behavior: "auto" });
      return;
    }

    if (typeof window !== "undefined") {
      window.scrollBy({ top: delta, behavior: "auto" });
    }
  }

  export function setScale(newScale: number) {
    const nextScale = clampPdfScale(newScale);
    if (Math.abs(nextScale - scale) <= ZOOM_EPSILON) {
      return;
    }

    scale = nextScale;
    queueZoomCommit();
  }

  $effect(() => {
    if (!pdfDoc) {
      committedScale = scale;
    }
  });

  export function getCurrentPage() {
    return currentPage;
  }

  export function getCurrentFilePath() {
    return filePath;
  }

  const handleViewerKeydown_ = (event: KeyboardEvent) => {
    if (event.key === "ArrowLeft") {
      goToPrevPage();
    } else if (event.key === "ArrowRight") {
      goToNextPage();
    }
  };
</script>

<svelte:window onkeydown={handleViewerKeydown} />

<ErrorBoundary>
  <div
    class="flex flex-col h-full relative outline-none"
    style="background: var(--pdf-reader-root-bg, var(--color-background)); color: var(--pdf-reader-text, var(--color-primary)); user-select: text !important; -webkit-user-select: text !important;"
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
      if (textLayer && event.target instanceof Node && textLayer.contains(event.target)) {
        handleTextSelection();
        return;
      }
      viewerRoot?.focus();
    }}
    onmouseup={handleTextSelection}
    onpointerup={handleTextSelection}
    ontouchend={handleTextSelection}
    style:--pdf-reader-root-bg={readerThemePalette.rootBackground} style:--pdf-reader-surface-bg={readerThemePalette.surfaceBackground} style:--pdf-reader-text={readerThemePalette.textColor} style:--pdf-selection-color={readerSettings.selectionColor}
  >
    {#if isLoading}
      <div class="absolute inset-0 flex items-center justify-center text-sm z-10 bg-[var(--color-background)]">{t("pdf.loading")}</div>
    {/if}
    {#if error}
      <div class="absolute inset-0 flex items-center justify-center text-sm z-10 bg-[var(--color-background)] text-red-600">{t("pdf.error")}: {error}</div>
    {/if}
    <div class="flex flex-wrap items-center gap-3 p-2" style="background: var(--pdf-reader-surface-bg, var(--color-surface)); border-bottom: 1px solid var(--color-border); visibility: {isLoading || error ? 'hidden' : 'visible'}">
      <button type="button" class="px-3 py-1.5 border border-[var(--color-border)] rounded bg-[var(--pdf-reader-surface-bg,var(--color-surface))] text-[var(--pdf-reader-text,var(--color-primary))] cursor-pointer text-[13px] hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed" onclick={() => (showToc = !showToc)}>
        {t("pdf.contents")}
      </button>
      <button type="button" class="inline-flex items-center gap-1.5 px-3 py-1.5 border border-[var(--color-border)] rounded bg-[var(--pdf-reader-surface-bg,var(--color-surface))] text-[var(--pdf-reader-text,var(--color-primary))] cursor-pointer text-[13px] hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed" aria-label={t("pdf.previous")} onclick={goToPrevPage} disabled={currentPage <= 1}>
        <span aria-hidden="true">&#8592;</span>
        {t("pdf.previous")}
      </button>
      <button type="button" class="inline-flex items-center gap-1.5 px-3 py-1.5 border border-[var(--color-border)] rounded bg-[var(--pdf-reader-surface-bg,var(--color-surface))] text-[var(--pdf-reader-text,var(--color-primary))] cursor-pointer text-[13px] hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed" aria-label={t("pdf.next")} onclick={goToNextPage} disabled={currentPage >= totalPages}>
        <span aria-hidden="true">&#8594;</span>
        {t("pdf.next")}
      </button>
      <button type="button" class="flex flex-col items-center gap-0.5 px-3 py-1.5 border border-[var(--color-border)] rounded bg-[var(--pdf-reader-surface-bg,var(--color-surface))] text-[var(--pdf-reader-text,var(--color-primary))] cursor-pointer text-[13px] hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed" onclick={toggleFullscreen} disabled={!fullscreenSupported}>
        <Icon name={isFullscreen ? "fullscreen-exit" : "fullscreen-enter"} size="sm" />
        <span class="text-[10px] leading-tight">{isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}</span>
      </button>
      <select bind:value={scale} onchange={() => setScale(scale)} class="ml-auto px-2 py-1 border border-[var(--color-border)] rounded bg-[var(--pdf-reader-surface-bg,var(--color-surface))] text-[var(--pdf-reader-text,var(--color-primary))]">
        {#each scaleOptions as option (option)}
          <option value={option}>{Math.round(option * 100)}%</option>
        {/each}
      </select>
    </div>
    {#if navigationError}
      <p class="p-2 text-red-600 text-[13px]" role="status" aria-live="polite">{navigationError}</p>
    {/if}
    <div class="flex flex-1 overflow-hidden" style="visibility: {isLoading || error ? 'hidden' : 'visible'}">
      {#if showToc}
        <aside class="w-60 flex-shrink-0 overflow-y-auto border-r border-[var(--color-border)]" style="background: var(--pdf-reader-surface-bg, var(--color-surface))">
          <h3 class="m-0 p-3 text-[14px] font-semibold border-b border-[var(--color-border)] text-[var(--pdf-reader-text,var(--color-primary))]">{t("pdf.tableOfContents")}</h3>
          {#if tocLoading}
            <p class="m-0 p-3 text-[13px] text-[var(--color-text-muted)]">{t("pdf.tocLoading")}</p>
          {:else if tocError}
            <p class="m-0 p-3 text-[13px] text-red-600">{t("pdf.tocError")}</p>
          {:else if flatOutline.length === 0}
            <p class="m-0 p-3 text-[13px] text-[var(--color-text-muted)]">{t("pdf.tocEmpty")}</p>
          {:else}
            <ul class="list-none m-0 p-0">
              {#each flatOutline as entry (entry.item.id)}
                <li>
                  <button
                    type="button"
                    onclick={() => navigateToOutlineItem(entry.item)}
                    class="block w-full text-left p-[10px_12px] border-none bg-transparent text-[13px] leading-relaxed break-words cursor-pointer disabled:opacity-55 disabled:cursor-default"
                    style="padding-left: calc(12px + (var(--toc-depth, 0) * 16px)); color: var(--pdf-reader-text, var(--color-primary))"
                    disabled={!entry.item.dest}
                  >
                    {entry.item.title}
                  </button>
                </li>
              {/each}
            </ul>
          {/if}
        </aside>
      {/if}
      <div class="flex-1 overflow-auto flex justify-center p-4" style="background: var(--pdf-reader-root-bg, var(--color-background))" bind:this={canvasContainer} onwheel={handleViewerWheel}>
        <div class="relative inline-block transition-transform duration-120 ease-out" class:outline-4 class:outline-blue-500={flashSearchResult} class:outline-offset-2={flashSearchResult} style={canvasWrapperStyle}>
          <canvas bind:this={canvas} class="relative z-0 shadow-[0_2px_8px_rgba(0,0,0,0.15)]" style="background: var(--pdf-reader-surface-bg, #fff)"></canvas>
          <div class="absolute inset-0 z-1 pointer-events-none" aria-hidden="true">
            {#each selectionOverlayRects as rect, index (`${rect.left}-${rect.top}-${index}`)}
              <div
                class="absolute rounded bg-[color-mix(in_srgb,var(--pdf-selection-color,#3388ff)_42%,transparent)] shadow-[0_0_0_1px_color-mix(in_srgb,var(--pdf-selection-color,#3388ff)_22%,transparent),0_2px_4px_rgba(0,0,0,0.1)] z-3 pointer-events-none"
                style={`left: ${rect.left}px; top: ${rect.top}px; width: ${rect.width}px; height: ${rect.height}px;`}
              ></div>
            {/each}
          </div>
          <div
            bind:this={textLayer}
            class="absolute top-0 left-0 inset-0 overflow-hidden pointer-events-auto opacity-1 leading-none cursor-text text-initial z-[5000] min-w-full min-h-full"
            draggable="false"
            role="presentation"
            ondragstart={(e) => e.preventDefault()}
          ></div>
          <div class="debug-info-overlay">
            <div>Selection: {selectedText || 'None'}</div>
            <div>Pos: {selectionPosition ? `x:${Math.round(selectionPosition.x)} y:${Math.round(selectionPosition.y)}` : 'None'}</div>
            <div>Layer: {textLayer ? 'Exists' : 'Missing'}</div>
            <div>Spans: {textLayer?.children?.length || 0}</div>
          </div>
          {#if showToolbar && selectionPosition}
            <div
              class="absolute -translate-x-1/2 z-[100] w-[min(320px,calc(100vw-32px))]"
              class:below={selectionPlacement === "below"}
              style="left: {selectionPosition.x}px; top: {selectionPosition.y}px;"
            >
              <HighlightToolbar
                {selectedText}
                selectionBounds={lastSelectionBounds}
                bookId={bookId || filePath}
                pageNumber={currentPage}
                cfi={selectedCfi}
                hasSelectionAnchor={selectionHasAnchor}
                {t}
                onClose={hideToolbar}
              />
            </div>
          {/if}
        </div>
      </div>
    </div>
    <div class="flex items-center h-12 p-4 border-t z-20" style="background: var(--pdf-reader-surface-bg, var(--color-surface)); border-color: var(--color-border); visibility: {isLoading || error ? 'hidden' : 'visible'}">
      <div class="flex items-center justify-between w-full max-w-[1200px] mx-auto gap-8">
        <div class="flex items-center gap-3">
          <span class="flex items-center gap-1 text-[13px]" style="color: var(--pdf-reader-text, var(--color-primary))">
            <input
              type="number"
              min="1"
              max={totalPages}
              value={currentPage}
              onchange={goToPage}
              class="w-12 p-1 border border-[var(--color-border)] rounded text-center bg-[var(--pdf-reader-surface-bg,var(--color-surface))] text-[var(--pdf-reader-text,var(--color-primary))]"
            />
            <span class="opacity-70">/ {totalPages}</span>
          </span>
        </div>
        <div class="flex items-center gap-4 flex-1 max-w-[800px]">
          <span class="text-[12px] font-medium opacity-80 whitespace-nowrap" style="color: var(--pdf-reader-text, #64748b)">{totalPages - currentPage} {t("pdf.pagesLeft")}</span>
          <div class="flex-1 h-1.5 rounded-full overflow-hidden bg-[rgba(148,163,184,0.15)]">
            <div class="h-full rounded-full transition-all duration-400" style="background: var(--pdf-selection-color, #3b82f6); width: {(currentPage / totalPages) * 100}%"></div>
          </div>
          <span class="text-[12px] font-medium opacity-80 whitespace-nowrap" style="color: var(--pdf-reader-text, #64748b)">{Math.round((currentPage / totalPages) * 100)}%</span>
        </div>
      </div>
    </div>
  </div>
</ErrorBoundary>

<style>
  .pdf-viewer {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--pdf-reader-root-bg, var(--color-background));
    color: var(--pdf-reader-text, var(--color-primary));
    position: relative;
    outline: none;
    user-select: text !important;
    -webkit-user-select: text !important;
  }

  .loading-overlay,
  .error-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    z-index: 10;
    background: var(--color-background);
  }

  .error-overlay {
    color: #dc2626;
  }

  .controls {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    border-bottom: 1px solid var(--color-border);
    flex-wrap: wrap;
  }

  .controls button {
    padding: 6px 12px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    color: var(--pdf-reader-text, var(--color-primary));
    cursor: pointer;
    font-size: 13px;
  }

  .toc-sidebar {
    width: 240px;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    border-right: 1px solid var(--color-border);
    overflow-y: auto;
    flex-shrink: 0;
  }

  .toc-sidebar h3 {
    margin: 0;
    padding: 12px;
    font-size: 14px;
    font-weight: 600;
    border-bottom: 1px solid var(--color-border);
    color: var(--pdf-reader-text, var(--color-primary));
  }

  .toc-list {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .toc-item {
    display: block;
    width: 100%;
    padding: 10px 12px 10px calc(12px + (var(--toc-depth, 0) * 16px));
    border: none;
    background: transparent;
    text-align: left;
    cursor: pointer;
    font-size: 13px;
    line-height: 1.4;
    word-break: break-word;
    color: var(--pdf-reader-text, var(--color-primary));
  }

  .toc-item:hover:not(:disabled) {
    background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface));
  }

  .toc-item:disabled {
    opacity: 0.55;
    cursor: default;
  }

  .toc-message {
    margin: 0;
    padding: 12px;
    font-size: 13px;
    color: var(--color-text-muted);
  }

  .toc-error {
    color: #dc2626;
  }

  .canvas-container {
    flex: 1;
    overflow: auto;
    display: flex;
    justify-content: center;
    padding: 16px;
    background: var(--pdf-reader-root-bg, var(--color-background));
  }

  .navigation-error {
    margin: 0;
    padding: 8px 12px 0;
    color: #dc2626;
    font-size: 13px;
  }

  .canvas-wrapper {
    position: relative;
    display: inline-block;
    transition: transform 120ms ease-out;
    isolation: isolate;
  }

  .canvas-wrapper.search-hit {
    outline: 3px solid #3b82f6;
    outline-offset: 6px;
    border-radius: 4px;
  }

  canvas {
    position: relative;
    z-index: 0;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    background: var(--pdf-reader-surface-bg, #fff);
  }

  .selection-overlay {
    position: absolute;
    inset: 0;
    z-index: 1;
    pointer-events: none;
  }

  .selection-rect {
    position: absolute;
    border-radius: 4px;
    background: color-mix(in srgb, var(--pdf-selection-color, #3388ff) 42%, transparent);
    box-shadow:
      0 0 0 1px color-mix(in srgb, var(--pdf-selection-color, #3388ff) 22%, transparent),
      0 2px 4px rgba(0, 0, 0, 0.1);
    z-index: 3;
    pointer-events: none;
  }

  .text-layer {
    position: absolute;
    top: 0;
    left: 0;
    inset: 0;
    overflow: hidden;
    pointer-events: auto !important;
    opacity: 1;
    line-height: 1;
    cursor: text !important;
    user-select: text !important;
    -webkit-user-select: text !important;
    text-align: initial;
    z-index: 5000 !important;
    min-width: 100%;
    min-height: 100%;
    -webkit-user-drag: none !important;
    user-drag: none !important;
  }

  .text-layer :global(span),
  .text-layer :global(br) {
    color: transparent;
    position: absolute;
    white-space: pre;
    transform-origin: 0% 0%;
    cursor: text !important;
    pointer-events: auto !important;
    user-select: text !important;
    -webkit-user-select: text !important;
    -webkit-user-drag: none !important;
    user-drag: none !important;
  }

  .text-layer :global(span)::selection,
  .text-layer :global(br)::selection {
    background: var(--pdf-selection-color, rgba(51, 136, 255, 0.4)) !important;
  }

  .text-layer :global(span)::-moz-selection,
  .text-layer :global(br)::-moz-selection {
    background: var(--pdf-selection-color, rgba(51, 136, 255, 0.4)) !important;
  }

  .toolbar-container {
    position: absolute;
    transform: translateX(-50%);
    z-index: 100;
    width: min(320px, calc(100vw - 32px));
  }

  .toolbar-container::before {
    content: "";
    position: absolute;
    left: 50%;
    bottom: -8px;
    width: 16px;
    height: 16px;
    transform: translateX(-50%) rotate(45deg);
    background: color-mix(in srgb, var(--pdf-reader-surface-bg, #fff) 92%, #0f172a 8%);
    border-right: 1px solid rgba(148, 163, 184, 0.28);
    border-bottom: 1px solid rgba(148, 163, 184, 0.28);
    z-index: -1;
  }

  .toolbar-container.below::before {
    top: -8px;
    bottom: auto;
    border-right: none;
    border-bottom: none;
    border-left: 1px solid rgba(148, 163, 184, 0.28);
    border-top: 1px solid rgba(148, 163, 184, 0.28);
  }

  @media (max-width: 900px) {
    .toc-sidebar {
      width: min(240px, 70vw);
    }

    .scale-select {
      margin-left: 0;
    }
  }

  .pdf-footer {
    padding: 8px 16px;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    border-top: 1px solid var(--color-border);
    z-index: 20;
    height: 48px;
    display: flex;
    align-items: center;
  }

  .footer-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    max-width: 1200px;
    margin: 0 auto;
    gap: 32px;
  }

  .footer-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .progress-details {
    display: flex;
    align-items: center;
    gap: 16px;
    flex: 1;
    max-width: 800px;
  }

  .progress-bar-container {
    flex: 1;
    height: 6px;
    background: rgba(148, 163, 184, 0.15);
    border-radius: 3px;
    overflow: hidden;
  }

  .progress-bar-fill {
    height: 100%;
    background: var(--pdf-selection-color, #3b82f6);
    border-radius: 3px;
    transition: width 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .pages-left, .percent-complete {
    font-size: 12px;
    font-weight: 500;
    color: var(--pdf-reader-text, #64748b);
    opacity: 0.8;
    white-space: nowrap;
  }

  .total-pages {
    font-size: 13px;
    color: var(--pdf-reader-text, #64748b);
    opacity: 0.7;
  }

  .reader-nav-button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .controls button:hover:not(:disabled) {
    background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface));
  }

  .controls button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .page-info {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--pdf-reader-text, var(--color-primary));
  }

  .page-input {
    width: 50px;
    padding: 4px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    text-align: center;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    color: var(--pdf-reader-text, var(--color-primary));
  }

  .scale-select {
    padding: 4px 8px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    margin-left: auto;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    color: var(--pdf-reader-text, var(--color-primary));
  }

  .content-area {
    display: flex;
    flex: 1;
    overflow: hidden;
  }
  .debug-text-layer {
    background: rgba(255, 0, 0, 0.1) !important;
    outline: 2px dashed red !important;
  }

  .debug-info-overlay {
    position: absolute;
    top: 10px;
    right: 10px;
    background: rgba(0, 0, 0, 0.8);
    color: #00ff00;
    padding: 8px;
    border-radius: 4px;
    font-family: monospace;
    font-size: 10px;
    z-index: 9999;
    pointer-events: none;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .selection-rect {
    position: absolute;
    border-radius: 4px;
    background: color-mix(in srgb, var(--pdf-selection-color, #3388ff) 42%, transparent);
    box-shadow: 0 0 0 2px red; /* Extra visible shadow for debug */
    z-index: 3;
    pointer-events: none;
  }
</style>