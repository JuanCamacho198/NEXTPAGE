<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';
  import Icon, { type IconName } from '$lib/shared/ui/navigation/Icon.svelte';
  import type { DictionaryKpis } from '../dictionaryKpis';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    kpis: DictionaryKpis;
  };

  let { t, kpis }: Props = $props();

  const cards = $derived<{ key: MessageKey; icon: IconName; value: number }[]>([
    { key: 'dictionary.kpiTotal', icon: 'book-open', value: kpis.total },
    { key: 'dictionary.kpiThisWeek', icon: 'calendar-plus', value: kpis.thisWeek },
    { key: 'dictionary.kpiReferencedBooks', icon: 'library', value: kpis.referencedBooks },
  ]);
</script>

<div class="grid grid-cols-1 gap-3 sm:grid-cols-3" data-testid="dictionary-kpi-row">
  {#each cards as card (card.key)}
    <article
      class="flex items-center justify-between gap-3 rounded-lg border border-(--color-panel-border) bg-(--color-panel) p-4"
    >
      <div class="flex min-w-0 flex-col gap-1">
        <span
          class="truncate text-2xs font-semibold tracking-wide text-(--color-text-tertiary)"
          data-testid="dictionary-kpi-label"
        >
          {t(card.key)}
        </span>
        <span class="text-2xl font-extrabold text-(--color-primary)">{card.value}</span>
      </div>
      <div
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-(--color-accent-fill) text-(--color-accent-blue)"
      >
        <Icon name={card.icon} size="md" />
      </div>
    </article>
  {/each}
</div>
