<script lang="ts">
  import * as pdfjsLib from "pdfjs-dist";
  import { Util } from "pdfjs-dist";
  import { onMount } from "svelte";
  import { getFileBytes } from "../tauriClient";
  import HighlightToolbar from "./HighlightToolbar.svelte";
  import type { MessageKey } from "../i18n";
  import {
    DEFAULT_PDF_SCALE,
    isPageWithinBounds,
    resolveNavigationTransaction,
  } from "./pdfNavigation";

  type Props = {
    filePath: string;
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
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    filePath,
    initialPage = 1,
    onPageChange,
    searchTargetLocator = null,
    onSessionProgress,
    t,
  }: Props = $props();

  let canvas: HTMLCanvasElement | undefined = $state();
  let textLayer: HTMLDivElement | undefined = $state();
  let viewerRoot: HTMLDivElement | undefined = $state();
  let currentPage = $state(1);
  let totalPages = $state(0);
  let flashSearchResult = $state(false);
  let sessionStartAt = new Date().toISOString();
  let lastPercent = 0;
  let scale = $state(DEFAULT_PDF_SCALE);
  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let navigationError = $state<string | null>(null);
  let isFullscreen = $state(false);
  let fullscreenSupported = $state(true);

  let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null;
  let currentPageObj: pdfjsLib.PDFPageProxy | null = null;

  let selectedText = $state("");
  let selectionPosition = $state<{ x: number; y: number } | null>(null);
  let showToolbar = $state(false);

  let activeLoadRequestId = 0;
  let activeNavigationRequestId = 0;
  let activeLoadingTask: pdfjsLib.PDFDocumentLoadingTask | null = null;
  let activeRenderTask: pdfjsLib.RenderTask | null = null;
  let textLayerMouseupTarget: HTMLDivElement | null = null;

  const isStaleLoad = (requestId: number) => requestId !== activeLoadRequestId;

  const isStaleNavigation = (requestId: number) => requestId !== activeNavigationRequestId;

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
    textLayerMouseupTarget = null;
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

    document.addEventListener("fullscreenchange", syncFullscreenState);
    document.addEventListener("fullscreenerror", handleFullscreenError);
    fullscreenSupported = canUseFullscreenApi();

    return () => {
      activeLoadRequestId += 1;
      activeNavigationRequestId += 1;
      clearTextLayerListener();
      destroyActiveLoadingTask();
      void cancelActiveRenderTask();
      void destroyCurrentDocument();
      document.removeEventListener("fullscreenchange", syncFullscreenState);
      document.removeEventListener("fullscreenerror", handleFullscreenError);
    };
  });

  $effect(() => {
    viewerRoot;
    fullscreenSupported = canUseFullscreenApi();
  });

  $effect(() => {
    if (filePath) {
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

  async function loadPdf() {
    if (!filePath) return;

    const requestId = ++activeLoadRequestId;
    activeNavigationRequestId += 1;
    clearTextLayerListener();
    destroyActiveLoadingTask();
    await cancelActiveRenderTask();
    await destroyCurrentDocument();

    isLoading = true;
    error = null;
    navigationError = null;
    scale = DEFAULT_PDF_SCALE;

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
      const requestedPage = Math.max(1, initialPage || 1);
      const targetPage = Math.min(requestedPage, totalPages);

      const rendered = await renderPage(targetPage, requestId);
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
      if (activeLoadingTask && isStaleLoad(requestId)) {
        activeLoadingTask = null;
      } else if (activeLoadingTask && !isStaleLoad(requestId)) {
        activeLoadingTask = null;
      }

      if (!isStaleLoad(requestId)) {
        isLoading = false;
      }
    }
  }

  async function renderPage(pageNum: number, requestId = activeLoadRequestId) {
    if (!pdfDoc || !canvas || !textLayer) return;

    const page = await pdfDoc.getPage(pageNum);
    if (isStaleLoad(requestId)) {
      return false;
    }

    currentPageObj = page;
    const viewport = page.getViewport({ scale });

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

    renderTextLayer(
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

    clearTextLayerListener();

    textLayer.innerHTML = "";
    textLayer.style.position = "absolute";
    textLayer.style.left = "0";
    textLayer.style.top = "0";
    textLayer.style.pointerEvents = "none";

    const textItems = textContent.items as Array<{
      str: string;
      transform: number[];
      width: number;
      height: number;
    }>;

    for (const item of textItems) {
      const div = document.createElement("span");
      div.textContent = item.str;
      div.style.position = "absolute";
      div.style.whiteSpace = "pre";

      const tx = Util.transform(viewport.transform, item.transform);
      div.style.left = `${tx[4]}px`;
      div.style.top = `${tx[5] - item.height}px`;
      div.style.fontSize = `${item.height}px`;
      div.style.fontFamily = item.transform[0] > 0 ? "sans-serif" : "sans-serif";

      textLayer.appendChild(div);
    }

    textLayer.addEventListener("mouseup", handleTextSelection);
    textLayerMouseupTarget = textLayer;
  }

  function handleTextSelection() {
    const selection = window.getSelection();
    const text = selection?.toString().trim();

    if (text && text.length > 0) {
      selectedText = text;

      const range = selection?.getRangeAt(0);
      const rect = range?.getBoundingClientRect();
      const containerRect = textLayer?.getBoundingClientRect();

      if (rect && containerRect) {
        selectionPosition = {
          x: rect.left + rect.width / 2 - containerRect.left,
          y: rect.top - containerRect.top - 10,
        };
        showToolbar = true;
      }
    } else {
      showToolbar = false;
      selectedText = "";
    }
  }

  function hideToolbar() {
    showToolbar = false;
    selectedText = "";
    window.getSelection()?.removeAllRanges();
  }

  async function navigateToPage(targetPage: number, options?: { flash?: boolean }): Promise<boolean> {
    if (!pdfDoc || !isPageWithinBounds(targetPage, totalPages)) {
      return false;
    }

    hideToolbar();
    const previousPage = currentPage;
    const requestId = ++activeNavigationRequestId;
    const loadRequestId = activeLoadRequestId;
    navigationError = null;

    try {
      const rendered = await renderPage(targetPage, loadRequestId);
      const resolution = resolveNavigationTransaction({
        previousPage,
        targetPage,
        rendered: Boolean(rendered),
        stale: isStaleNavigation(requestId) || isStaleLoad(loadRequestId),
      });

      if (!resolution.didCommit) {
        currentPage = resolution.committedPage;
        if (resolution.shouldShowError) {
          navigationError = t("pdf.navigationFailed");
        }
        return false;
      }

      currentPage = resolution.committedPage;
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
      if (!isStaleNavigation(requestId) && !isStaleLoad(loadRequestId)) {
        currentPage = previousPage;
        navigationError = t("pdf.navigationFailed");
      }
      return false;
    }
  }

  function goToPrevPage() {
    if (currentPage <= 1) return;
    void navigateToPage(currentPage - 1);
  }

  function goToNextPage() {
    if (currentPage >= totalPages) return;
    void navigateToPage(currentPage + 1);
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

  export function setScale(newScale: number) {
    scale = newScale;
    if (pdfDoc) {
      void renderPage(currentPage);
    }
  }

  export function getCurrentPage() {
    return currentPage;
  }

  export function getCurrentFilePath() {
    return filePath;
  }
</script>

<div class="pdf-viewer" bind:this={viewerRoot}>
  {#if isLoading}
    <div class="loading">{t("pdf.loading")}</div>
  {:else if error}
    <div class="error">{t("pdf.error")}: {error}</div>
  {:else}
    <div class="controls">
      <button type="button" onclick={goToPrevPage} disabled={currentPage <= 1}>
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
      </span>
      <button type="button" onclick={goToNextPage} disabled={currentPage >= totalPages}>
        {t("pdf.next")}
      </button>
      <button type="button" onclick={toggleFullscreen} disabled={!fullscreenSupported}>
        {isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}
      </button>
      <select bind:value={scale} onchange={() => setScale(scale)} class="scale-select">
        <option value={1}>100%</option>
        <option value={1.5}>150%</option>
        <option value={2}>200%</option>
      </select>
    </div>
    {#if navigationError}
      <p class="navigation-error" role="status" aria-live="polite">{navigationError}</p>
    {/if}
    <div class="canvas-container">
      <div class="canvas-wrapper" class:search-hit={flashSearchResult}>
        <canvas bind:this={canvas}></canvas>
        <div bind:this={textLayer} class="text-layer"></div>
        {#if showToolbar && selectionPosition}
          <div
            class="toolbar-container"
            style="left: {selectionPosition.x}px; top: {selectionPosition.y}px;"
          >
            <HighlightToolbar
              {selectedText}
              bookId={filePath}
              pageNumber={currentPage}
              onClose={hideToolbar}
            />
          </div>
        {/if}
      </div>
    </div>
  {/if}
</div>

<style>
  .pdf-viewer {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--color-background);
    color: var(--color-primary);
  }

  .loading, .error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    font-size: 14px;
  }

  .error {
    color: #dc2626;
  }

  .controls {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
  }

  .controls button {
    padding: 6px 12px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    background: var(--color-surface);
    color: var(--color-primary);
    cursor: pointer;
    font-size: 13px;
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
    color: var(--color-primary);
  }

  .page-input {
    width: 50px;
    padding: 4px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    text-align: center;
    background: var(--color-surface);
    color: var(--color-primary);
  }

  .scale-select {
    padding: 4px 8px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    margin-left: auto;
    background: var(--color-surface);
    color: var(--color-primary);
  }

  .canvas-container {
    flex: 1;
    overflow: auto;
    display: flex;
    justify-content: center;
    padding: 16px;
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
  }

  .canvas-wrapper.search-hit {
    outline: 3px solid #3b82f6;
    outline-offset: 6px;
    border-radius: 4px;
  }

  canvas {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    background: #fff;
  }

  .text-layer {
    position: absolute;
    top: 0;
    left: 0;
    overflow: hidden;
    pointer-events: auto;
    opacity: 0.3;
    line-height: 1;
  }

  .text-layer :global(span) {
    color: transparent;
    position: absolute;
    white-space: pre;
    transform-origin: left bottom;
  }

  .text-layer :global(span::selection) {
    background: #3388ff;
  }

  .toolbar-container {
    position: absolute;
    transform: translateX(-50%);
    z-index: 100;
  }
</style>
