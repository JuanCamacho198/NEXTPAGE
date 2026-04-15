<script lang="ts">
  import * as pdfjsLib from "pdfjs-dist";
  import { onMount } from "svelte";

  type Props = {
    filePath: string;
    initialPage?: number;
    onPageChange?: (page: number, total: number) => void;
  };

  let { filePath, initialPage = 1, onPageChange }: Props = $props();

  let canvas: HTMLCanvasElement | undefined = $state();
  let currentPage = $state(initialPage);
  let totalPages = $state(0);
  let scale = $state(1.5);
  let isLoading = $state(true);
  let error = $state<string | null>(null);

  let pdfDoc: pdfjsLib.PDFDocumentProxy | null = null;

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
    if (!pdfDoc || !canvas) return;

    const page = await pdfDoc.getPage(pageNum);
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
  }

  function goToPrevPage() {
    if (currentPage <= 1) return;
    currentPage--;
    renderPage(currentPage);
    onPageChange?.(currentPage, totalPages);
  }

  function goToNextPage() {
    if (currentPage >= totalPages) return;
    currentPage++;
    renderPage(currentPage);
    onPageChange?.(currentPage, totalPages);
  }

  async function goToPage(event: Event) {
    const target = event.target as HTMLInputElement;
    const page = parseInt(target.value, 10);
    if (page >= 1 && page <= totalPages) {
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
      <canvas bind:this={canvas}></canvas>
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

  canvas {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    background: #fff;
  }
</style>