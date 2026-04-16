<script lang="ts">
  import * as pdfjsLib from "pdfjs-dist";
  import { Util } from "pdfjs-dist";
  import { onMount } from "svelte";
  import HighlightToolbar from "./HighlightToolbar.svelte";

  type Props = {
    filePath: string;
    initialPage?: number;
    onPageChange?: (page: number, total: number) => void;
  };

  let { filePath, initialPage = 1, onPageChange }: Props = $props();

  let canvas: HTMLCanvasElement | undefined = $state();
  let textLayer: HTMLDivElement | undefined = $state();
  let currentPage = $state(initialPage);
  let totalPages = $state(0);
  let scale = $state(1.5);
  let isLoading = $state(true);
  let error = $state<string | null>(null);

  let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null;
  let currentPageObj: pdfjsLib.PDFPageProxy | null = null;

  let selectedText = $state("");
  let selectionPosition = $state<{ x: number; y: number } | null>(null);
  let showToolbar = $state(false);

  onMount(() => {
    pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
      "pdfjs-dist/build/pdf.worker.min.mjs",
      import.meta.url
    ).toString();

    return () => {
      if (pdfDoc) {
        pdfDoc.destroy();
      }
    };
  });

  $effect(() => {
    if (filePath) {
      loadPdf();
    }
  });

  async function loadPdf() {
    if (!filePath) return;

    isLoading = true;
    error = null;

    try {
      if (pdfDoc) {
        pdfDoc.destroy();
      }

      const loadingTask = pdfjsLib.getDocument(filePath);
      pdfDoc = await loadingTask.promise;
      totalPages = pdfDoc.numPages;
      currentPage = Math.min(initialPage, totalPages);

      await renderPage(currentPage);
      onPageChange?.(currentPage, totalPages);
    } catch (err) {
      error = err instanceof Error ? err.message : "Failed to load PDF";
    } finally {
      isLoading = false;
    }
  }

  async function renderPage(pageNum: number) {
    if (!pdfDoc || !canvas || !textLayer) return;

    const page = await pdfDoc.getPage(pageNum);
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

    await page.render(renderContext).promise;

    textLayer.style.width = `${viewport.width}px`;
    textLayer.style.height = `${viewport.height}px`;

    const textContent = await page.getTextContent();
    renderTextLayer(textContent as { items: Array<{ str: string; transform: number[]; width: number; height: number }> }, viewport);
  }

  async function renderTextLayer(textContent: { items: Array<{ str: string; transform: number[]; width: number; height: number }> }, viewport: pdfjsLib.PageViewport) {
    if (!textLayer) return;

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

  function goToPrevPage() {
    if (currentPage <= 1) return;
    hideToolbar();
    currentPage--;
    renderPage(currentPage);
    onPageChange?.(currentPage, totalPages);
  }

  function goToNextPage() {
    if (currentPage >= totalPages) return;
    hideToolbar();
    currentPage++;
    renderPage(currentPage);
    onPageChange?.(currentPage, totalPages);
  }

  async function goToPage(event: Event) {
    const target = event.target as HTMLInputElement;
    const page = parseInt(target.value, 10);
    if (page >= 1 && page <= totalPages) {
      hideToolbar();
      currentPage = page;
      await renderPage(currentPage);
      onPageChange?.(currentPage, totalPages);
    }
  }

  export function setScale(newScale: number) {
    scale = newScale;
    if (pdfDoc) {
      renderPage(currentPage);
    }
  }

  export function getCurrentPage() {
    return currentPage;
  }

  export function getCurrentFilePath() {
    return filePath;
  }
</script>

<div class="pdf-viewer">
  {#if isLoading}
    <div class="loading">Loading PDF...</div>
  {:else if error}
    <div class="error">Error: {error}</div>
  {:else}
    <div class="controls">
      <button type="button" onclick={goToPrevPage} disabled={currentPage <= 1}>
        Previous
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
        Next
      </button>
      <select bind:value={scale} onchange={() => setScale(scale)} class="scale-select">
        <option value={1}>100%</option>
        <option value={1.5}>150%</option>
        <option value={2}>200%</option>
      </select>
    </div>
    <div class="canvas-container">
      <div class="canvas-wrapper">
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
    background: #f5f5f5;
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
    background: #fff;
    border-bottom: 1px solid #e5e7eb;
  }

  .controls button {
    padding: 6px 12px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    background: #fff;
    cursor: pointer;
    font-size: 13px;
  }

  .controls button:hover:not(:disabled) {
    background: #f3f4f6;
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
  }

  .page-input {
    width: 50px;
    padding: 4px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    text-align: center;
  }

  .scale-select {
    padding: 4px 8px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    margin-left: auto;
  }

  .canvas-container {
    flex: 1;
    overflow: auto;
    display: flex;
    justify-content: center;
    padding: 16px;
  }

  .canvas-wrapper {
    position: relative;
    display: inline-block;
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