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

<ul class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4 2xl:grid-cols-5 list-none p-0 m-0">
  {#each books as book}
    <li>
      <article
        class="group flex min-h-[360px] flex-col rounded-(--radius-xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(20,32,49,0.92),rgba(12,20,33,0.94))] p-4 shadow-(--shadow-panel)"
      >
        <div class="mb-3 flex items-start justify-between gap-3">
          <span
            class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-2.5 py-1 text-(--text-micro) uppercase tracking-[0.16em] text-(--color-text-muted)"
          >
            {getStateLabel(book)}
          </span>
          <ShelfBookActions {book} {t} {onOpenBook} {onToggleFavorite} {onStatusChange} {onViewDetails} {onRemoveBook} variant="grid" />
        </div>

        <div class="relative mb-4 aspect-[0.72] overflow-hidden rounded-[20px] bg-(--color-surface-subtle)">
          <SafeCover path={book.coverPath ?? ''} alt={`Portada de ${book.title}`} className="h-full w-full object-cover">
            {#snippet fallback()}
              <div
                class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] px-6 text-center text-xs uppercase tracking-[0.18em] text-(--color-primary)"
              >
                {t('shelf.noCover')}
              </div>
            {/snippet}
          </SafeCover>
        </div>

        <div class="space-y-1">
          <h3 class="line-clamp-2 text-sm font-semibold text-(--color-primary)">
            {book.title}
          </h3>
          <p class="line-clamp-1 text-xs text-(--color-text-muted)">
            {book.author || t('shelf.unknownAuthor')}
          </p>
        </div>

        <div
          class="mt-4 space-y-2"
          role="progressbar"
          aria-valuenow={getSafeProgressPercentage(book)}
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div class="w-full h-2 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
            <div class="h-full rounded-full bg-[var(--gradient-accent-h)]" style={`width: ${formatPercent(book)};`}></div>
          </div>
          <div class="flex items-center justify-between text-xs text-(--color-text-muted)">
            <span>{t('shelf.percentRead', { percent: formatPercent(book) })}</span>
            <span>{book.minutesRead} {t('library.min')}</span>
          </div>
        </div>

        <div class="mt-auto grid grid-cols-2 gap-2 pt-4">
          <Button variant="secondary" size="sm" class="rounded-xl whitespace-nowrap" onclick={() => onOpenBook?.(book)}>
            {t('shelf.openBook')}
          </Button>
          <Button
            size="sm"
            class="rounded-xl bg-(--gradient-accent) !text-[#07111d] whitespace-nowrap"
            onclick={() => onContinueReading?.(book)}
          >
            {getSafeProgressPercentage(book) > 0 ? t('app.continue') : t('shelf.start')}
          </Button>
        </div>
      </article>
    </li>
  {/each}

  <li>
    <button
      type="button"
      class="flex min-h-[360px] flex-col items-center justify-center gap-4 rounded-(--radius-xl) border border-dashed border-(--color-border-strong) bg-(--color-surface-subtle) p-6 text-center text-(--color-text-muted) transition hover:border-[rgba(78,140,255,0.5)] hover:text-(--color-primary)"
      onclick={onImportBook}
    >
      <div class="flex h-16 w-16 items-center justify-center rounded-full border border-(--color-border) bg-(--color-surface-subtle)">
        <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M12 5V19"></path>
          <path d="M5 12H19"></path>
        </svg>
      </div>
      <div>
        <p class="text-sm font-semibold text-(--color-primary)">{t('shelf.addBook')}</p>
        <p class="mt-1 text-xs">{t('shelf.importDescription')}</p>
      </div>
    </button>
  </li>
</ul>
