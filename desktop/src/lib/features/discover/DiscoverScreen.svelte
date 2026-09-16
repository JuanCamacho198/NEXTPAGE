<script lang="ts">
  import { discoverState, filterBooksByChip } from './DiscoverDomainState.svelte';
  import { DISCOVER_RAIL_LIMIT } from './railPlan';
  import type { CatalogBook } from '$lib/shared/services/catalog';
  import type { MessageKey } from '$lib/shared/i18n/messages.en';
  import { navigationState } from '$lib/shared/stores/NavigationDomainState.svelte';
  import DiscoverCard from './DiscoverCard.svelte';
  import DiscoverDetail from './DiscoverDetail.svelte';
  import DiscoverHero from './DiscoverHero.svelte';
  import DiscoverOfflineState from './DiscoverOfflineState.svelte';
  import DiscoverRailSection from './DiscoverRailSection.svelte';
  import DiscoverSkeletonCard from './DiscoverSkeletonCard.svelte';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  /** One slot per rail index: Loading skeleton, Loaded section, or inline Error. */
  type RailView =
    | { index: number; kind: 'Loading'; title: string }
    | { index: number; kind: 'Loaded'; title: string; books: CatalogBook[] }
    | { index: number; kind: 'Error'; title: string; offline: boolean };

  let { t }: { t: Translate } = $props();

  /** Live source count for the hero pill (`En línea · N fuentes`). */
  let sourceCount = $state(0);
  /** Selected trending chip; filters loaded rails client-side only (no catalog call). */
  let selectedChip = $state<string | null>(null);

  async function loadRails(): Promise<void> {
    await discoverState.ensureRailsLoaded();
    sourceCount = discoverState.refreshSources().length;
  }

  // Browse-first load: every rail resolves through an explicit featured ordering
  // or a non-empty term search, and each rail fails without affecting the others.
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

  /**
   * Per-index rail views. Rendering is never gated on an all-rails-settled
   * condition: each index publishes its own slot, so rail order stays stable
   * while rails settle one by one. Hidden rails render nothing and a chip-filter
   * with zero matches collapses like a Hidden rail. Pure `$derived` over
   * already-loaded books — zero catalog calls.
   */
  const railViews = $derived.by<RailView[]>(() => {
    const specs = discoverState.railsState.specs;
    const views: RailView[] = [];
    for (const [index, rail] of discoverState.rails.entries()) {
      const spec = specs[index];
      if (!spec) continue;
      const title = t(spec.titleKey);
      if (rail.kind === 'Loading') {
        views.push({ index, kind: 'Loading', title });
      } else if (rail.kind === 'Error') {
        views.push({ index, kind: 'Error', title, offline: rail.offline });
      } else if (rail.kind === 'Loaded') {
        const books = filterBooksByChip(rail.books, selectedChip);
        if (books.length > 0) views.push({ index, kind: 'Loaded', title, books });
      }
    }
    return views;
  });

  const showOffline = $derived(
    !discoverState.isOnline && discoverState.rails.every((rail) => rail.kind !== 'Loaded'),
  );

  function submitSearch(query: string): void {
    discoverState.setQuery(query);
    void discoverState.searchFirstPage();
  }

  function retryRails(): void {
    void discoverState.refreshRails();
  }
</script>

<!--
  Flow layout (`h-auto`, no `overflow-y-auto`): the AppRouter `#main-content`
  is the single scroller for the rail page; titlebar/sidebar stay fixed.
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
      downloadState={discoverState.downloadState}
      downloadError={discoverState.downloadError}
      progressBytes={discoverState.progressBytes}
      progressTotal={discoverState.progressTotal}
      {t}
      onDismiss={() => discoverState.dismissDetail()}
      onDownload={() => void discoverState.startDownload()}
      onCancelDownload={() => discoverState.cancelDownload()}
      onRetryDownload={() => void discoverState.retryDownload()}
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
  {:else if showOffline}
    <DiscoverOfflineState {t} onRetry={retryRails} />
  {:else}
    {#each railViews as view (view.index)}
      {#if view.kind === 'Loading'}
        <section aria-label={view.title} class="flex flex-col gap-3">
          <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{view.title}</h2>
          <div
            class="grid gap-3"
            style="grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))"
          >
            {#each Array.from({ length: DISCOVER_RAIL_LIMIT }, (_, i) => i) as i (i)}
              <DiscoverSkeletonCard />
            {/each}
          </div>
        </section>
      {:else if view.kind === 'Loaded'}
        <DiscoverRailSection
          title={view.title}
          books={view.books}
          onOpen={(id) => void discoverState.openDetail(id)}
        />
      {:else}
        <section aria-label={view.title} class="flex flex-col gap-3">
          <h2 class="m-0 text-lg font-semibold text-(--color-primary)">{view.title}</h2>
          <div class="flex flex-col items-start gap-2">
            <p class="m-0 text-sm text-(--color-text-muted)">
              {view.offline ? t('discover.offline') : t('discover.errorUpstream')}
            </p>
            <button
              type="button"
              class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
              onclick={() => void discoverState.retryRail(view.index)}
            >
              {t('discover.retry')}
            </button>
          </div>
        </section>
      {/if}
    {/each}
  {/if}
</section>
