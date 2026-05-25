<script lang="ts">
  import type { MessageKey } from '$lib/i18n';

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

<div class="toolbar">
  <button type="button" onclick={onToggleToc} class="toc-btn">
    {showToc ? t('epub.hide') : t('epub.toc')}
  </button>
  <span class="progress">{Math.round(currentPercentage)}%</span>
  <div class="nav-buttons">
    <button type="button" onclick={onPrev} class="nav-btn" aria-label={t('epub.previous')}>
      <span aria-hidden="true">&larr;</span> {t('epub.previous')}
    </button>
    <button type="button" onclick={onNext} class="nav-btn" aria-label={t('epub.next')}>
      <span aria-hidden="true">&rarr;</span> {t('epub.next')}
    </button>
  </div>
  <div class="settings-controls">
    <button type="button" onclick={() => onFontSizeChange(fontSize - 10)} class="size-btn">
      A-
    </button>
    <span class="size-label">{fontSize}%</span>
    <button type="button" onclick={() => onFontSizeChange(fontSize + 10)} class="size-btn">
      A+
    </button>
  </div>
</div>

<style>
  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
    flex-wrap: wrap;
  }

  .toc-btn,
  .nav-btn,
  .size-btn {
    padding: 6px 12px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    background: var(--color-surface);
    color: var(--color-primary);
    cursor: pointer;
    font-size: 13px;
  }

  .toc-btn:hover,
  .nav-btn:hover,
  .size-btn:hover {
    background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface));
  }

  .nav-buttons {
    display: flex;
    gap: 8px;
  }

  .progress {
    font-size: 13px;
    color: var(--color-text-muted);
    min-width: 40px;
    text-align: center;
  }

  .settings-controls {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
  }

  .size-label {
    font-size: 12px;
    min-width: 40px;
    text-align: center;
  }

</style>
