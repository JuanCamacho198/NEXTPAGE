<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { getSafeProgressPercentage } from '$lib/shared/stores/homeState';
  import { BookCard, ShelfActionMenu } from '$lib/features/library';
  import SafeCover from '$lib/features/library/components/SafeCover.svelte';
  import Modal from '$lib/shared/ui/layout/Modal.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';

  let showShelfModal = $state(false);

  const shelfSortOptions = $derived(
    appState.SHELF_SORT_OPTIONS.map((o) => ({ value: o.key, label: appState.t(o.label) })),
  );

  $effect(() => {
    if (appState.selectedShelfBook) {
      showShelfModal = true;
    }
  });
  $effect(() => {
    if (!showShelfModal && appState.selectedShelfBook) {
      appState.closeShelfDetails();
    }
  });

  function formatMinutes(minutes: number): string {
    if (minutes < 1) return '< 1 min';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0 && m > 0) return `${h}h ${m}m`;
    if (h > 0) return `${h}h`;
    return `${m} min`;
  }

  function formatRelativeDate(iso: string): string {
    try {
      const date = new Date(iso);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      if (diffDays === 0) return 'Hoy';
      if (diffDays === 1) return 'Ayer';
      if (diffDays < 7) return `Hace ${diffDays} días`;
      if (diffDays < 30) return `Hace ${Math.floor(diffDays / 7)} sem.`;
      if (diffDays < 365) return `Hace ${Math.floor(diffDays / 30)} meses`;
      return `Hace ${Math.floor(diffDays / 365)} años`;
    } catch {
      return '';
    }
  }
</script>

<section class="space-y-3">
  <header class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
    <div class="flex flex-wrap items-center gap-2" data-testid="shelf-tabs">
      {#each appState.SHELF_TAB_OPTIONS as tabOption}
        <button
          type="button"
          data-testid={`shelf-tab-${tabOption.key}`}
          class={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${appState.shelfQueryState.tab === tabOption.key ? 'border-(--color-primary) bg-[color:color-mix(in_srgb,var(--color-primary)_12%,var(--color-surface))] text-(--color-primary)' : 'border-(--color-border) bg-(--color-background) text-(--color-text-muted) hover:bg-(--color-border)'}`}
          onclick={() => {
            appState.setShelfTab(tabOption.key);
          }}
        >
          {appState.t(tabOption.label)}
        </button>
      {/each}
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <span class="sr-only">{appState.t('home.shelfSortLabel')}</span>
      <div data-testid="shelf-sort">
        <Dropdown
          options={shelfSortOptions}
          value={appState.shelfQueryState.sortKey}
          onchange={({ value }) => {
            appState.setShelfSort(value as (typeof appState.SHELF_SORT_OPTIONS)[number]['key']);
          }}
        />
      </div>

      <fieldset
        class="inline-flex rounded-md border-(--color-border) bg-(--color-background) p-1 border-0"
        data-testid="shelf-view-toggle"
      >
        <legend class="sr-only">Vista de estantería</legend>
        <button
          type="button"
          class={`flex h-7 w-8 items-center justify-center rounded px-0 py-1 text-xs font-medium ${appState.shelfQueryState.viewMode === 'grid' ? 'bg-(--color-surface) text-(--color-primary)' : 'text-(--color-text-muted)'}`}
          onclick={() => {
            appState.setShelfViewMode('grid');
          }}
          aria-label="Vista en cuadrícula"
        >
          <Icon name="list" size="sm" />
        </button>
        <button
          type="button"
          class={`flex h-7 w-8 items-center justify-center rounded px-0 py-1 text-xs font-medium ${appState.shelfQueryState.viewMode === 'list' ? 'bg-(--color-surface) text-(--color-primary)' : 'text-(--color-text-muted)'}`}
          onclick={() => {
            appState.setShelfViewMode('list');
          }}
          aria-label="Vista en lista"
        >
          <Icon name="grid" size="sm" />
        </button>
      </fieldset>

      <div class="relative min-w-[220px] flex-1 lg:min-w-[280px]">
        <input
          type="text"
          data-testid="shelf-search"
          class="w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-1.5 pr-8 text-sm text-(--color-primary) placeholder-(--color-text-muted)"
          placeholder={appState.t('home.shelfSearchPlaceholder')}
          value={appState.shelfQueryState.rawQuery}
          oninput={appState.handleShelfQueryInput}
        />
        {#if appState.shelfQueryState.rawQuery.length > 0}
          <button
            type="button"
            class="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-(--color-text-muted)"
            aria-label={appState.t('home.shelfClearSearch')}
            onclick={appState.clearShelfQuery}
          >
            x
          </button>
        {/if}
      </div>
    </div>
  </header>

  {#if appState.shelfSortToken}
    <p class="text-xs text-(--color-text-muted)">
      {appState.t('home.shelfSortFromQuery', { value: appState.shelfSortToken })}
    </p>
  {/if}

  {#if appState.shelfWarnings.length > 0}
    <div
      class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900"
      data-testid="shelf-warnings"
    >
      <p class="font-medium">{appState.t('home.shelfWarningsLabel')}</p>
      <p class="mt-1">
        {appState.t('home.shelfSearchInvalid', { value: appState.shelfWarnings.join(', ') })}
      </p>
    </div>
  {/if}

  <p class="text-xs text-(--color-text-muted)">
    {appState.t('home.shelfResults', {
      count: appState.shelfBooks.length,
      total: appState.myShelfBooks.length,
    })}
  </p>
</section>

{#if appState.myShelfBooks.length === 0}
  <p class="text-sm text-(--color-text-muted)">{appState.t('home.myShelfPlaceholder')}</p>
{:else if appState.shelfBooks.length === 0}
  <p class="text-sm text-(--color-text-muted)">{appState.t('home.shelfNoResults')}</p>
{:else}
  {#if appState.shelfQueryState.viewMode === 'grid'}
    {#if appState.shelfBooks.length === 1}
      {@const book = appState.shelfBooks[0]}
      <BookCard
        {book}
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
    {:else}
      <ul class="grid grid-cols-1 gap-2 md:grid-cols-2">
        {#each appState.shelfBooks as book}
          <li>
            <BookCard
              {book}
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
    {/if}
  {:else}
    <ul class="space-y-2">
      {#each appState.shelfBooks as book}
        <li>
          <BookCard
            {book}
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
  {/if}
{/if}

{#if appState.selectedShelfBook}
  {@const shelfDetail = appState.selectedShelfBook}
  {@const progressPct = Math.round(getSafeProgressPercentage(shelfDetail))}
  <Modal bind:open={showShelfModal} title={shelfDetail.title} size="lg" noCloseButton>
    {#snippet children()}
      <div class="flex flex-col gap-6 sm:flex-row">
        <!-- Cover column -->
        <div class="shrink-0 mx-auto sm:mx-0">
          {#if shelfDetail.coverPath}
            <SafeCover
              path={shelfDetail.coverPath}
              alt={shelfDetail.title}
              className="w-36 h-52 object-cover rounded-lg shadow-md border border-(--color-border)"
            >
              {#snippet fallback()}
                <div
                  class="w-36 h-52 rounded-lg bg-gradient-to-br from-(--color-primary)/8 to-(--color-primary)/3 flex items-center justify-center border border-(--color-border) shadow-md"
                >
                  <span class="text-4xl font-bold text-(--color-primary)/30"
                    >{shelfDetail.title.trim()[0]?.toUpperCase() || '?'}</span
                  >
                </div>
              {/snippet}
            </SafeCover>
          {:else}
            <div
              class="w-36 h-52 rounded-lg bg-gradient-to-br from-(--color-primary)/8 to-(--color-primary)/3 flex items-center justify-center border border-(--color-border) shadow-md"
            >
              <span class="text-4xl font-bold text-(--color-primary)/30"
                >{shelfDetail.title.trim()[0]?.toUpperCase() || '?'}</span
              >
            </div>
          {/if}
        </div>

        <!-- Info column -->
        <div class="flex-1 min-w-0 space-y-3">
          {#if shelfDetail.author}
            <p class="text-sm text-(--color-text-muted)">{shelfDetail.author}</p>
          {/if}

          <!-- Format badge -->
          <span
            class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border uppercase {shelfDetail.format ===
            'epub'
              ? 'bg-(--color-primary)/8 text-(--color-primary) border-(--color-primary)/25'
              : 'bg-amber-500/8 text-amber-600 border-amber-500/25'}"
          >
            {shelfDetail.format}
          </span>

          <!-- Progress -->
          {#if shelfDetail.totalPages > 0}
            <div class="space-y-1">
              <div class="flex justify-between text-xs text-(--color-text-muted)">
                <span>Progreso</span>
                <span>{shelfDetail.currentPage}/{shelfDetail.totalPages} · {progressPct}%</span>
              </div>
              <div class="h-1.5 w-full rounded-full bg-(--color-border)">
                <div
                  class="h-1.5 rounded-full bg-(--color-primary) transition-all"
                  style="width: {progressPct}%"
                ></div>
              </div>
            </div>
          {/if}

          <!-- Metadata -->
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-(--color-text-muted)">
            {#if shelfDetail.minutesRead > 0}
              <dt class="sr-only">Tiempo de lectura</dt>
              <dd>{formatMinutes(shelfDetail.minutesRead)} leídos</dd>
            {/if}
            {#if shelfDetail.updatedAt}
              <dt class="sr-only">Última actividad</dt>
              <dd>{formatRelativeDate(shelfDetail.updatedAt)}</dd>
            {/if}
          </dl>
        </div>
      </div>
    {/snippet}
    {#snippet footer()}
      <Button size="sm" variant="ghost" onclick={appState.closeShelfDetails}
        >{appState.t('settings.close')}</Button
      >
      <Button
        size="sm"
        onclick={() => {
          void appState.startReading(shelfDetail);
        }}
      >
        {appState.t('app.read')}
      </Button>
    {/snippet}
  </Modal>
{/if}
