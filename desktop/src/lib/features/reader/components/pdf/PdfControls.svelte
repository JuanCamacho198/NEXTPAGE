<script lang="ts">
  import Icon from "$lib/shared/ui/navigation/Icon.svelte";
  import type { MessageKey } from "$lib/shared/i18n";
  import { scaleOptions } from "$lib/features/reader/pdf/pdfState.svelte";
  import { debugState } from "$lib/shared/debug/debugState.svelte";

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

  async function handleGoToPage(event: Event): Promise<void> {
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

<div class="flex items-center gap-3 px-3 py-2 bg-(--pdf-reader-surface-bg,var(--color-surface)) border-b border-(--color-border) flex-wrap" style:visibility={isLoading || error ? 'hidden' : 'visible'}>
  <button type="button" onclick={onToggleToc} title={t("pdf.contents")} class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--pdf-reader-surface-bg,var(--color-surface)) text-(--pdf-reader-text,var(--color-primary)) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed">
    <Icon name="menu" size="sm" />
  </button>
  <button type="button" onclick={onPrevPage} disabled={currentPage <= 1} title={t("pdf.previous")} class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--pdf-reader-surface-bg,var(--color-surface)) text-(--pdf-reader-text,var(--color-primary)) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed">
    <Icon name="chevron-left" size="sm" />
  </button>
  <button type="button" onclick={onNextPage} disabled={currentPage >= totalPages} title={t("pdf.next")} class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--pdf-reader-surface-bg,var(--color-surface)) text-(--pdf-reader-text,var(--color-primary)) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed">
    <Icon name="arrow-right" size="sm" />
  </button>
  <span class="flex items-center gap-1 text-xs text-(--pdf-reader-text,var(--color-primary))">
    <input
      type="number"
      min="1"
      max={totalPages}
      value={currentPage}
      onchange={handleGoToPage}
      class="w-[50px] p-1 border border-(--color-border) rounded text-center bg-(--pdf-reader-surface-bg,var(--color-surface)) text-(--pdf-reader-text,var(--color-primary))"
    />
    <span class="text-xs text-(--pdf-reader-text,var(--color-text-auxiliary)) opacity-70">/ {totalPages}</span>
  </span>
  <button
    type="button"
    onclick={onToggleFullscreen}
    disabled={!fullscreenSupported}
    title={isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}
    class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--pdf-reader-surface-bg,var(--color-surface)) text-(--pdf-reader-text,var(--color-primary)) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
  >
    <Icon name={isFullscreen ? "fullscreen-exit" : "fullscreen-enter"} size="sm" />
  </button>
  {#if debugState.enabled}
    <span class="text-[11px] text-(--pdf-reader-text,var(--color-text-auxiliary)) opacity-60 font-mono">p{currentPage}/{totalPages} | {Math.round(scale * 100)}%</span>
  {/if}
  <select
    value={scale}
    onchange={(e) => onSetScale(Number(e.currentTarget.value))}
    class="ml-auto px-2 py-1 border border-(--color-border) rounded bg-(--pdf-reader-surface-bg,var(--color-surface)) text-(--pdf-reader-text,var(--color-primary)) max-sm:ml-0"
    title={t("pdf.zoomLevel", { level: String(Math.round(scale * 100)) })}
  >
    {#each scaleOptions as option (option)}
      <option value={option}>{Math.round(option * 100)}%</option>
    {/each}
  </select>
</div>
