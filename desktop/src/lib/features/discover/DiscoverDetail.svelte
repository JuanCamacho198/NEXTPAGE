<script lang="ts">
  import Modal from '$lib/shared/ui/layout/Modal.svelte';
  import type { MessageKey } from '$lib/shared/i18n/messages.en';
  import type { CatalogBook } from '$lib/shared/services/catalog';
  import type { DiscoverDetailStatus, DiscoverDownloadState } from './DiscoverDomainState.svelte';
  import {
    discoverDescription,
    discoverExternalLink,
    discoverFormatLabels,
  } from './discoverDetailFormat';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let {
    detail,
    detailStatus,
    t,
    onDismiss,
    downloadState = 'idle',
    downloadError = null,
    progressBytes = 0,
    progressTotal = null,
    onDownload = () => {},
    onCancelDownload = () => {},
    onRetryDownload = () => {},
  }: {
    detail: CatalogBook | null;
    detailStatus: DiscoverDetailStatus;
    t: Translate;
    onDismiss: () => void;
    downloadState?: DiscoverDownloadState;
    downloadError?: string | null;
    progressBytes?: number;
    progressTotal?: number | null;
    onDownload?: () => void;
    onCancelDownload?: () => void;
    onRetryDownload?: () => void;
  } = $props();

  /** Modal facade (mirrors ShelfDetailModal): `bind:open` + reset-on-close. */
  // svelte-ignore state_referenced_locally
  let open = $state(detailStatus !== 'closed');
  let coverFailed = $state(false);

  // Keep `open` in step with the domain status; a user close (Escape,
  // backdrop, close button) flows back through `onDismiss`.
  $effect(() => {
    open = detailStatus !== 'closed';
  });
  $effect(() => {
    if (!open && detailStatus !== 'closed') onDismiss();
  });
  // Reset-on-close: never show a stale cover fallback on reopen.
  $effect(() => {
    if (!open) coverFailed = false;
  });

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
  const description = $derived(detail ? discoverDescription(detail) : undefined);
  const formatLabels = $derived(detail?.formats ? discoverFormatLabels(detail.formats) : []);
  const externalLink = $derived(detail ? discoverExternalLink(detail) : null);
  const externalLabel = $derived(
    externalLink?.kind === 'openlibrary'
      ? t('discover.externalOpenLibrary')
      : t('discover.externalGutenberg'),
  );
  const showDownloadCta = $derived(
    detail?.downloadUrl !== null &&
      detail?.downloadUrl !== undefined &&
      detail.downloadUrl.trim() !== '',
  );
  const progressPct = $derived(
    progressTotal !== null && progressTotal > 0
      ? Math.min(100, Math.round((progressBytes / progressTotal) * 100))
      : null,
  );
</script>

<Modal bind:open title={detail?.title ?? t('sidebar.discover')} size="xl">
  {#snippet children()}
    {#if detailStatus === 'loading'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.loading')}</p>
    {:else if detailStatus === 'notFound'}
      <p class="text-sm text-(--color-text-muted)">{t('discover.detailNotFound')}</p>
    {:else if detailStatus === 'error' || detail === null}
      <p class="text-sm text-(--color-text-muted)">{t('discover.errorUpstream')}</p>
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
          <h3 class="truncate text-lg font-semibold text-(--color-primary)">{detail.title}</h3>
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
      </div>
      {#if description !== undefined}
        <p class="mt-3 text-sm text-(--color-text-muted)">{description}</p>
      {/if}
      {#if formatLabels.length > 0}
        <div class="mt-3 flex flex-wrap items-center gap-1.5">
          <span class="text-xs font-medium text-(--color-primary)">{t('discover.formats')}</span>
          {#each formatLabels as label (label)}
            <span
              class="inline-flex items-center rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-2 py-0.5 text-2xs font-medium text-(--color-secondary)"
            >
              {label}
            </span>
          {/each}
        </div>
      {/if}
      {#if externalLink !== null}
        <a
          href={externalLink.url}
          target="_blank"
          rel="noreferrer"
          class="mt-3 inline-flex text-sm text-(--color-primary) hover:underline"
        >
          {externalLabel}
        </a>
      {/if}
      {#if showDownloadCta}
        <div class="mt-4">
          {#if downloadState === 'idle'}
            <button
              type="button"
              class="inline-flex rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
              onclick={onDownload}
            >
              {t('discover.download')}
            </button>
          {:else if downloadState === 'downloading'}
            <p role="status" class="text-sm text-(--color-text-muted)">
              {t('discover.downloading')}
            </p>
            {#if progressPct !== null}
              <div
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progressPct}
                class="mt-2 h-1.5 w-full rounded-full bg-(--color-border)"
              >
                <div
                  class="h-1.5 rounded-full bg-(--color-primary) transition-all"
                  style="width: {progressPct}%"
                ></div>
              </div>
            {/if}
            <button
              type="button"
              class="mt-2 text-sm text-(--color-primary) hover:underline"
              onclick={onCancelDownload}
            >
              {t('discover.downloadCancel')}
            </button>
          {:else if downloadState === 'importing'}
            <p role="status" class="text-sm text-(--color-text-muted)">{t('discover.importing')}</p>
          {:else if downloadState === 'imported'}
            <p role="status" class="text-sm font-medium text-(--color-primary)">
              {t('discover.imported')}
            </p>
          {:else if downloadState === 'cancelled'}
            <p role="status" class="text-sm text-(--color-text-muted)">
              {t('discover.downloadCancelled')}
            </p>
            <button
              type="button"
              class="mt-2 rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
              onclick={onRetryDownload}
            >
              {t('discover.retry')}
            </button>
          {:else}
            <p role="alert" class="text-sm text-(--color-text-muted)">
              {t('discover.downloadFailed')}{#if downloadError !== null}
                {` (${downloadError})`}{/if}
            </p>
            <button
              type="button"
              class="mt-2 rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
              onclick={onRetryDownload}
            >
              {t('discover.retry')}
            </button>
          {/if}
        </div>
      {/if}
    {/if}
  {/snippet}
  {#snippet footer()}
    <button
      type="button"
      class="rounded-md border border-(--color-border) px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-surface-subtle)"
      onclick={onDismiss}
    >
      {t('discover.dismiss')}
    </button>
  {/snippet}
</Modal>
