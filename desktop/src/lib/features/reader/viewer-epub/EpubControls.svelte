<script lang="ts">
  import ReaderControls from '../chrome/ReaderControls.svelte';
  import ZoomDropdown from '../chrome/ZoomDropdown.svelte';
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
</script>

<ReaderControls
  {currentPage}
  {totalPages}
  {isFullscreen}
  {t}
  {onPrev}
  {onNext}
  {onGoToPage}
  {onToggleFullscreen}
  {onToggleToc}
  tocTestId="epub-toc"
  prevTestId="epub-prev"
  nextTestId="epub-next"
  fullscreenTestId="epub-fullscreen"
  pageInputTestId="epub-page-input"
  totalPagesTestId="epub-total-pages"
>
  {#snippet right()}
    <span class="text-xs text-(--color-text-muted) min-w-10 text-center"
      >{Math.round(currentPercentage)}%</span
    >
    <ZoomDropdown value={fontSize} onSelect={onFontSizeChange} />
  {/snippet}
</ReaderControls>
