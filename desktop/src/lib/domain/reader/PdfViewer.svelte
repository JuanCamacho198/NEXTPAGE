<script lang="ts">
  import * as pdfjsLib from "pdfjs-dist";
  import { onMount } from "svelte";
  import { getFileBytes } from "$lib/api/tauriClient";
  import HighlightToolbar from "./HighlightToolbar.svelte";
  import type { MessageKey } from "$lib/i18n";
  import type { PdfOutlineItem, ReaderSettings, ReaderThemeMode } from "$lib/types";
  import {
    adjustPdfScaleForWheel,
    clampPdfScale,
    DEFAULT_PDF_SCALE,
    isPageWithinBounds,
    PDF_SCALE_STEP,
  } from "./pdfNavigation";
  import { resolveReaderArrowIntent } from "./keyboardNav";

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
  const scaleOptions = Array.from({ length: 26 }, (_, index) => (50 + index * 10) / 100);
  const TOOLBAR_OFFSET = 18;
  const TOOLBAR_WIDTH_ESTIMATE = 320;
  const TOOLBAR_EDGE_PADDING = 16;
  const VERTICAL_SCROLL_STEP_PX = 120;
  const ZOOM_COMMIT_DELAY_MS = 120;
  const ZOOM_EPSILON = 0.001;

  type RefLike = { num: number; gen: number };
  type PdfRefProxy = Parameters<pdfjsLib.PDFDocumentProxy["getPageIndex"]>[0];

  type FlatOutlineItem = {
    item: PdfOutlineItem;
    depth: number;
  };

  const isStaleLoad = (requestId: number) => requestId !== activeLoadRequestId;

  const isStaleNavigation = (requestId: number) => requestId !== activeNavigationRequestId;

  const clamp = (value: number, min: number, max: number) => {
    return Math.min(max, Math.max(min, Math.round(value)));
  };

  const clampSelectionPoint = (value: number, min: number, max: number) => {
    if (max < min) {
      return min;
    }

    return Math.min(max, Math.max(min, value));
  };

  const resolveThemePalette = (themeMode: ReaderThemeMode) => {
    if (themeMode === "sepia") {
      return {
        rootBackground: "#efe2cc",
        surfaceBackground: "#f6ebd8",
        textColor: "#2f2416",
      };
    }

    if (themeMode === "night") {
      return {
        rootBackground: "#0f1320",
        surfaceBackground: "#161c2d",
        textColor: "#e8ecf7",
      };
    }

    return {
      rootBackground: "#f4efe1",
      surfaceBackground: "#fbf7ed",
      textColor: "#221a12",
    };
  };

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
    textLayerMouseupTarget = null;
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
        showToolbar = false;
        selectionHasAnchor = false;
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

    const textDivs: HTMLSpanElement[] = [];
    const sharedParams = {
      container: textLayer,
      viewport,
      textDivs,
      enhanceTextSelection: true,
    };

    const pdfLibWithTextLayer = pdfjsLib as unknown as {
      renderTextLayer?: (params: {
        container: HTMLDivElement;
        viewport: pdfjsLib.PageViewport;
        textDivs: HTMLSpanElement[];
        enhanceTextSelection: boolean;
        textContent?: unknown;
        textContentSource?: unknown;
      }) => { cancel?: () => void; promise?: Promise<void> } | void;
    };

    let task =
      pdfLibWithTextLayer.renderTextLayer?.({
        ...sharedParams,
        textContentSource: textContent,
      }) ?? null;

    if (!task) {
      task =
        pdfLibWithTextLayer.renderTextLayer?.({
          ...sharedParams,
          textContent,
        }) ?? null;
    }

    if (task) {
      activeTextLayerTask = task;
      if (task.promise) {
        try {
          await task.promise;
        } catch {
          return;
        } finally {
          if (activeTextLayerTask === task) {
            activeTextLayerTask = null;
          }
        }
      } else if (activeTextLayerTask === task) {
        activeTextLayerTask = null;
      }
    }

    textLayer.addEventListener("mouseup", handleTextSelection);
    textLayer.addEventListener("touchend", handleTextSelection);
    textLayerMouseupTarget = textLayer;
  }

  function handleTextSelection() {
    queueMicrotask(updateSelectionState);
  }

  function updateSelectionState() {
    const selection = window.getSelection();
    const text = selection?.toString().trim();

    if (text && text.length > 0) {
      selectedText = text;
      const containerRect = textLayer?.getBoundingClientRect();

      selectedCfi = null;
      let hasAnchor = false;
      let nextPosition: { x: number; y: number } | null = null;

      // Store selection bounds for highlight persistence
      let selectionBounds = { left: 0, top: 0, right: 0, bottom: 0 };

      if (selection && selection.rangeCount > 0) {
        try {
          const range = selection.getRangeAt(0);
          const rects = Array.from(range.getClientRects()).filter((rect) => rect.width > 0 && rect.height > 0);
          const fallbackRect = range.getBoundingClientRect();
          const resolvedRects =
            rects.length > 0
              ? rects
              : fallbackRect.width > 0 || fallbackRect.height > 0
                ? [fallbackRect]
                : [];

          if (resolvedRects.length > 0 && containerRect) {
            const left = Math.min(...resolvedRects.map((rect) => rect.left));
            const top = Math.min(...resolvedRects.map((rect) => rect.top));
            const right = Math.max(...resolvedRects.map((rect) => rect.right));
            const bottom = Math.max(...resolvedRects.map((rect) => rect.bottom));
            const selectionCenter = left + (right - left) / 2;
            const anchorX = clampSelectionPoint(
              selectionCenter - containerRect.left,
              TOOLBAR_EDGE_PADDING + TOOLBAR_WIDTH_ESTIMATE / 2,
              containerRect.width - TOOLBAR_EDGE_PADDING - TOOLBAR_WIDTH_ESTIMATE / 2,
            );
            const canPlaceAbove = top - containerRect.top > 76;

            hasAnchor = true;
            selectionPlacement = canPlaceAbove ? "above" : "below";
            nextPosition = {
              x: anchorX,
              y: canPlaceAbove
                ? top - containerRect.top - TOOLBAR_OFFSET
                : bottom - containerRect.top + TOOLBAR_OFFSET,
            };
            selectionBounds = {
              left: left - containerRect.left,
              top: top - containerRect.top,
              right: right - containerRect.left,
              bottom: bottom - containerRect.top,
            };
          }
        } catch {
          hasAnchor = false;
        }
      }

      // Store selection bounds in a variable accessible to saveHighlight
      lastSelectionBounds = selectionBounds;

      if (!nextPosition && containerRect) {
        selectionPlacement = "below";
        nextPosition = {
          x: containerRect.width / 2,
          y: 16,
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
        showToolbar = false;
        selectionHasAnchor = false;
      }
    } else {
      showToolbar = false;
      selectedText = "";
      selectedCfi = null;
      selectionHasAnchor = false;
    }
  }

  function hideToolbar() {
    showToolbar = false;
    selectedText = "";
    selectedCfi = null;
    selectionHasAnchor = false;
    window.getSelection()?.removeAllRanges();
  }

  async function navigateToPage(targetPage: number, options?: { flash?: boolean }): Promise<boolean> {
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
  }

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
</script>

<svelte:window onkeydown={handleViewerKeydown} />

<div
  class="pdf-viewer"
  bind:this={viewerRoot}
  tabindex="0"
  onfocus={() => {
    isViewerFocused = true;
  }}
  onblur={() => {
    isViewerFocused = false;
  }}
  onclick={() => {
    viewerRoot?.focus();
  }}
  style={`--pdf-reader-root-bg: ${readerThemePalette.rootBackground}; --pdf-reader-surface-bg: ${readerThemePalette.surfaceBackground}; --pdf-reader-text: ${readerThemePalette.textColor}; --pdf-selection-color: ${readerSettings.selectionColor};`}
>
  {#if isLoading}
    <div class="loading-overlay">{t("pdf.loading")}</div>
  {/if}
  {#if error}
    <div class="error-overlay">{t("pdf.error")}: {error}</div>
  {/if}
  <!-- Controls and canvas always stay in DOM so canvas ref is always available -->
  <div class="controls" style:visibility={isLoading || error ? 'hidden' : 'visible'}>
    <button type="button" onclick={() => (showToc = !showToc)}>
      {t("pdf.contents")}
    </button>
    <button type="button" class="reader-nav-button" aria-label={t("pdf.previous")} onclick={goToPrevPage} disabled={currentPage <= 1}>
      <span aria-hidden="true">&#8592;</span>
      {t("pdf.previous")}
    </button>
    <span class="page-info">
      <input
        type="number"
        min="1"
        max={totalPages}
        value={currentPage}
        onchange={goToPage}
        class="page-input"
      />
      / {totalPages}
      <span class="page-progress"> · {totalPages - currentPage} {t("pdf.pagesLeft")} | {Math.round((currentPage / totalPages) * 100)}%</span>
    </span>
    <button type="button" class="reader-nav-button" aria-label={t("pdf.next")} onclick={goToNextPage} disabled={currentPage >= totalPages}>
      <span aria-hidden="true">&#8594;</span>
      {t("pdf.next")}
    </button>
    <button type="button" onclick={toggleFullscreen} disabled={!fullscreenSupported}>
      {isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}
    </button>
    <select bind:value={scale} onchange={() => setScale(scale)} class="scale-select">
      {#each scaleOptions as option (option)}
        <option value={option}>{Math.round(option * 100)}%</option>
      {/each}
    </select>
  </div>
  {#if navigationError}
    <p class="navigation-error" role="status" aria-live="polite">{navigationError}</p>
  {/if}
  <div class="content-area" style:visibility={isLoading || error ? 'hidden' : 'visible'}>
    {#if showToc}
      <aside class="toc-sidebar">
        <h3>{t("pdf.tableOfContents")}</h3>
        {#if tocLoading}
          <p class="toc-message">{t("pdf.tocLoading")}</p>
        {:else if tocError}
          <p class="toc-message toc-error">{tocError}</p>
        {:else if flatOutline.length === 0}
          <p class="toc-message">{t("pdf.tocEmpty")}</p>
        {:else}
          <ul class="toc-list">
            {#each flatOutline as entry (entry.item.id)}
              <li>
                <button
                  type="button"
                  onclick={() => navigateToOutlineItem(entry.item)}
                  class="toc-item"
                  disabled={!entry.item.dest}
                  style={`--toc-depth: ${entry.depth};`}
                >
                  {entry.item.title}
                </button>
              </li>
            {/each}
          </ul>
        {/if}
      </aside>
    {/if}
    <div class="canvas-container" bind:this={canvasContainer} onwheel={handleViewerWheel}>
      <div class="canvas-wrapper" class:search-hit={flashSearchResult} style={canvasWrapperStyle}>
        <canvas bind:this={canvas}></canvas>
        <div bind:this={textLayer} class="text-layer"></div>
        {#if showToolbar && selectionPosition}
          <div
            class="toolbar-container"
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
</div>

<style>
  .pdf-viewer {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--pdf-reader-root-bg, var(--color-background));
    color: var(--pdf-reader-text, var(--color-primary));
    position: relative;
    outline: none;
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

  .page-progress {
    margin-left: 8px;
    color: var(--pdf-reader-muted, #888);
    font-size: 12px;
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
  }

  .canvas-wrapper.search-hit {
    outline: 3px solid #3b82f6;
    outline-offset: 6px;
    border-radius: 4px;
  }

  canvas {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    background: var(--pdf-reader-surface-bg, #fff);
  }

  .text-layer {
    position: absolute;
    top: 0;
    left: 0;
    overflow: hidden;
    pointer-events: auto;
    opacity: 1;
    line-height: 1;
    cursor: text;
    user-select: text;
    -webkit-user-select: text;
  }

  .text-layer :global(span),
  .text-layer :global(br) {
    color: transparent;
    position: absolute;
    white-space: pre;
    transform-origin: 0% 0%;
    cursor: text;
    pointer-events: auto;
  }

/* Highlighter-style selection: uniform rectangular box, not per-glyph */
  .text-layer :global(span)::selection {
    background: var(--pdf-selection-color, #3388ff) !important;
    color: inherit !important;
    -webkit-text-fill-color: inherit !important;
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
</style>
