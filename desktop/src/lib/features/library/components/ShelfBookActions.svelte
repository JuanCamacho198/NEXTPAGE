<script lang="ts">
  import DropMenu from '$lib/shared/ui/navigation/DropMenu.svelte';
  import { FAVORITES_COLLECTION_ID, type ShelfBook } from '$lib/features/library/utils';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    book: ShelfBook;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onOpenBook?: (book: ShelfBook) => void;
    onToggleFavorite?: (book: ShelfBook) => void;
    onStatusChange?: (book: ShelfBook, status: string) => void;
    onViewDetails?: (book: ShelfBook) => void;
    onRemoveBook?: (book: ShelfBook) => void;
    variant?: 'grid' | 'list';
  };

  let {
    book,
    t,
    onOpenBook,
    onToggleFavorite,
    onStatusChange,
    onViewDetails,
    onRemoveBook,
    variant = 'grid',
  }: Props = $props();

  const isFavorite = $derived(Boolean(book.collectionIds?.includes(FAVORITES_COLLECTION_ID)));

  const triggerClass = $derived(
    variant === 'list'
      ? 'flex h-10 w-10 items-center justify-center rounded-xl border border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted)'
      : 'flex h-9 w-9 items-center justify-center rounded-xl border border-(--color-border) bg-[rgba(20,32,49,0.92)] text-(--color-text-muted)',
  );
</script>

<DropMenu position="bottom-right">
  {#snippet trigger()}
    <button type="button" class={triggerClass} aria-label={t('shelf.bookOptions', { title: book.title })}>
      <svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
        <circle cx="5" cy="12" r="1.8"></circle>
        <circle cx="12" cy="12" r="1.8"></circle>
        <circle cx="19" cy="12" r="1.8"></circle>
      </svg>
    </button>
  {/snippet}
  <button
    class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
    onclick={() => onOpenBook?.(book)}>{t('shelf.openBook')}</button
  >
  <button
    class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
    onclick={() => onToggleFavorite?.(book)}
  >
    {isFavorite ? t('shelf.removeFavorite') : t('shelf.markFavorite')}
  </button>
  <button
    class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
    onclick={() => onStatusChange?.(book, 'completed')}>{t('shelf.markCompleted')}</button
  >
  <button
    class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
    onclick={() => onViewDetails?.(book)}>{t('shelf.viewDetails')}</button
  >
  <button
    class="w-full px-4 py-2.5 text-left text-sm text-(--color-danger) hover:bg-[rgba(255,255,255,0.08)]"
    onclick={() => onRemoveBook?.(book)}>{t('shelf.removeLibrary')}</button
  >
</DropMenu>
