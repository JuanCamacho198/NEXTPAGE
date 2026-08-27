<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { libraryState } from '$lib/shared/stores/LibraryDomainState.svelte';
  import { navigationState } from '$lib/shared/stores/NavigationDomainState.svelte';
  import { BookCard, ShelfActionMenu } from '$lib/features/library';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';

  let scrollEl = $state<HTMLUListElement | null>(null);
  let canScrollLeft = $state(false);
  let canScrollRight = $state(false);

  const showArrows = $derived(libraryState.continueReadingBooks.length > 2);

  function updateScrollState(): void {
    if (!scrollEl) {
      canScrollLeft = false;
      canScrollRight = false;
      return;
    }
    canScrollLeft = scrollEl.scrollLeft > 2;
    canScrollRight = scrollEl.scrollLeft + scrollEl.clientWidth < scrollEl.scrollWidth - 2;
  }

  function scrollByOffset(offset: number): void {
    scrollEl?.scrollBy({ left: offset, behavior: 'smooth' });
  }

  $effect(() => {
    void libraryState.continueReadingBooks.length;
    queueMicrotask(updateScrollState);
  });

  $effect(() => {
    if (!scrollEl) return;
    const el = scrollEl;
    const ro = new ResizeObserver(() => updateScrollState());
    ro.observe(el);
    for (const child of Array.from(el.children)) {
      ro.observe(child);
    }
    updateScrollState();
    return () => ro.disconnect();
  });
</script>

{#if libraryState.continueReadingBooks.length === 0}
  <!-- empty -->
{:else}
  <div class="relative">
    {#if showArrows}
      <button
        type="button"
        class="absolute left-0 top-1/2 z-10 -translate-y-1/2 rounded-full border border-(--color-border) bg-(--color-surface) p-1.5 shadow-md transition-opacity hover:bg-(--color-surface-hover) disabled:cursor-not-allowed disabled:opacity-35"
        aria-label="Scroll left"
        disabled={!canScrollLeft}
        onclick={() => scrollByOffset(-320)}
      >
        <Icon name="chevron-left" size="sm" />
      </button>
      <button
        type="button"
        class="absolute right-0 top-1/2 z-10 -translate-y-1/2 rounded-full border border-(--color-border) bg-(--color-surface) p-1.5 shadow-md transition-opacity hover:bg-(--color-surface-hover) disabled:cursor-not-allowed disabled:opacity-35"
        aria-label="Scroll right"
        disabled={!canScrollRight}
        onclick={() => scrollByOffset(320)}
      >
        <Icon name="chevron-right" size="sm" />
      </button>
    {/if}

    <ul
      bind:this={scrollEl}
      onscroll={updateScrollState}
      class="flex gap-3 overflow-x-auto overflow-y-hidden snap-x snap-mandatory scroll-smooth [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden {showArrows
        ? 'px-8'
        : ''}"
      style="scrollbar-width: none; -ms-overflow-style: none;"
    >
      {#each libraryState.continueReadingBooks as book (book.id)}
        <li class="snap-start shrink-0 w-[320px] max-w-[85%]">
          <BookCard
            {book}
            variant="continue-reading"
            compact={libraryState.continueReadingBooks.length > 1}
            selected={navigationState.previewBookId === book.id}
            onSelect={() => {
              navigationState.openShelfDetails(book.id);
            }}
            onRead={() => {
              void appState.startReading(book);
            }}
            t={appState.t}
          >
            {#snippet actions()}
              <ShelfActionMenu
                bookId={book.id}
                isFavorite={Boolean(book.collectionIds?.includes(1))}
                readLabel={appState.t('app.read')}
                editLabel={appState.t('library.editMetadata.title')}
                removeLabel={appState.t('library.removeFromShelf')}
                favoriteAddLabel={appState.t('library.favoriteAdd')}
                favoriteRemoveLabel={appState.t('library.favoriteRemove')}
                triggerLabel={appState.t('library.optionsFor', { title: book.title })}
                onViewDetails={() => navigationState.openShelfDetails(book.id)}
                viewDetailsLabel={appState.t('shelf.viewDetails')}
                onRead={() => {
                  void appState.startReading(book);
                }}
                onEdit={() => {
                  libraryState.handleEditBook(book);
                }}
                onRemove={() => {
                  libraryState.pendingRemoveBook = book;
                }}
                onToggleFavorite={() => {
                  void libraryState.handleToggleFavorite(book);
                }}
              />
            {/snippet}
          </BookCard>
        </li>
      {/each}
    </ul>
  </div>
{/if}
