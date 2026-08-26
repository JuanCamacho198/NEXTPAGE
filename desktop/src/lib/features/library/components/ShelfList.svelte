<script lang="ts">
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import SafeCover from './SafeCover.svelte';
  import ShelfBookActions from './ShelfBookActions.svelte';
  import { formatPercent, getSafeProgressPercentage, getStateLabel, type ShelfBook } from '$lib/features/library/utils';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    books: ShelfBook[];
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onImportBook?: () => void;
    onOpenBook?: (book: ShelfBook) => void;
    onContinueReading?: (book: ShelfBook) => void;
    onToggleFavorite?: (book: ShelfBook) => void;
    onStatusChange?: (book: ShelfBook, status: string) => void;
    onViewDetails?: (book: ShelfBook) => void;
    onRemoveBook?: (book: ShelfBook) => void;
  };

  let {
    books,
    t,
    onImportBook,
    onOpenBook,
    onContinueReading,
    onToggleFavorite,
    onStatusChange,
    onViewDetails,
    onRemoveBook,
  }: Props = $props();
</script>

<ul class="space-y-3 list-none p-0 m-0">
  {#each books as book}
    <li>
      <article
        class="flex flex-col gap-4 rounded-(--radius-xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(20,32,49,0.92),rgba(12,20,33,0.94))] p-4 shadow-(--shadow-panel) md:flex-row md:items-center"
      >
        <div class="flex items-start gap-4 md:min-w-0 md:flex-1">
          <div class="h-28 w-20 shrink-0 overflow-hidden rounded-[18px] bg-(--color-surface-subtle)">
            <SafeCover
              path={book.coverPath ?? ''}
              alt={`${t('library.cover')} ${book.title}`}
              className="h-full w-full object-cover"
            >
              {#snippet fallback()}
                <div
                  class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] px-2 text-center text-micro uppercase tracking-[0.16em] text-(--color-primary)"
                >
                  {t('shelf.noCover')}
                </div>
              {/snippet}
            </SafeCover>
          </div>

          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h3 class="line-clamp-1 text-base font-semibold text-(--color-primary)">
                {book.title}
              </h3>
              <span
                class="rounded-full border border-(--color-border) px-2 py-1 text-micro uppercase tracking-[0.12em] text-(--color-text-muted)"
              >
                {getStateLabel(book)}
              </span>
            </div>
            <p class="mt-1 text-sm text-(--color-text-muted)">
              {book.author || t('shelf.unknownAuthor')}
            </p>

            <div
              class="mt-4 max-w-xl space-y-2"
              role="progressbar"
              aria-valuenow={getSafeProgressPercentage(book)}
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div class="w-full h-2 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                <div class="h-full rounded-full bg-[var(--gradient-accent-h)]" style={`width: ${formatPercent(book)};`}></div>
              </div>
              <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-(--color-text-muted)">
                <span>{t('shelf.percentRead', { percent: formatPercent(book) })}</span>
                <span>{t('shelf.minutesLogged', { minutes: book.minutesRead })}</span>
                <span>{t('shelf.pageProgress', { current: book.currentPage, total: book.totalPages || '-' })}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2 md:justify-end">
          <Button variant="secondary" size="sm" class="rounded-xl whitespace-nowrap" onclick={() => onOpenBook?.(book)}
            >{t('shelf.read')}</Button
          >
          <Button
            size="sm"
            class="rounded-xl bg-(--gradient-accent) !text-[#07111d] whitespace-nowrap"
            onclick={() => onContinueReading?.(book)}
          >
            {getSafeProgressPercentage(book) > 0 ? t('shelf.continueReading') : t('shelf.startReading')}
          </Button>
          <ShelfBookActions {book} {t} {onOpenBook} {onToggleFavorite} {onStatusChange} {onViewDetails} {onRemoveBook} variant="list" />
        </div>
      </article>
    </li>
  {/each}

  <li>
    <button
      type="button"
      class="flex min-h-[120px] items-center justify-center gap-4 rounded-(--radius-xl) border border-dashed border-(--color-border-strong) bg-(--color-surface-subtle) p-6 text-left text-(--color-text-muted) transition hover:border-[rgba(78,140,255,0.5)] hover:text-(--color-primary)"
      onclick={onImportBook}
    >
      <div class="flex h-14 w-14 items-center justify-center rounded-full border border-(--color-border) bg-(--color-surface-subtle)">
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M12 5V19"></path>
          <path d="M5 12H19"></path>
        </svg>
      </div>
      <div>
        <p class="text-sm font-semibold text-(--color-primary)">{t('shelf.addBook')}</p>
        <p class="mt-1 text-xs">{t('shelf.importMoreDescription')}</p>
      </div>
    </button>
  </li>
</ul>
