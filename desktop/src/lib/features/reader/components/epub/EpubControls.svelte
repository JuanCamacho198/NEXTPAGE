<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

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

<div class="flex items-center gap-3 px-3 py-2 bg-(--color-surface) border-b border-(--color-border) flex-wrap">
  <button
    type="button"
    data-testid="epub-toc"
    onclick={onToggleToc}
    title={t('epub.toc')}
    class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
  >
    <Icon name="menu" size="sm" />
  </button>
  <button
    type="button"
    data-testid="epub-prev"
    onclick={onPrev}
    disabled={currentPage <= 1}
    title={t('epub.previous')}
    class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
  >
    <Icon name="chevron-left" size="sm" />
  </button>
  <button
    type="button"
    data-testid="epub-next"
    onclick={onNext}
    disabled={currentPage >= totalPages}
    title={t('epub.next')}
    class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
  >
    <Icon name="arrow-right" size="sm" />
  </button>
  <span class="flex items-center gap-1 text-xs text-(--color-primary)">
    <input
      type="number"
      data-testid="epub-page-input"
      min="1"
      max={totalPages}
      value={currentPage}
      onchange={handleGoToPage}
      class="w-[50px] p-1 border border-(--color-border) rounded text-center bg-(--color-surface) text-(--color-primary)"
    />
    <span data-testid="epub-total-pages" class="text-xs text-(--color-text-muted) opacity-70">/ {totalPages}</span>
  </span>
  <button
    type="button"
    data-testid="epub-fullscreen"
    onclick={onToggleFullscreen}
    title={isFullscreen ? t('pdf.fullscreenExit') : t('pdf.fullscreenEnter')}
    class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs min-w-8 min-h-8 hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))] disabled:opacity-50 disabled:cursor-not-allowed"
  >
    {#if isFullscreen}
      <Icon name="fullscreen-exit" size="sm" />
    {:else}
      <Icon name="fullscreen-enter" size="sm" />
    {/if}
  </button>
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
</div>
