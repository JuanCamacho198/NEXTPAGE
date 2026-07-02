<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { getSafeProgressPercentage } from '$lib/shared/stores/homeState';
  import { BookCard, ShelfActionMenu } from '$lib/features/library';
  import SafeCover from '$lib/features/library/components/SafeCover.svelte';
  import Modal from '$lib/shared/ui/layout/Modal.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import { readFile } from '@tauri-apps/plugin-fs';
  import { pickImage } from '$lib/shared/services/FilePicker';
  import { upsertBookCover } from '$lib/shared/api/tauriClient';

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

  function getCollectionNames(ids: number[] | undefined): string[] {
    if (!ids || ids.length === 0) return [];
    const result: string[] = [];
    for (const id of ids) {
      const coll = appState.collections.find((c) => c.id === id);
      if (coll) result.push(coll.name);
    }
    return result;
  }

  function formatRelativeDate(iso: string): string {
    try {
      const date = new Date(iso);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMin = Math.floor(diffMs / (1000 * 60));
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
      if (diffDays === 0) {
        if (diffMin < 1) return 'Ahora';
        if (diffMin < 60) return `Hace ${diffMin} min`;
        const hours = Math.floor(diffMin / 60);
        return `Hace ${hours} h`;
      }
      if (diffDays === 1) return 'Ayer';
      if (diffDays < 7) return `Hace ${diffDays} días`;
      if (diffDays < 30) return `Hace ${Math.floor(diffDays / 7)} sem.`;
      if (diffDays < 365) return `Hace ${Math.floor(diffDays / 30)} meses`;
      return `Hace ${Math.floor(diffDays / 365)} años`;
    } catch {
      return '';
    }
  }

  const LANGUAGE_NAMES: Record<string, string> = {
    es: 'Español',
    en: 'Inglés',
    fr: 'Francés',
    de: 'Alemán',
    it: 'Italiano',
    pt: 'Portugués',
    ru: 'Ruso',
    ja: 'Japonés',
    zh: 'Chino',
    ar: 'Árabe',
    ko: 'Coreano',
    nl: 'Neerlandés',
    pl: 'Polaco',
    sv: 'Sueco',
    tr: 'Turco',
    vi: 'Vietnamita',
  };

  function getLanguageName(code: string): string {
    return LANGUAGE_NAMES[code.toLowerCase()] ?? code.toUpperCase();
  }

  function formatPublicationDate(iso: string): string {
    if (!iso) return '';
    try {
      const date = new Date(iso);
      if (isNaN(date.getTime())) return iso;
      return date.toLocaleDateString('es-ES', {
        year: 'numeric',
        month: 'short',
      });
    } catch {
      return iso;
    }
  }

  function getMimeTypeFromExtension(fileName: string): string {
    const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
    const map: Record<string, string> = {
      png: 'image/png',
      jpg: 'image/jpeg',
      jpeg: 'image/jpeg',
      webp: 'image/webp',
      gif: 'image/gif',
    };
    return map[ext] ?? 'image/png';
  }

  const STATUS_OPTIONS = [
    { value: 'reading', label: 'En lectura' },
    { value: 'to_read', label: 'Por leer' },
    { value: 'completed', label: 'Completado' },
  ];

  function getCurrentStatus(book: typeof appState.selectedShelfBook): string {
    if (!book) return 'reading';
    if (book.completed) return 'completed';
    if (book.toRead) return 'to_read';
    return 'reading';
  }

  async function handleCoverImport(
    book: NonNullable<typeof appState.selectedShelfBook>,
  ): Promise<void> {
    const result = await pickImage();
    if (!result) return;
    try {
      const bytes = await readFile(result.path);
      const mimeType = getMimeTypeFromExtension(result.name);
      await upsertBookCover({
        bookId: book.id,
        data: Array.from(bytes),
        mimeType,
      });
      // Update local coverPath so the new cover shows immediately
      const found = appState.books.find((b) => b.id === book.id);
      if (found) {
        found.coverPath = result.path;
      }
    } catch (e) {
      console.error('Failed to import cover:', e);
    }
  }

  // ─── Genre options ───
  const KNOWN_GENRES = [
    'Novela',
    'Ficción',
    'No ficción',
    'Ciencia ficción',
    'Fantasía',
    'Terror',
    'Misterio',
    'Romance',
    'Thriller',
    'Biografía / Memorias',
    'Historia',
    'Ciencia / Tecnología',
    'Autoayuda',
    'Filosofía',
    'Ensayo',
    'Poesía',
    'Aventura',
    'Clásicos',
  ] as const;

  const GENRE_OPTIONS = [
    ...KNOWN_GENRES.map((g) => ({ value: g, label: g })),
    { value: '__other__', label: '✏️ Otro…' },
  ];

  // ─── Inline editing ───
  let isEditing = $state(false);
  let editTitle = $state('');
  let editAuthor = $state('');
  let selectedGenre = $state<string | null>(null);
  let customGenre = $state('');
  let editError = $state<string | null>(null);
  let isSaving = $state(false);

  const MAX_GENRE_LENGTH = 80;
  const CONTROL_CHAR_REGEX = /[\u0000-\u001f\u007f]/;

  function startEditing(book: NonNullable<typeof appState.selectedShelfBook>): void {
    editTitle = book.title;
    editAuthor = book.author || '';
    const stored = (book.genre ?? '').trim();
    const known = KNOWN_GENRES.find((g) => g.toLowerCase() === stored.toLowerCase());
    if (known) {
      selectedGenre = known;
      customGenre = '';
    } else {
      selectedGenre = '__other__';
      customGenre = stored;
    }
    editError = null;
    isEditing = true;
  }

  function cancelEditing(): void {
    isEditing = false;
    editError = null;
  }

  function resolveGenre(): string {
    if (selectedGenre === '__other__') return customGenre.trim();
    return selectedGenre ?? '';
  }

  async function saveEditing(): Promise<void> {
    const book = appState.selectedShelfBook;
    if (!book) return;
    if (!editTitle.trim()) {
      editError = 'El título es obligatorio';
      return;
    }
    const trimmedGenre = resolveGenre();
    if (trimmedGenre.length > MAX_GENRE_LENGTH) {
      editError = 'El género no puede exceder 80 caracteres';
      return;
    }
    if (trimmedGenre.length > 0 && CONTROL_CHAR_REGEX.test(trimmedGenre)) {
      editError = 'El género contiene caracteres inválidos';
      return;
    }

    isSaving = true;
    editError = null;
    try {
      await appState.handleSaveEditedBook({
        ...book,
        title: editTitle.trim(),
        author: editAuthor.trim(),
        genre: trimmedGenre.length > 0 ? trimmedGenre : null,
      });
      isEditing = false;
    } catch (e) {
      editError = e instanceof Error ? e.message : 'Error al guardar';
    } finally {
      isSaving = false;
    }
  }

  // Reset editing state when modal closes
  $effect(() => {
    if (!showShelfModal) {
      isEditing = false;
      editError = null;
    }
  });
</script>

<section class="space-y-3">
  <header class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
    <div class="flex flex-wrap items-center gap-2" data-testid="shelf-tabs">
      {#each appState.SHELF_TAB_OPTIONS as tabOption}
        <button
          type="button"
          data-testid={`shelf-tab-${tabOption.key}`}
          class={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${appState.shelfQueryState.tab === tabOption.key ? 'border-(--color-primary) bg-[color:color-mix(in_srgb,var(--color-primary)_12%,var(--color-surface))] text-(--color-primary)' : 'border-(--color-border) bg-(--color-background) text-(--color-text-muted) hover:bg-(--color-surface-hover)'}`}
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
  {@const currentStatus = getCurrentStatus(shelfDetail)}
  <Modal
    bind:open={showShelfModal}
    title={isEditing ? 'Editar metadatos' : shelfDetail.title}
    size="xl"
  >
    {#snippet children()}
      <div class="flex flex-col gap-6 sm:flex-row">
        <!-- Cover column -->
        <div class="shrink-0 mx-auto sm:mx-0">
          {#if shelfDetail.coverPath}
            <div class="relative w-48">
              <SafeCover
                path={shelfDetail.coverPath}
                alt={shelfDetail.title}
                className="w-48 h-64 object-cover rounded-lg shadow-md border border-(--color-border)"
              >
                {#snippet fallback()}
                  <div
                    class="w-48 h-64 rounded-lg bg-gradient-to-br from-(--color-primary)/8 to-(--color-primary)/3 flex items-center justify-center border border-(--color-border) shadow-md"
                  >
                    <span class="text-4xl font-bold text-(--color-primary)/30"
                      >{shelfDetail.title.trim()[0]?.toUpperCase() || '?'}</span
                    >
                  </div>
                {/snippet}
              </SafeCover>
              <!-- Trash icon overlay -->
              <button
                type="button"
                class="absolute bottom-2 right-2 flex h-7 w-7 items-center justify-center rounded-full bg-black/50 text-white hover:bg-black/70 transition-colors"
                onclick={() => void appState.handleDeleteCover(shelfDetail)}
                aria-label="Eliminar portada"
              >
                <svg
                  class="h-3.5 w-3.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  stroke-width="2"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                  />
                </svg>
              </button>
            </div>
          {:else}
            <div class="relative w-48">
              <div
                class="w-36 h-52 rounded-lg bg-gradient-to-br from-(--color-primary)/8 to-(--color-primary)/3 flex items-center justify-center border border-(--color-border) shadow-md"
              >
                <span class="text-4xl font-bold text-(--color-primary)/30"
                  >{shelfDetail.title.trim()[0]?.toUpperCase() || '?'}</span
                >
              </div>
              <!-- Import icon overlay -->
              <button
                type="button"
                class="absolute bottom-2 right-2 flex h-7 w-7 items-center justify-center rounded-full bg-black/50 text-white hover:bg-black/70 transition-colors"
                onclick={() => handleCoverImport(shelfDetail)}
                aria-label="Importar portada"
              >
                <svg
                  class="h-3.5 w-3.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  stroke-width="2"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5 5-5M12 15V3"
                  />
                </svg>
              </button>
            </div>
          {/if}
        </div>

        <!-- Info column -->
        <div class="flex-1 min-w-0 space-y-4">
          {#if isEditing}
            <!-- Inline edit form -->
            <div class="rounded-lg border border-(--color-border) p-4 space-y-3">
              <div>
                <label
                  for="edit-title"
                  class="mb-1 block text-xs font-medium text-(--color-primary)"
                >
                  Título
                </label>
                <input
                  id="edit-title"
                  type="text"
                  bind:value={editTitle}
                  class="w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) focus:border-(--color-primary) focus:outline-none"
                />
              </div>
              <div>
                <label
                  for="edit-author"
                  class="mb-1 block text-xs font-medium text-(--color-primary)"
                >
                  Autor
                </label>
                <input
                  id="edit-author"
                  type="text"
                  bind:value={editAuthor}
                  class="w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) focus:border-(--color-primary) focus:outline-none"
                />
              </div>
              <div>
                <label
                  for="edit-genre"
                  class="mb-1 block text-xs font-medium text-(--color-primary)"
                >
                  Género
                </label>
                <Dropdown
                  options={GENRE_OPTIONS}
                  bind:value={selectedGenre}
                  placeholder="Seleccionar género"
                  class="w-full"
                />
                {#if selectedGenre === '__other__'}
                  <input
                    id="edit-genre"
                    type="text"
                    bind:value={customGenre}
                    maxlength={MAX_GENRE_LENGTH}
                    placeholder="Escribir género personalizado"
                    class="mt-2 w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) focus:border-(--color-primary) focus:outline-none"
                  />
                {/if}
              </div>
              {#if editError}
                <p class="text-sm text-red-600">{editError}</p>
              {/if}
            </div>
          {:else}
            <!-- Info card -->
            <div class="rounded-lg border border-(--color-border) p-4">
              <div class="grid grid-cols-2 gap-x-6 gap-y-2">
                <!-- Autor -->
                <p class="text-xs text-(--color-text-muted)">
                  <span class="font-medium text-(--color-primary)">Autor:</span>
                  {#if shelfDetail.author}
                    {shelfDetail.author}
                  {:else}
                    <span class="italic opacity-60">Sin autor</span>
                  {/if}
                </p>

                <!-- Formato -->
                <p class="flex items-center gap-2 text-xs text-(--color-text-muted)">
                  <span class="font-medium text-(--color-primary) shrink-0">Formato:</span>
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border uppercase {shelfDetail.format ===
                    'epub'
                      ? 'bg-(--color-primary)/8 text-(--color-primary) border-(--color-primary)/25'
                      : 'bg-amber-500/8 text-amber-600 border-amber-500/25'}"
                  >
                    {shelfDetail.format}
                  </span>
                </p>

                <!-- Género -->
                <p class="text-xs text-(--color-text-muted)">
                  <span class="font-medium text-(--color-primary)">Género:</span>
                  {#if shelfDetail.genre}
                    {shelfDetail.genre}
                  {:else}
                    <span class="italic opacity-60">Sin clasificar</span>
                  {/if}
                </p>

                <!-- Idioma -->
                <p class="text-xs text-(--color-text-muted)">
                  <span class="font-medium text-(--color-primary)">Idioma:</span>
                  {#if shelfDetail.language}
                    {getLanguageName(shelfDetail.language)}
                  {:else}
                    <span class="italic opacity-60">Sin especificar</span>
                  {/if}
                </p>
              </div>

              <!-- Status dropdown + Favorite + Completed -->
              <div class="flex flex-wrap items-center gap-2 pt-3">
                <!-- Status dropdown -->
                <div class="flex items-center gap-1.5">
                  <span class="text-xs text-(--color-text-muted)">Estado:</span>
                  <Dropdown
                    options={STATUS_OPTIONS}
                    value={currentStatus}
                    onchange={({ value }) => {
                      appState.handleStatusChange(shelfDetail, value);
                    }}
                  />
                </div>

                <!-- Favorite toggle -->
                {#if shelfDetail.isFavorite !== undefined}
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium border transition-colors {shelfDetail.isFavorite
                      ? 'bg-amber-500/15 text-amber-500 border-amber-500/25'
                      : 'bg-(--color-surface-subtle) text-(--color-text-muted) border-(--color-border) hover:border-amber-500/25'}"
                    onclick={() => void appState.handleToggleFavorite(shelfDetail)}
                    aria-label={shelfDetail.isFavorite
                      ? 'Quitar de favoritos'
                      : 'Agregar a favoritos'}
                  >
                    <svg
                      class="h-3.5 w-3.5"
                      viewBox="0 0 24 24"
                      stroke-width="1.5"
                    >
                      {#if shelfDetail.isFavorite}
                        <path
                          fill="#f59e0b"
                          stroke="#f59e0b"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M11.048 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z"
                        />
                      {:else}
                        <path
                          fill="none"
                          stroke="currentColor"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M11.048 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z"
                        />
                      {/if}
                    </svg>
                    {shelfDetail.isFavorite ? 'Favorito' : 'Favorito'}
                  </button>
                {/if}

                <!-- Completed badge -->
                {#if shelfDetail.completed}
                  <span
                    class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium border bg-green-500/10 text-green-500 border-green-500/25"
                  >
                    <svg
                      class="h-3 w-3"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      stroke-width="2.5"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                    Completado
                  </span>
                {/if}
              </div>
            </div>
          {/if}

          <!-- Progress card -->
          <div class="rounded-lg border border-(--color-border) p-4 space-y-3">
            <h4 class="text-xs font-semibold uppercase tracking-wider text-(--color-text-muted)">
              <Icon name="clock" size="sm" class="inline -mt-0.5 mr-1" />
              Lectura
            </h4>
            {#if progressPct > 0}
              <div class="space-y-1">
                <div class="flex items-center justify-between text-(--text-2xs) text-(--color-text-muted)">
                  <span>Progreso</span>
                  <span>{progressPct}%</span>
                </div>
                <div class="h-1.5 w-full rounded-full bg-(--color-border)">
                  <div
                    class="h-1.5 rounded-full bg-(--color-primary) transition-all"
                    style="width: {progressPct}%"
                  ></div>
                </div>
                {#if shelfDetail.totalPages > 0}
                  <p class="text-(--text-2xs) text-(--color-text-muted)">
                    Página {shelfDetail.currentPage} de {shelfDetail.totalPages}
                  </p>
                {/if}
              </div>
            {/if}
            <dl class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-(--color-text-muted)">
              {#if shelfDetail.minutesRead > 0}
                <dt class="sr-only">Tiempo de lectura</dt>
                <dd class="font-medium text-(--color-primary)">
                  <Icon name="clock" size="sm" class="inline -mt-0.5 mr-0.5" />
                  {formatMinutes(shelfDetail.minutesRead)} leídos
                </dd>
              {/if}
              {#if shelfDetail.updatedAt}
                <dt class="sr-only">Última actividad</dt>
                <dd>{formatRelativeDate(shelfDetail.updatedAt)}</dd>
              {/if}
            </dl>
            {#if shelfDetail.totalPages === 0 && shelfDetail.minutesRead === 0 && !shelfDetail.updatedAt}
              <p class="text-xs italic text-(--color-text-muted) opacity-60">
                Sin datos de lectura todavía
              </p>
            {/if}
          </div>

          <!-- Details card -->
          <div class="rounded-lg border border-(--color-border) p-4 space-y-3">
            <h4 class="text-xs font-semibold uppercase tracking-wider text-(--color-text-muted)">
              <Icon name="info" size="sm" class="inline -mt-0.5 mr-1" />
              Detalles
            </h4>
            {#if shelfDetail.publicationDate}
              <p class="flex items-center gap-1.5 text-xs text-(--color-text-muted)">
                <Icon name="calendar" size="sm" class="shrink-0" />
                Publicado: {formatPublicationDate(shelfDetail.publicationDate)}
              </p>
            {/if}
            {#if shelfDetail.createdAt}
              <p class="flex items-center gap-1.5 text-xs text-(--color-text-muted)">
                <Icon name="calendar" size="sm" class="shrink-0" />
                Agregado: {formatRelativeDate(shelfDetail.createdAt)}
              </p>
            {/if}
            {#if shelfDetail.collectionIds && shelfDetail.collectionIds.length > 0}
              {@const collNames = getCollectionNames(shelfDetail.collectionIds)}
              {#if collNames.length > 0}
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="text-xs text-(--color-text-muted)">Colecciones:</span>
                  {#each collNames as name}
                    <span
                      class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium bg-(--color-primary)/8 text-(--color-primary) border border-(--color-primary)/15"
                    >
                      {name}
                    </span>
                  {/each}
                </div>
              {/if}
            {/if}
          </div>
        </div>
      </div>
    {/snippet}
    {#snippet footer()}
      {#if isEditing}
        <Button size="sm" variant="ghost" onclick={cancelEditing} disabled={isSaving}>
          Cancelar
        </Button>
        <Button size="sm" onclick={saveEditing} disabled={isSaving}>
          {isSaving ? 'Guardando…' : 'Guardar'}
        </Button>
      {:else}
        <Button size="sm" variant="ghost" onclick={() => startEditing(shelfDetail)}>
          <Icon name="edit" size="sm" /> Editar metadatos
        </Button>
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
      {/if}
    {/snippet}
  </Modal>
{/if}
