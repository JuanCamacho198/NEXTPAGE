<script lang="ts">
  import type { CatalogBook } from '$lib/shared/services/catalog';
  import DiscoverCard from './DiscoverCard.svelte';
  import { DISCOVER_RAIL_LIMIT } from './DiscoverDomainState.svelte';

  let {
    title,
    books,
    onOpen,
  }: {
    title: string;
    books: CatalogBook[];
    onOpen: (id: string) => void;
  } = $props();

  /** Rails never over-render: at most 6 cards, short rails render as-is. */
  const visible = $derived(books.slice(0, DISCOVER_RAIL_LIMIT));
</script>

{#if visible.length > 0}
  <section aria-label={title} class="flex flex-col gap-3">
    <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{title}</h2>
    <div class="grid gap-3" style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))">
      {#each visible as book (book.id)}
        <DiscoverCard {book} {onOpen} />
      {/each}
    </div>
  </section>
{/if}
