<script lang="ts">
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import type { MessageKey } from "$lib/i18n";
  import { scaleOptions } from "$lib/features/reader/pdf/pdfState.svelte";
  import { debugState } from "$lib/debug/debugState.svelte";

  type Props = {
    currentPage: number;
    totalPages: number;
    scale: number;
    isFullscreen: boolean;
    fullscreenSupported: boolean;
    showToc: boolean;
    isLoading: boolean;
    error: string | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onPrevPage: () => void;
    onNextPage: () => void;
    onGoToPage: (page: number) => Promise<boolean>;
    onSetScale: (scale: number) => void;
    onToggleFullscreen: () => void;
    onToggleToc: () => void;
  };

  let {
    currentPage,
    totalPages,
    scale,
    isFullscreen,
    fullscreenSupported,
    showToc,
    isLoading,
    error,
    t,
    onPrevPage,
    onNextPage,
    onGoToPage,
    onSetScale,
    onToggleFullscreen,
    onToggleToc,
  }: Props = $props();

  async function handleGoToPage(event: Event) {
    const target = event.target as HTMLInputElement;
    const page = Number.parseInt(target.value, 10);
    if (Number.isFinite(page) && page >= 1 && page <= totalPages) {
      const success = await onGoToPage(page);
      if (!success) {
        target.value = String(currentPage);
      }
    } else {
      target.value = String(currentPage);
    }
  }
</script>

<div class="controls" style:visibility={isLoading || error ? 'hidden' : 'visible'}>
  <button type="button" onclick={onToggleToc} title={t("pdf.contents")}>
    <Icon name="menu" size="sm" />
  </button>
  <button type="button" onclick={onPrevPage} disabled={currentPage <= 1} title={t("pdf.previous")}>
    <Icon name="chevron-left" size="sm" />
  </button>
  <button type="button" onclick={onNextPage} disabled={currentPage >= totalPages} title={t("pdf.next")}>
    <Icon name="arrow-right" size="sm" />
  </button>
  <span class="page-info">
    <input
      type="number"
      min="1"
      max={totalPages}
      value={currentPage}
      onchange={handleGoToPage}
      class="page-input"
    />
    <span class="total-pages">/ {totalPages}</span>
  </span>
  <button
    type="button"
    onclick={onToggleFullscreen}
    disabled={!fullscreenSupported}
    title={isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}
  >
    <Icon name={isFullscreen ? "fullscreen-exit" : "fullscreen-enter"} size="sm" />
  </button>
  {#if debugState.enabled}
    <span class="debug-info">p{currentPage}/{totalPages} | {Math.round(scale * 100)}%</span>
  {/if}
  <select
    value={scale}
    onchange={(e) => onSetScale(Number(e.currentTarget.value))}
    class="scale-select"
    title={t("pdf.zoomLevel", { level: String(Math.round(scale * 100)) })}
  >
    {#each scaleOptions as option (option)}
      <option value={option}>{Math.round(option * 100)}%</option>
    {/each}
  </select>
</div>

<style>
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
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 6px 10px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    color: var(--pdf-reader-text, var(--color-primary));
    cursor: pointer;
    font-size: 13px;
    min-width: 32px;
    min-height: 32px;
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

  .total-pages {
    font-size: 13px;
    color: var(--pdf-reader-text, #64748b);
    opacity: 0.7;
  }

  .scale-select {
    padding: 4px 8px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    margin-left: auto;
    background: var(--pdf-reader-surface-bg, var(--color-surface));
    color: var(--pdf-reader-text, var(--color-primary));
  }

  .debug-info {
    font-size: 12px;
    color: var(--pdf-reader-text, #64748b);
    opacity: 0.6;
    font-family: monospace;
  }

  @media (max-width: 900px) {
    .scale-select {
      margin-left: 0;
    }
  }
</style>
