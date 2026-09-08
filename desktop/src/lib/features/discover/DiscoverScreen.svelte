<script lang="ts">
  import { discoverState } from './DiscoverDomainState.svelte';
  import DiscoverCard from './DiscoverCard.svelte';
  import DiscoverDetail from './DiscoverDetail.svelte';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let { t }: { t: Translate } = $props();

  let searchInput = $state('');

  const showGrid = $derived(
    discoverState.status === 'loaded' || discoverState.status === 'loadingMore',
  );

  function submitSearch(): void {
    discoverState.setQuery(searchInput);
    void discoverState.searchFirstPage();
  }

  function onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      submitSearch();
    }
  }

  function onGridScroll(event: Event): void {
    const el = event.currentTarget as HTMLElement;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 240) {
      void discoverState.loadNextPage();
    }
  }
</script>

<section aria-labelledby="discover-heading" class="flex h-full flex-col gap-3 overflow-hidden">
  <h1 id="discover-heading" class="m-0 text-xl font-semibold">{t('sidebar.discover')}</h1>
  <form class="flex gap-2" onsubmit={(e) => { e.preventDefault(); submitSearch(); }}>
    <input
      type="search"
      bind:value={searchInput}
      placeholder={t('discover.searchPlaceholder')}
      aria-label={t('discover.searchAriaLabel')}
      class="flex-1 rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) focus:border-(--color-primary) focus:outline-none"
      onkeydown={onSearchKeydown}
    />
    <button
      type="submit"
      class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-2 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
    >
      {t('discover.search')}
    </button>
  </form>

  {#if discoverState.detailStatus !== 'closed'}
    <DiscoverDetail
      detail={discoverState.detail}
      detailStatus={discoverState.detailStatus}
      {t}
      onDismiss={() => discoverState.dismissDetail()}
    />
  {/if}

  <div class="min-h-0 flex-1 overflow-y-auto">
    {#if discoverState.status === 'idle'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.idle')}</p>
    {:else if discoverState.status === 'loading'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.loading')}</p>
    {:else if discoverState.status === 'empty'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.empty')}</p>
    {:else if discoverState.status === 'offline' || discoverState.status === 'error'}
      <div class="flex flex-col items-start gap-2">
        <p class="text-sm text-(--color-text-muted)">
          {discoverState.status === 'offline' ? t('discover.offline') : t('discover.errorUpstream')}
        </p>
        <button
          type="button"
          class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
          onclick={() => void discoverState.retry()}
        >
          {t('discover.retry')}
        </button>
      </div>
    {:else if showGrid}
      <div
        class="grid gap-3"
        style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
        onscroll={onGridScroll}
      >
        {#each discoverState.books as book (book.id)}
          <DiscoverCard {book} onOpen={(id) => void discoverState.openDetail(id)} />
        {/each}
      </div>
      {#if discoverState.status === 'loadingMore'}
        <p class="mt-3 text-xs text-(--color-text-muted)">{t('discover.loadingMore')}</p>
      {:else if discoverState.nextPage === null}
        <p class="mt-3 text-xs text-(--color-text-muted)">{t('discover.endOfResults')}</p>
      {/if}
    {/if}
  </div>
</section>
