<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n/messages.en';
  import type { CatalogBook } from '$lib/shared/services/catalog';
  import type { DiscoverDetailStatus } from './DiscoverDomainState.svelte';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let {
    detail,
    detailStatus,
    t,
    onDismiss,
  }: {
    detail: CatalogBook | null;
    detailStatus: DiscoverDetailStatus;
    t: Translate;
    onDismiss: () => void;
  } = $props();

  let coverFailed = $state(false);

  // Reset the cover fallback whenever a different book loads.
  let lastDetailId: string | null = $state(null);
  $effect(() => {
    const id = detail?.id ?? null;
    if (id !== lastDetailId) {
      lastDetailId = id;
      coverFailed = false;
    }
  });

  /** Short source attribution for the cover badge (mirrors DiscoverCard). */
  function badgeLabel(provider: string): string {
    if (provider === 'builtin:gutendex') return 'Gutenberg';
    if (provider === 'builtin:openlibrary') return 'Open Library';
    if (provider === 'curated') return 'Curated';
    if (provider.startsWith('addon:')) return 'Add-on';
    return provider;
  }

  const showCover = $derived(
    detail !== null && detail.coverUrl !== null && detail.coverUrl !== undefined && !coverFailed,
  );
  const showDescription = $derived(
    detail?.description !== null &&
      detail?.description !== undefined &&
      detail.description.trim() !== '',
  );
</script>

{#if detailStatus !== 'closed'}
  <div
    class="rounded-lg border border-(--color-border) bg-(--color-surface-subtle) p-4"
    role="dialog"
    aria-label={t('sidebar.discover')}
  >
    {#if detailStatus === 'loading'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.loading')}</p>
    {:else if detailStatus === 'notFound'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.detailNotFound')}</p>
      <button
        type="button"
        class="mt-2 text-sm text-(--color-primary) hover:underline"
        onclick={onDismiss}>{t('discover.dismiss')}</button
      >
    {:else if detailStatus === 'error' || detail === null}
      <p class="text-sm text-(--color-text-muted)">{t('discover.errorUpstream')}</p>
      <button
        type="button"
        class="mt-2 text-sm text-(--color-primary) hover:underline"
        onclick={onDismiss}>{t('discover.dismiss')}</button
      >
    {:else}
      <div class="flex items-start gap-4">
        <div
          class="relative aspect-2/3 w-24 shrink-0 overflow-hidden rounded-md bg-gradient-to-br from-(--color-primary)/8 to-(--color-primary)/3"
        >
          {#if showCover && detail !== null}
            <img
              src={detail.coverUrl ?? ''}
              alt={detail.title}
              loading="lazy"
              decoding="async"
              class="h-full w-full object-cover"
              onerror={() => (coverFailed = true)}
            />
          {:else if detail !== null}
            <div class="flex h-full w-full items-center justify-center">
              <span class="text-3xl font-bold text-(--color-primary)/30"
                >{detail.title.trim()[0]?.toUpperCase() || '?'}</span
              >
            </div>
          {/if}
          {#if detail !== null}
            <span
              class="absolute top-1.5 left-1.5 rounded-full border border-(--color-border) bg-(--color-background)/85 px-2 py-0.5 text-2xs font-medium text-(--color-secondary)"
            >
              {badgeLabel(detail.provider)}
            </span>
          {/if}
        </div>
        <div class="min-w-0 flex-1">
          <h2 class="truncate text-lg font-semibold text-(--color-primary)">{detail.title}</h2>
          <p class="text-sm text-(--color-text-muted)">
            {t('discover.byAuthors', { authors: detail.authors.join(', ') })}
          </p>
          {#if detail.languages.length > 0}
            <p class="mt-1 text-xs text-(--color-text-muted)">
              <span class="font-medium text-(--color-primary)">{t('discover.languages')}</span>
              {detail.languages.join(', ')}
            </p>
          {/if}
          {#if detail.subjects.length > 0}
            <p class="mt-1 text-xs text-(--color-text-muted)">
              <span class="font-medium text-(--color-primary)">{t('discover.subjects')}</span>
              {detail.subjects.slice(0, 8).join(', ')}
            </p>
          {/if}
        </div>
        <button
          type="button"
          class="shrink-0 text-sm text-(--color-primary) hover:underline"
          onclick={onDismiss}>{t('discover.dismiss')}</button
        >
      </div>
      {#if showDescription && detail !== null}
        <p class="mt-3 text-sm text-(--color-text-muted)">{detail.description ?? ''}</p>
      {/if}
      {#if detail.downloadUrl !== null}
        <a
          href={detail.downloadUrl}
          target="_blank"
          rel="noreferrer"
          class="mt-3 inline-flex rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
        >
          {t('discover.download')}
        </a>
      {/if}
    {/if}
  </div>
{/if}
