<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';
  import type { PdfOutlineItem } from '$lib/shared/types';

  type FlatOutlineItem = {
    item: PdfOutlineItem;
    depth: number;
  };

  type Props = {
    flatOutline: FlatOutlineItem[];
    tocLoading: boolean;
    tocError: string | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onNavigate: (item: PdfOutlineItem) => void;
  };

  let { flatOutline, tocLoading, tocError, t, onNavigate }: Props = $props();
</script>

<aside
  class="w-60 bg-(--pdf-reader-surface-bg,var(--color-surface)) border-r border-(--color-border) overflow-y-auto shrink-0 max-sm:w-[min(240px,70vw)]"
>
  <h3
    class="m-0 px-3 py-3 text-sm font-semibold border-b border-(--color-border) text-(--pdf-reader-text,var(--color-primary))"
  >
    {t('pdf.tableOfContents')}
  </h3>
  {#if tocLoading}
    <p class="m-0 px-3 py-3 text-xs text-(--color-text-muted)">{t('pdf.tocLoading')}</p>
  {:else if tocError}
    <p class="m-0 px-3 py-3 text-xs text-red-600">{tocError}</p>
  {:else if flatOutline.length === 0}
    <p class="m-0 px-3 py-3 text-xs text-(--color-text-muted)">{t('pdf.tocEmpty')}</p>
  {:else}
    <ul class="m-0 p-0 list-none">
      {#each flatOutline as entry (entry.item.id)}
        <li>
          <button
            type="button"
            onclick={() => onNavigate(entry.item)}
            disabled={!entry.item.dest}
            style="padding-left: calc(12px + (var(--toc-depth, 0) * 16px));"
            class="w-full px-3 py-2.5 border-none bg-transparent text-left cursor-pointer text-xs leading-[1.4] wrap-break-word text-(--pdf-reader-text,var(--color-primary)) disabled:opacity-55 disabled:cursor-default hover:not-disabled:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]"
          >
            {entry.item.title}
          </button>
        </li>
      {/each}
    </ul>
  {/if}
</aside>
