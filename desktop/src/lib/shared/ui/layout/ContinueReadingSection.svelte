<script lang="ts">
  import { appState } from "$lib/shared/stores/AppState.svelte";
  import { BookCard } from "$lib/features/library";
</script>

{#if appState.continueReadingBooks.length === 0}
  <p class="text-sm text-(--color-text-muted)">{appState.t("home.continueReadingPlaceholder")}</p>
{:else if appState.continueReadingBooks.length === 1}
  {@const book = appState.continueReadingBooks[0]}
  <BookCard
    book={book}
    variant="continue-reading"
    selected={appState.previewBookId === book.id}
    onSelect={() => {
      appState.openDetails(book);
    }}
    onRead={() => {
      void appState.startReading(book);
    }}
    t={appState.t}
  />
{:else}
  <ul class="space-y-2">
    {#each appState.continueReadingBooks as book}
      <li>
        <BookCard
          book={book}
          variant="continue-reading"
          compact={appState.continueReadingBooks.length > 1}
          selected={appState.previewBookId === book.id}
          onSelect={() => {
            appState.openDetails(book);
          }}
          onRead={() => {
            void appState.startReading(book);
          }}
          t={appState.t}
        />
      </li>
    {/each}
  </ul>
  {#if appState.previewBookId}
    {@const pb = appState.getBookById(appState.previewBookId)}
    {#if pb}
      <p class="mt-2 text-sm text-(--color-text-muted)">{appState.t("app.homeReadHint")}</p>
    {/if}
  {/if}
{/if}
