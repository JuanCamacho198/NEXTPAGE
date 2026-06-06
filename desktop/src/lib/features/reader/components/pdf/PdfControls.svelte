<script lang="ts">
  import ReaderControls from "../ReaderControls.svelte";
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
</script>

<div style:visibility={isLoading || error ? 'hidden' : 'visible'}>
  <ReaderControls
    {currentPage}
    {totalPages}
    {isFullscreen}
    {t}
    onPrev={onPrevPage}
    onNext={onNextPage}
    onGoToPage={onGoToPage}
    onToggleFullscreen={onToggleFullscreen}
    onToggleToc={onToggleToc}
  >
    {#snippet right()}
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
    {/snippet}
  </ReaderControls>
</div>
