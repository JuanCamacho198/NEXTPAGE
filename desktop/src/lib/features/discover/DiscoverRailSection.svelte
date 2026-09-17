<script lang="ts">
  import type { CatalogBook } from '$lib/shared/services/catalog';
  import type { MessageKey } from '$lib/shared/i18n/messages.en';
  import type { DiscoverRailState } from './DiscoverRailsDomainState.svelte';
  import { DISCOVER_RAIL_LIMIT } from './railPlan';
  import DiscoverCard from './DiscoverCard.svelte';
  import DiscoverRailError from './DiscoverRailError.svelte';
  import DiscoverSkeletonCard from './DiscoverSkeletonCard.svelte';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  /**
   * Per-rail renderer: owns its own Loading skeleton, Loaded grid (with the
   * "Ver todo" affordance) and Error + retry. `Hidden` and a chip filter that
   * matched nothing render no section at all — no placeholder header.
   */
  let {
    title,
    state,
    books = [],
    t,
    onOpen,
    onRetry,
    onViewAll,
  }: {
    title: string;
    state: DiscoverRailState;
    /** Already chip-filtered books; used only while the rail is `Loaded`. */
    books?: CatalogBook[];
    t: Translate;
    onOpen: (id: string) => void;
    onRetry: () => void;
    onViewAll: () => void;
  } = $props();

  /** Rails never over-render: at most 6 cards, short rails render as-is. */
  const visible = $derived(books.slice(0, DISCOVER_RAIL_LIMIT));
  /** Loading placeholder count mirrors the render limit. */
  const skeletonSlots = Array.from({ length: DISCOVER_RAIL_LIMIT }, (_, index) => index);
</script>

{#if state.kind === 'Loading'}
  <section aria-label={title} class="flex flex-col gap-3">
    <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{title}</h2>
    <div class="grid gap-3" style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))">
      {#each skeletonSlots as slot (slot)}
        <DiscoverSkeletonCard />
      {/each}
    </div>
  </section>
{:else if state.kind === 'Error'}
  <section aria-label={title} class="flex flex-col gap-3">
    <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{title}</h2>
    <DiscoverRailError code={state.code} {t} {onRetry} />
  </section>
{:else if visible.length > 0}
  <section aria-label={title} class="flex flex-col gap-3">
    <div class="flex items-baseline justify-between gap-3">
      <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{title}</h2>
      <button
        type="button"
        class="rounded-md px-2 py-1 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/10"
        onclick={onViewAll}
      >
        {t('discover.rail.viewAll')}
      </button>
    </div>
    <div class="grid gap-3" style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))">
      {#each visible as book (book.id)}
        <DiscoverCard {book} {onOpen} />
      {/each}
    </div>
  </section>
{/if}
