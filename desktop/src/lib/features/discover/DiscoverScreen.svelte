<script lang="ts">
  import {
    DISCOVER_RAIL_LIMIT,
    DISCOVER_RAIL_SPECS,
    discoverState,
    filterBooksByChip,
  } from './DiscoverDomainState.svelte';
  import type { MessageKey } from '$lib/shared/i18n/messages.en';
  import { navigationState } from '$lib/shared/stores/NavigationDomainState.svelte';
  import DiscoverCard from './DiscoverCard.svelte';
  import DiscoverDetail from './DiscoverDetail.svelte';
  import DiscoverHero from './DiscoverHero.svelte';
  import DiscoverOfflineState from './DiscoverOfflineState.svelte';
  import DiscoverRailSection from './DiscoverRailSection.svelte';
  import DiscoverSkeletonCard from './DiscoverSkeletonCard.svelte';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let { t }: { t: Translate } = $props();

  /** Live source count for the hero pill (`En línea · N fuentes`). */
  let sourceCount = $state(0);
  /** Selected trending chip; filters loaded rails client-side only (no catalog call). */
  let selectedChip = $state<string | null>(null);
  /** True once the first `refreshRails()` settled (success or fail-closed). */
  let railsReady = $state(false);

  async function loadRails(): Promise<void> {
    await discoverState.refreshRails();
    sourceCount = discoverState.refreshSources().length;
    railsReady = true;
  }

  // Browse-first load: rails resolve through `featured()`/`searchSource()`,
  // each rail failing closed to Hidden without affecting the others.
  $effect(() => {
    void loadRails();
  });

  // Search pagination follows the main-content scroller (this screen owns no
  // internal scroll container — `#main-content` is the single scroller).
  $effect(() => {
    const scroller = document.getElementById('main-content');
    if (!scroller) return;
    const onScroll = (): void => {
      if (discoverState.query.trim() === '') return;
      if (scroller.scrollTop + scroller.clientHeight >= scroller.scrollHeight - 240) {
        void discoverState.loadNextPage();
      }
    };
    scroller.addEventListener('scroll', onScroll);
    return () => scroller.removeEventListener('scroll', onScroll);
  });

  /** Search branch is active once a query produced non-idle search state. */
  const searchActive = $derived(
    discoverState.query.trim() !== '' && discoverState.status !== 'idle',
  );

  const showSearchGrid = $derived(
    searchActive && (discoverState.status === 'loaded' || discoverState.status === 'loadingMore'),
  );

  const railsLoading = $derived(
    !railsReady || discoverState.rails.some((rail) => rail.kind === 'Loading'),
  );

  /**
   * Visible rails in spec order. Hidden rails render nothing (fail-closed);
   * chip-filtered rails with zero matches collapse too. Pure `$derived`
   * over already-loaded books — zero catalog calls.
   */
  const visibleRails = $derived(
    DISCOVER_RAIL_SPECS.map((spec, index) => {
      const rail = discoverState.rails[index];
      if (!rail || rail.kind !== 'Loaded') return null;
      const books = filterBooksByChip(rail.books, selectedChip);
      if (books.length === 0) return null;
      return { title: spec.title, books };
    }).filter((rail) => rail !== null),
  );

  const showOffline = $derived(!discoverState.isOnline && visibleRails.length === 0);

  function submitSearch(query: string): void {
    discoverState.setQuery(query);
    void discoverState.searchFirstPage();
  }

  function retryRails(): void {
    railsReady = false;
    void loadRails();
  }
</script>

<!--
  Flow layout (`h-auto`, no `overflow-y-auto`): the AppRouter `#main-content`
  is the single scroller for the 1800px rail page; titlebar/sidebar stay fixed.
-->
<section aria-labelledby="discover-heading" class="flex h-auto flex-col gap-6">
  <DiscoverHero
    {t}
    {sourceCount}
    isOnline={discoverState.isOnline}
    {selectedChip}
    onSelectChip={(chip) => (selectedChip = chip)}
    onSearchSubmit={submitSearch}
    onNavigateHome={() => navigationState.navigateToHome()}
  />

  {#if discoverState.detailStatus !== 'closed'}
    <DiscoverDetail
      detail={discoverState.detail}
      detailStatus={discoverState.detailStatus}
      {t}
      onDismiss={() => discoverState.dismissDetail()}
    />
  {/if}

  {#if searchActive}
    <div class="flex flex-col gap-3">
      <h2 class="m-0 text-lg font-semibold text-(--color-primary)">
        {t('discover.searchResults')}
      </h2>
      {#if discoverState.status === 'loading'}
        <div
          class="grid gap-3"
          style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
        >
          {#each Array.from({ length: DISCOVER_RAIL_LIMIT }, (_, i) => i) as i (i)}
            <DiscoverSkeletonCard />
          {/each}
        </div>
      {:else if discoverState.status === 'empty'}
        <p class="text-sm text-(--color-text-muted)">{t('discover.empty')}</p>
      {:else if discoverState.status === 'offline' || discoverState.status === 'error'}
        <div class="flex flex-col items-start gap-2">
          <p class="text-sm text-(--color-text-muted)">
            {#if discoverState.errorCode === 'INVALID_PAGE'}{t('discover.errorInvalidPage')}
            {:else if discoverState.errorCode === 'NOT_FOUND'}{t('discover.errorNotFound')}
            {:else if discoverState.status === 'offline'}{t('discover.offline')}
            {:else}{t('discover.errorUpstream')}{/if}
          </p>
          <button
            type="button"
            class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
            onclick={() => void discoverState.retry()}
          >
            {t('discover.retry')}
          </button>
        </div>
      {:else if showSearchGrid}
        <div
          class="grid gap-3"
          style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
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
  {:else if railsLoading}
    {#each DISCOVER_RAIL_SPECS as spec (spec.title)}
      <section aria-label={spec.title} class="flex flex-col gap-3">
        <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{spec.title}</h2>
        <div
          class="grid gap-3"
          style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
        >
          {#each Array.from({ length: DISCOVER_RAIL_LIMIT }, (_, i) => i) as i (i)}
            <DiscoverSkeletonCard />
          {/each}
        </div>
      </section>
    {/each}
  {:else if showOffline}
    <DiscoverOfflineState {t} onRetry={retryRails} />
  {:else}
    {#each visibleRails as rail (rail.title)}
      <DiscoverRailSection
        title={rail.title}
        books={rail.books}
        onOpen={(id) => void discoverState.openDetail(id)}
      />
    {/each}
  {/if}
</section>
