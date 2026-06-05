<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    currentPercentage: number;
    fontSize: number;
    showToc: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onPrev: () => void;
    onNext: () => void;
    onFontSizeChange: (size: number) => void;
    onToggleToc: () => void;
  };

  let {
    currentPercentage,
    fontSize,
    showToc,
    t,
    onPrev,
    onNext,
    onFontSizeChange,
    onToggleToc,
  }: Props = $props();
</script>

<div class="flex items-center gap-3 px-3 py-2 bg-(--color-surface) border-b border-(--color-border) flex-wrap">
  <button type="button" onclick={onToggleToc} class="px-3 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]">
    {showToc ? t('epub.hide') : t('epub.toc')}
  </button>
  <span class="text-xs text-(--color-text-muted) min-w-10 text-center">{Math.round(currentPercentage)}%</span>
  <div class="flex gap-2">
    <button type="button" onclick={onPrev} class="px-3 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]" aria-label={t('epub.previous')}>
      <span aria-hidden="true">&larr;</span> {t('epub.previous')}
    </button>
    <button type="button" onclick={onNext} class="px-3 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]" aria-label={t('epub.next')}>
      <span aria-hidden="true">&rarr;</span> {t('epub.next')}
    </button>
  </div>
  <div class="flex items-center gap-1 ml-auto">
    <button type="button" onclick={() => onFontSizeChange(fontSize - 10)} class="px-3 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]">
      A-
    </button>
    <span class="text-[11px] min-w-10 text-center text-(--color-primary)">{fontSize}%</span>
    <button type="button" onclick={() => onFontSizeChange(fontSize + 10)} class="px-3 py-1.5 border border-(--color-border) rounded bg-(--color-surface) text-(--color-primary) cursor-pointer text-xs hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]">
      A+
    </button>
  </div>
</div>
