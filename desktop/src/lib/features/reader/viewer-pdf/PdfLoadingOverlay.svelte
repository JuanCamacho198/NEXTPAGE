<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    isLoading: boolean;
    error: string | null;
    loadProgress: number;
    loadProgressMax: number;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { isLoading, error, loadProgress, loadProgressMax, t }: Props = $props();
</script>

{#if isLoading}
  <div class="absolute inset-0 z-10 flex items-center justify-center bg-(--color-background)">
    {#if loadProgressMax > 0 && loadProgress < loadProgressMax}
      <div class="flex flex-col items-center gap-2 min-w-60">
        <span class="text-sm text-(--pdf-reader-text,var(--color-text-auxiliary))"
          >{t('pdf.loading')}</span
        >
        <div
          class="w-full h-1.5 bg-(--color-border,var(--color-surface-strong)) rounded-full overflow-hidden"
        >
          <div
            class="h-full bg-sky-400 rounded-full transition-[width] duration-150 ease-in"
            style="width: {Math.round((loadProgress / loadProgressMax) * 100)}%"
          ></div>
        </div>
        <span class="text-xs text-(--pdf-reader-text,var(--color-text-auxiliary)) opacity-80"
          >{Math.round((loadProgress / loadProgressMax) * 100)}%</span
        >
      </div>
    {:else}
      {t('pdf.loading')}
    {/if}
  </div>
{/if}
{#if error}
  <div
    class="absolute inset-0 z-10 flex items-center justify-center bg-(--color-background) text-red-600 text-sm"
  >
    {t('pdf.error')}: {error}
  </div>
{/if}
