<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { libraryState } from '$lib/shared/stores/LibraryDomainState.svelte';
  import { navigationState } from '$lib/shared/stores/NavigationDomainState.svelte';
  import { BookCard, ShelfActionMenu } from '$lib/features/library';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';

  let scrollEl = $state<HTMLUListElement | null>(null);
  let canScrollLeft = $state(false);
  let canScrollRight = $state(false);

  const showArrows = $derived(libraryState.continueReadingBooks.length > 1);

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

  function handleNext(): void {
    if (!scrollEl) return;
    if (!canScrollRight) {
      scrollEl.scrollTo({ left: 0, behavior: 'smooth' });
    } else {
      scrollByOffset(scrollEl.clientWidth);
    }
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
        class="absolute right-2 top-[40%] z-10 -translate-y-1/2 rounded-full border border-(--color-border) bg-(--color-surface) p-1.5 shadow-md transition hover:bg-(--color-surface-hover)"
        aria-label="Scroll right"
        onclick={handleNext}
      >
        <Icon name="chevron-right" size="sm" />
      </button>
    {/if}

    <ul
      bind:this={scrollEl}
      onscroll={updateScrollState}
      class="flex gap-0 overflow-x-auto overflow-y-hidden snap-x snap-mandatory scroll-smooth [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
      style="scrollbar-width: none; -ms-overflow-style: none;"
    >
      {#each libraryState.continueReadingBooks as book (book.id)}
        <li class="snap-start shrink-0 w-full">
          <BookCard
            {book}
            variant="continue-reading"
            compact={false}
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
