<script lang="ts">
  import ReaderControls from '../chrome/ReaderControls.svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import { scaleOptions } from '$lib/features/reader/viewer-pdf/pdfState.svelte';
  import { debugState } from '$lib/shared/debug/debugState.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';

  type Props = {
    currentPage: number;
    totalPages: number;
    scale: number;
    isFullscreen: boolean;
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

  const scaleDropdownOptions: Array<{ value: string; label: string }> = $derived(
    scaleOptions.map((s) => ({ value: String(s), label: `${Math.round(s * 100)}%` })),
  );

  let {
    currentPage,
    totalPages,
    scale,
    isFullscreen,
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
    {onGoToPage}
    {onToggleFullscreen}
    {onToggleToc}
  >
    {#snippet right()}
      {#if debugState.enabled}
        <span
          class="text-[11px] text-(--pdf-reader-text,var(--color-text-auxiliary)) opacity-60 font-mono"
          >p{currentPage}/{totalPages} | {Math.round(scale * 100)}%</span
        >
      {/if}
      <Dropdown
        options={scaleDropdownOptions}
        value={String(scale)}
        class="min-w-[80px] ml-auto max-sm:ml-0"
        onchange={({ value }) => onSetScale(Number(value))}
      />
    {/snippet}
  </ReaderControls>
</div>
