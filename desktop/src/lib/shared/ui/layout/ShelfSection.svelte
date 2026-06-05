<script lang="ts">
  import { appState } from "$lib/shared/stores/AppState.svelte";
  import { getSafeProgressPercentage } from "$lib/shared/stores/homeState";
  import BookCard from "$lib/components/library/BookCard.svelte";
  import ShelfActionMenu from "$lib/components/library/ShelfActionMenu.svelte";
  import Icon from "$lib/shared/ui/navigation/Icon.svelte";
  import Button from "$lib/shared/ui/forms/Button.svelte";
</script>

<div class="space-y-3">
  <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
    <div class="flex flex-wrap items-center gap-2" data-testid="shelf-tabs">
      {#each appState.SHELF_TAB_OPTIONS as tabOption}
        <button
          type="button"
          data-testid={`shelf-tab-${tabOption.key}`}
          class={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${appState.shelfQueryState.tab === tabOption.key ? "border-(--color-primary) bg-[color:color-mix(in_srgb,var(--color-primary)_12%,var(--color-surface))] text-(--color-primary)" : "border-(--color-border) bg-(--color-background) text-(--color-text-muted) hover:bg-(--color-border)"}`}
          onclick={() => {
            appState.setShelfTab(tabOption.key);
          }}
        >
          {appState.t(tabOption.label)}
        </button>
      {/each}
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <label class="sr-only" for="shelf-sort-select">{appState.t("home.shelfSortLabel")}</label>
      <select
        id="shelf-sort-select"
        data-testid="shelf-sort"
        class="rounded-md border border-(--color-border) bg-(--color-background) px-2 py-1 text-xs text-(--color-primary)"
        value={appState.shelfQueryState.sortKey}
        onchange={(event) => {
          const value = (event.target as HTMLSelectElement).value as (typeof appState.SHELF_SORT_OPTIONS)[number]["key"];
          appState.setShelfSort(value);
        }}
      >
        {#each appState.SHELF_SORT_OPTIONS as sortOption}
          <option value={sortOption.key}>{appState.t(sortOption.label)}</option>
        {/each}
      </select>

      <div class="inline-flex rounded-md border border-(--color-border) bg-(--color-background) p-1" data-testid="shelf-view-toggle">
        <button
          type="button"
          class={`flex h-7 w-8 items-center justify-center rounded px-0 py-1 text-xs font-medium ${appState.shelfQueryState.viewMode === "grid" ? "bg-(--color-surface) text-(--color-primary)" : "text-(--color-text-muted)"}`}
          onclick={() => {
            appState.setShelfViewMode("grid");
          }}
          aria-label="Vista en cuadrícula"
        >
          <Icon name="list" size="sm" />
        </button>
        <button
          type="button"
          class={`flex h-7 w-8 items-center justify-center rounded px-0 py-1 text-xs font-medium ${appState.shelfQueryState.viewMode === "list" ? "bg-(--color-surface) text-(--color-primary)" : "text-(--color-text-muted)"}`}
          onclick={() => {
            appState.setShelfViewMode("list");
          }}
          aria-label="Vista en lista"
        >
          <Icon name="grid" size="sm" />
        </button>
      </div>

      <div class="relative min-w-[220px] flex-1 lg:min-w-[280px]">
        <input
          type="text"
          data-testid="shelf-search"
          class="w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-1.5 pr-8 text-sm text-(--color-primary) placeholder-(--color-text-muted)"
          placeholder={appState.t("home.shelfSearchPlaceholder")}
          value={appState.shelfQueryState.rawQuery}
          oninput={appState.handleShelfQueryInput}
        />
        {#if appState.shelfQueryState.rawQuery.length > 0}
          <button
            type="button"
            class="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-(--color-text-muted)"
            aria-label={appState.t("home.shelfClearSearch")}
            onclick={appState.clearShelfQuery}
          >
            x
          </button>
        {/if}
      </div>
    </div>
  </div>

  {#if appState.shelfSortToken}
    <p class="text-xs text-(--color-text-muted)">{appState.t("home.shelfSortFromQuery", { value: appState.shelfSortToken })}</p>
  {/if}

  {#if appState.shelfWarnings.length > 0}
    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900" data-testid="shelf-warnings">
      <p class="font-medium">{appState.t("home.shelfWarningsLabel")}</p>
      <p class="mt-1">{appState.t("home.shelfSearchInvalid", { value: appState.shelfWarnings.join(", ") })}</p>
    </div>
  {/if}

  <p class="text-xs text-(--color-text-muted)">{appState.t("home.shelfResults", { count: appState.shelfBooks.length, total: appState.myShelfBooks.length })}</p>
</div>

{#if appState.myShelfBooks.length === 0}
  <p class="text-sm text-(--color-text-muted)">{appState.t("home.myShelfPlaceholder")}</p>
{:else if appState.shelfBooks.length === 0}
  <p class="text-sm text-(--color-text-muted)">{appState.t("home.shelfNoResults")}</p>
{:else}
  {#if appState.shelfQueryState.viewMode === "grid"}
    {#if appState.shelfBooks.length === 1}
      {@const book = appState.shelfBooks[0]}
      <BookCard
        book={book}
        variant="shelf"
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
            readLabel={appState.t("app.read")}
            editLabel={appState.t("library.editMetadata.title")}
            removeLabel={appState.t("library.removeFromShelf")}
            favoriteAddLabel={appState.t("library.favoriteAdd")}
            favoriteRemoveLabel={appState.t("library.favoriteRemove")}
            triggerLabel={appState.t("library.optionsFor", { title: book.title })}
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
      <ul class="grid grid-cols-1 gap-2 md:grid-cols-2">
        {#each appState.shelfBooks as book}
          <li>
            <BookCard
              book={book}
              variant="shelf"
              compact={true}
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
                  readLabel={appState.t("app.read")}
                  editLabel={appState.t("library.editMetadata.title")}
                  removeLabel={appState.t("library.removeFromShelf")}
                  favoriteAddLabel={appState.t("library.favoriteAdd")}
                  favoriteRemoveLabel={appState.t("library.favoriteRemove")}
                  triggerLabel={appState.t("library.optionsFor", { title: book.title })}
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
    {/if}
  {:else}
    <ul class="space-y-2">
      {#each appState.shelfBooks as book}
        <li>
          <BookCard
            book={book}
            variant="shelf"
            compact={true}
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
                readLabel={appState.t("app.read")}
                editLabel={appState.t("library.editMetadata.title")}
                removeLabel={appState.t("library.removeFromShelf")}
                favoriteAddLabel={appState.t("library.favoriteAdd")}
                favoriteRemoveLabel={appState.t("library.favoriteRemove")}
                triggerLabel={appState.t("library.optionsFor", { title: book.title })}
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
  {/if}
{/if}

{#if appState.selectedShelfBook}
  {@const shelfDetail = appState.selectedShelfBook}
  <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true" aria-label={shelfDetail.title}>
    <div class="w-full max-w-xl rounded-xl border border-(--color-border) bg-(--color-surface) p-4 shadow-xl">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <h3 class="line-clamp-2 text-lg font-semibold text-(--color-primary)">{shelfDetail.title}</h3>
          <p class="truncate text-sm text-(--color-text-muted)">{shelfDetail.author || appState.t("app.unknownAuthor")}</p>
        </div>
        <Button size="sm" variant="ghost" onclick={appState.closeShelfDetails}>{appState.t("settings.close")}</Button>
      </div>
      <div class="mt-4 space-y-1 text-sm text-(--color-text-muted)">
        <p>{shelfDetail.format.toUpperCase()}</p>
        <p>{shelfDetail.currentPage}/{shelfDetail.totalPages || "-"} · {Math.round(getSafeProgressPercentage(shelfDetail))}%</p>
      </div>
      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="ghost" onclick={appState.closeShelfDetails}>{appState.t("settings.close")}</Button>
        <Button
          size="sm"
          onclick={() => {
            void appState.startReading(shelfDetail);
          }}
        >
          {appState.t("app.read")}
        </Button>
      </div>
    </div>
  </div>
{/if}
