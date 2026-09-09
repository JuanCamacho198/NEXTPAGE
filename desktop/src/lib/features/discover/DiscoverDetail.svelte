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
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0">
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
