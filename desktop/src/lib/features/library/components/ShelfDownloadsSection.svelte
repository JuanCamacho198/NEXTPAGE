<script lang="ts">
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import SafeCover from './SafeCover.svelte';
  import { downloadableCatalog, downloadBook } from '$lib/stores/downloadableCatalog.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onDownloaded?: () => void;
  };

  let { t, onDownloaded }: Props = $props();

  let downloadErrorDismissed = $state(false);

  $effect(() => {
    if (downloadableCatalog.error) downloadErrorDismissed = false;
  });

  $effect(() => {
    void downloadableCatalog.loadAvailableFromDrive();
  });

  async function handleDownload(bookId: string): Promise<void> {
    try {
      await downloadBook(bookId);
    } catch {
      return;
    }
    if (downloadableCatalog.error || downloadableCatalog.books.some((b) => b.id === bookId)) return;
    onDownloaded?.();
  }
</script>

{#if downloadableCatalog.books.length > 0}
  <section
    class="mt-6 rounded-(--radius-2xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(78,140,255,0.08),rgba(12,20,33,0.94))] p-4 shadow-(--shadow-section)"
  >
    <header class="mb-3 flex items-center justify-between">
      <h2 class="text-sm font-semibold text-(--color-primary)">
        {t('shelf.availableDevices')}
        <span class="ml-2 rounded-full bg-(--color-primary)/20 px-2 py-0.5 text-micro text-(--color-primary)">
          {downloadableCatalog.count}
        </span>
      </h2>
    </header>

    {#if downloadableCatalog.error && !downloadErrorDismissed}
      <div
        class="mb-3 flex items-start gap-2 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-400"
      >
        <span class="flex-1">{downloadableCatalog.error}</span>
        <button
          type="button"
          class="shrink-0 text-red-400 hover:text-red-300"
          onclick={() => {
            downloadErrorDismissed = true;
            downloadableCatalog.clearDownloadError();
          }}
          aria-label={t('shelf.closeAria')}
        >
          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6l12 12"></path>
          </svg>
        </button>
      </div>
    {/if}

    <ul class="space-y-3 list-none p-0 m-0">
      {#each downloadableCatalog.books as row}
        <li>
          <article
            class="flex items-start gap-3 rounded-(--radius-xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(20,32,49,0.92),rgba(12,20,33,0.94))] p-3 shadow-(--shadow-panel)"
          >
            <div class="h-20 w-14 shrink-0 overflow-hidden rounded-[18px] bg-(--color-surface-subtle)">
              <SafeCover
                path={row.coverUrl ?? ''}
                alt={`Portada de ${row.displayTitle}`}
                className="h-full w-full object-cover"
              >
                {#snippet fallback()}
                  <div
                    class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] text-micro uppercase tracking-[0.16em] text-(--color-primary)"
                  >
                    {row.ext.toUpperCase()}
                  </div>
                {/snippet}
              </SafeCover>
            </div>

            <div class="min-w-0 flex-1">
              <h3 class="line-clamp-1 text-sm font-semibold text-(--color-primary)">
                {row.displayTitle}
              </h3>
              <p class="mt-0.5 line-clamp-1 text-xs text-(--color-text-muted)">
                {row.author || t('shelf.unknownAuthor')}
              </p>
              <div class="mt-1 flex flex-wrap items-center gap-2">
                <span
                  class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-2 py-0.5 text-micro uppercase tracking-[0.08em] text-(--color-text-muted)"
                >
                  {row.ext.toUpperCase()}
                </span>
              </div>
            </div>

            <div class="shrink-0">
              {#if downloadableCatalog.isDownloading.has(row.id)}
                <div class="flex items-center gap-2">
                  <svg class="h-4 w-4 animate-spin text-(--color-primary)" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  <span class="text-xs text-(--color-text-muted)">{t('shelf.downloading')}</span>
                </div>
              {:else}
                <Button
                  size="sm"
                  class="rounded-xl bg-(--color-primary) !text-(--color-background) whitespace-nowrap"
                  onclick={() => handleDownload(row.id)}
                >
                  {t('shelf.download')}
                </Button>
              {/if}
            </div>
          </article>
        </li>
      {/each}
    </ul>
  </section>
{/if}
