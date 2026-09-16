<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n/messages.en';
  import { TRENDING_CHIPS } from './DiscoverDomainState.svelte';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let {
    t,
    sourceCount,
    isOnline,
    selectedChip,
    onSelectChip,
    onSearchSubmit,
    onNavigateHome,
  }: {
    t: Translate;
    sourceCount: number;
    isOnline: boolean;
    selectedChip: string | null;
    onSelectChip: (chip: string | null) => void;
    onSearchSubmit: (query: string) => void;
    onNavigateHome: () => void;
  } = $props();

  let searchInput = $state('');

  function submit(event: Event): void {
    event.preventDefault();
    onSearchSubmit(searchInput);
  }

  function toggleChip(chip: string): void {
    onSelectChip(selectedChip === chip ? null : chip);
  }
</script>

<div class="flex flex-col gap-4">
  <nav aria-label="Breadcrumb" class="flex items-center gap-1.5 text-sm">
    <button
      type="button"
      class="text-(--color-text-muted) transition-colors hover:text-(--color-primary) hover:underline"
      onclick={onNavigateHome}
    >
      {t('sidebar.home')}
    </button>
    <span aria-hidden="true" class="text-(--color-text-muted)">/</span>
    <span aria-current="page" class="font-medium text-(--color-primary)">
      {t('sidebar.discover')}
    </span>
  </nav>

  <div class="flex flex-wrap items-start justify-between gap-3">
    <div class="flex min-w-0 flex-col gap-1">
      <h1 id="discover-heading" class="m-0 text-xl font-semibold text-(--color-primary)">
        {t('discover.heroTitle')}
      </h1>
      <p class="m-0 text-sm text-(--color-text-muted)">{t('discover.heroSubtitle')}</p>
    </div>
    {#if isOnline}
      <span
        role="status"
        class="shrink-0 rounded-full border border-(--color-border) bg-(--color-accent-soft) px-3 py-1 text-xs font-medium text-(--color-secondary)"
      >
        {t('discover.onlineSources', { count: sourceCount })}
      </span>
    {:else}
      <span
        role="status"
        class="shrink-0 rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1 text-xs font-medium text-(--color-text-muted)"
      >
        {t('discover.offlineLabel')}
      </span>
    {/if}
  </div>

  <form class="flex gap-2" onsubmit={submit}>
    <input
      type="search"
      bind:value={searchInput}
      placeholder={t('discover.searchPlaceholder')}
      aria-label={t('discover.searchAriaLabel')}
      class="flex-1 rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) focus:border-(--color-primary) focus:outline-none"
    />
    <button
      type="submit"
      class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-2 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
    >
      {t('discover.search')}
    </button>
  </form>

  <div role="group" aria-label={t('discover.trending')} class="flex flex-wrap gap-2">
    {#each TRENDING_CHIPS as chip (chip)}
      <button
        type="button"
        aria-pressed={selectedChip === chip}
        onclick={() => toggleChip(chip)}
        class="rounded-full border px-3 py-1 text-xs font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-(--color-primary)/50 {selectedChip ===
        chip
          ? 'border-(--color-primary)/40 bg-(--color-primary)/12 text-(--color-primary)'
          : 'border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted) hover:border-(--color-primary)/40 hover:text-(--color-primary)'}"
      >
        {chip}
      </button>
    {/each}
  </div>
</div>
