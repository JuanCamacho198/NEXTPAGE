<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { BookCard, ShelfActionMenu } from '$lib/features/library';
</script>

{#if appState.continueReadingBooks.length === 0}
  <p class="text-sm text-(--color-text-muted)">{appState.t('home.continueReadingPlaceholder')}</p>
{:else if appState.continueReadingBooks.length === 1}
  {@const book = appState.continueReadingBooks[0]}
  <BookCard
    {book}
    variant="continue-reading"
    selected={appState.previewBookId === book.id}
    onSelect={() => {
      appState.openShelfDetails(book);
    }}
    onRead={() => {
      void appState.startReading(book);
    }}
    t={appState.t}
  >
    {#snippet actions()}
      <ShelfActionMenu
        bookId={book.id}
        isFavorite={Boolean(book.isFavorite)}
        readLabel={appState.t('app.read')}
        editLabel={appState.t('library.editMetadata.title')}
        removeLabel={appState.t('library.removeFromShelf')}
        favoriteAddLabel={appState.t('library.favoriteAdd')}
        favoriteRemoveLabel={appState.t('library.favoriteRemove')}
        triggerLabel={appState.t('library.optionsFor', { title: book.title })}
        onRead={() => {
          void appState.startReading(book);
        }}
        onEdit={() => {
          appState.handleEditBook(book);
        }}
        onRemove={() => {
          void appState.handleHideBook(book);
        }}
        onToggleFavorite={() => {
          void appState.handleToggleFavorite(book);
        }}
      />
    {/snippet}
  </BookCard>
{:else}
  <ul class="space-y-2">
    {#each appState.continueReadingBooks as book}
      <li>
        <BookCard
          {book}
          variant="continue-reading"
          compact={appState.continueReadingBooks.length > 1}
          selected={appState.previewBookId === book.id}
          onSelect={() => {
            appState.openShelfDetails(book);
          }}
          onRead={() => {
            void appState.startReading(book);
          }}
          t={appState.t}
        >
          {#snippet actions()}
            <ShelfActionMenu
              bookId={book.id}
              isFavorite={Boolean(book.isFavorite)}
              readLabel={appState.t('app.read')}
              editLabel={appState.t('library.editMetadata.title')}
              removeLabel={appState.t('library.removeFromShelf')}
              favoriteAddLabel={appState.t('library.favoriteAdd')}
              favoriteRemoveLabel={appState.t('library.favoriteRemove')}
              triggerLabel={appState.t('library.optionsFor', { title: book.title })}
              onEdit={() => {
                appState.handleEditBook(book);
              }}
              onRemove={() => {
                void appState.handleHideBook(book);
              }}
              onToggleFavorite={() => {
                void appState.handleToggleFavorite(book);
              }}
            />
          {/snippet}
        </BookCard>
      </li>
    {/each}
  </ul>
  {#if appState.previewBookId}
    {@const pb = appState.getBookById(appState.previewBookId)}
    {#if pb}
      <p class="mt-2 text-sm text-(--color-text-muted)">{appState.t('app.homeReadHint')}</p>
    {/if}
  {/if}
{/if}
