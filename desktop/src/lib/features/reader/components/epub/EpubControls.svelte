<script lang="ts">
  import ReaderControls from "../ReaderControls.svelte";
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    currentPage: number;
    totalPages: number;
    currentPercentage: number;
    fontSize: number;
    isFullscreen: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onPrev: () => void;
    onNext: () => void;
    onGoToPage: (page: number) => Promise<boolean>;
    onFontSizeChange: (size: number) => void;
    onToggleFullscreen: () => void;
    onToggleToc: () => void;
  };

  let {
    currentPage,
    totalPages,
    currentPercentage,
    fontSize,
    isFullscreen,
    t,
    onPrev,
    onNext,
    onGoToPage,
    onFontSizeChange,
    onToggleFullscreen,
    onToggleToc,
  }: Props = $props();
</script>

<ReaderControls
  {currentPage}
  {totalPages}
  {isFullscreen}
  {t}
  onPrev={onPrev}
  onNext={onNext}
  onGoToPage={onGoToPage}
  onToggleFullscreen={onToggleFullscreen}
  onToggleToc={onToggleToc}
>
  {#snippet right()}
    <span class="text-xs text-(--color-text-muted) min-w-10 text-center">{Math.round(currentPercentage)}%</span>
    <div class="flex items-center gap-1 ml-auto">
      <button
        type="button"
        data-testid="epub-font-decrease"
        onclick={() => onFontSizeChange(fontSize - 10)}
        title={t('pdf.zoomLevel', { level: fontSize })}
        class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
      >
        A-
      </button>
      <span data-testid="epub-font-percent" class="text-[11px] min-w-10 text-center text-(--color-primary)">{fontSize}%</span>
      <button
        type="button"
        data-testid="epub-font-increase"
        onclick={() => onFontSizeChange(fontSize + 10)}
        title={t('pdf.zoomLevel', { level: fontSize })}
        class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
      >
        A+
      </button>
    </div>
  {/snippet}
</ReaderControls>
