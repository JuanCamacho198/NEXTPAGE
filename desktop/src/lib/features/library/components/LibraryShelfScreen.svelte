<script lang="ts">
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import DropMenu from '$lib/shared/ui/navigation/DropMenu.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import SafeCover from './SafeCover.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import {
    FILTER_OPTIONS,
    SORT_OPTIONS,
    getSafeProgressPercentage,
    getStateLabel,
    getTimestamp,
    formatPercent,
    type ShelfBook,
    type ShelfFilter,
    type ShelfSort,
    type ShelfView,
  } from '$lib/features/library/utils';

  type Props = {
    books: ShelfBook[];
    isImporting?: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onImportBook?: () => void;
    onOpenBook?: (book: ShelfBook) => void;
    onContinueReading?: (book: ShelfBook) => void;
    onToggleFavorite?: (book: ShelfBook) => void;
    onMarkCompleted?: (book: ShelfBook) => void;
    onViewDetails?: (book: ShelfBook) => void;
    onRemoveBook?: (book: ShelfBook) => void;
  };

  let {
    books,
    isImporting = false,
    t,
    onImportBook,
    onOpenBook,
    onContinueReading,
    onToggleFavorite,
    onMarkCompleted,
    onViewDetails,
    onRemoveBook,
  }: Props = $props();

  let searchQuery = $state('');
  let activeFilter = $state<ShelfFilter>('all');
  let activeSort = $state<ShelfSort>('date_added');
  let activeView = $state<ShelfView>('grid');

  const sortDropdownOptions = $derived(
    SORT_OPTIONS.map((o) => ({ value: o.key, label: o.label })),
  );
  const activeSortLabel = $derived(
    SORT_OPTIONS.find((o) => o.key === activeSort)?.label ?? '',
  );

  const totalBooks = $derived(books.length);
  const readingBooks = $derived(
    books.filter(
      (book) => getSafeProgressPercentage(book) > 0 && getSafeProgressPercentage(book) < 100,
    ).length,
  );
  const completedBooks = $derived(
    books.filter((book) => book.completed || getSafeProgressPercentage(book) >= 100).length,
  );

  const filteredBooks = $derived.by(() => {
    const query = searchQuery.trim().toLowerCase();

    const visible = books.filter((book) => {
      const progress = getSafeProgressPercentage(book);
      const matchesSearch =
        query.length === 0 ||
        book.title.toLowerCase().includes(query) ||
        (book.author ?? '').toLowerCase().includes(query);

      if (!matchesSearch) {
        return false;
      }

      if (activeFilter === 'all') {
        return true;
      }

      if (activeFilter === 'favorites') {
        return Boolean(book.isFavorite);
      }

      if (activeFilter === 'reading') {
        return progress > 0 && progress < 100;
      }

      if (activeFilter === 'completed') {
        return Boolean(book.completed) || progress >= 100;
      }

      return progress === 0;
    });

    return [...visible].sort((left: ShelfBook, right: ShelfBook) => {
      if (activeSort === 'title') {
        return left.title.localeCompare(right.title, 'es');
      }

      if (activeSort === 'progress') {
        return getSafeProgressPercentage(right) - getSafeProgressPercentage(left);
      }

      if (activeSort === 'last_read') {
        return getTimestamp(right) - getTimestamp(left);
      }

      return getTimestamp(right) - getTimestamp(left);
    });
  });
</script>

<section class="space-y-5">
  <header
    class="rounded-(--radius-2xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(17,30,48,0.94),rgba(10,18,31,0.94))] p-5 shadow-(--shadow-hero)"
  >
    <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div class="space-y-2">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight text-(--color-primary)">Estantería</h1>
          <p class="mt-1 text-sm text-(--color-text-muted)">
            Todos tus libros organizados en un solo lugar.
          </p>
        </div>

        <div class="flex flex-wrap gap-3 text-xs text-(--color-text-muted)">
          <div
            class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1.5"
          >
            {totalBooks} libros
          </div>
          <div
            class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1.5"
          >
            {readingBooks} leyendo
          </div>
          <div
            class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1.5"
          >
            {completedBooks} completados
          </div>
        </div>
      </div>

      <div class="flex w-full flex-col gap-3 xl:max-w-[640px]">
        <div class="flex flex-col gap-3 md:flex-row md:items-center">
          <label class="group relative flex-1">
            <span class="sr-only">Buscar libros</span>
            <svg
              class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-(--color-text-muted)"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            >
              <circle cx="11" cy="11" r="7"></circle>
              <path d="M20 20L17 17"></path>
            </svg>
            <input
              type="text"
              class="h-11 w-full rounded-2xl border border-(--color-border) bg-[rgba(8,17,31,0.72)] pl-11 pr-16 text-sm text-(--color-primary) outline-none placeholder:text-center placeholder:text-(--color-text-muted)"
              placeholder={t('library.searchPlaceholder')}
              bind:value={searchQuery}
            />
            <span
              class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 rounded-md border border-(--color-border) px-1.5 py-0.5 text-(--text-micro) text-(--color-text-muted)"
            >
              Ctrl K
            </span>
          </label>

          <Button
            onclick={onImportBook}
            disabled={isImporting}
            class="h-11 min-w-[170px] rounded-2xl bg-(--gradient-accent) !text-[#07111d] shadow-(--shadow-glow-strong)"
          >
            {isImporting ? 'Importando...' : 'Importar libro'}
          </Button>
        </div>
      </div>
    </div>
  </header>

  <section
    class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-section)"
  >
    <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
      <fieldset class="border-0 p-0 m-0">
        <legend class="sr-only">Filtrar por estado</legend>
        <div class="flex flex-wrap gap-2">
          {#each FILTER_OPTIONS as option}
            <button
              type="button"
              class={`rounded-2xl border px-3 py-2 text-xs font-medium transition ${activeFilter === option.key ? 'border-[rgba(82,143,255,0.4)] bg-[rgba(78,140,255,0.22)] text-(--color-primary)' : 'border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted) hover:text-(--color-primary)'}`}
              onclick={() => {
                activeFilter = option.key;
              }}
            >
              {option.label}
            </button>
          {/each}
        </div>
      </fieldset>

      <div class="flex flex-col gap-3 md:flex-row md:items-center">
        <span class="text-xs text-(--color-text-muted)">Ordenar por</span>
        <Dropdown options={sortDropdownOptions} bind:value={activeSort} class="min-w-[130px]">
          {#snippet trigger()}
            <span class="text-sm text-(--color-primary)">{activeSortLabel}</span>
            <svg class="ml-1 h-4 w-4 text-(--color-text-muted)" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          {/snippet}
        </Dropdown>

        <fieldset
          class="inline-flex rounded-2xl border-(--color-border) bg-(--color-surface-subtle) p-1 border-0"
        >
          <legend class="sr-only">Vista de estantería</legend>
          <button
            type="button"
            class={`flex h-9 w-10 items-center justify-center rounded-xl ${activeView === 'grid' ? 'bg-[rgba(78,140,255,0.2)] text-(--color-primary)' : 'text-(--color-text-muted)'}`}
            aria-label="Vista en cuadrícula"
            onclick={() => {
              activeView = 'grid';
            }}
          >
            <Icon name="grid" size="sm" title="Cuadrícula" />
          </button>
          <button
            type="button"
            class={`flex h-9 w-10 items-center justify-center rounded-xl ${activeView === 'list' ? 'bg-[rgba(78,140,255,0.2)] text-(--color-primary)' : 'text-(--color-text-muted)'}`}
            aria-label="Vista en lista"
            onclick={() => {
              activeView = 'list';
            }}
          >
            <Icon name="list" size="sm" title="Lista" />
          </button>
        </fieldset>
      </div>
    </div>
  </section>

  {#if activeView === 'grid'}
    <ul
      class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4 2xl:grid-cols-5 list-none p-0 m-0"
    >
      {#each filteredBooks as book}
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
              <DropMenu position="bottom-right">
                {#snippet trigger()}
                  <button
                    type="button"
                    class="flex h-9 w-9 items-center justify-center rounded-xl border border-(--color-border) bg-[rgba(20,32,49,0.92)] text-(--color-text-muted)"
                    aria-label={`Opciones para ${book.title}`}
                  >
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                      <circle cx="5" cy="12" r="1.8"></circle>
                      <circle cx="12" cy="12" r="1.8"></circle>
                      <circle cx="19" cy="12" r="1.8"></circle>
                    </svg>
                  </button>
                {/snippet}
                <button
                  class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
                  onclick={() => onOpenBook?.(book)}>Abrir libro</button
                >
                <button
                  class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
                  onclick={() => onToggleFavorite?.(book)}
                >
                  {book.isFavorite ? 'Quitar de favoritos' : 'Marcar como favorito'}
                </button>
                <button
                  class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
                  onclick={() => onMarkCompleted?.(book)}>Marcar como completado</button
                >
                <button
                  class="w-full px-4 py-2.5 text-left text-sm text-(--color-primary) hover:bg-[rgba(255,255,255,0.08)]"
                  onclick={() => onViewDetails?.(book)}>Ver detalles</button
                >
                <button
                  class="w-full px-4 py-2.5 text-left text-sm text-(--color-danger) hover:bg-[rgba(255,255,255,0.08)]"
                  onclick={() => onRemoveBook?.(book)}>Eliminar de la biblioteca</button
                >
              </DropMenu>
            </div>

            <div
              class="relative mb-4 aspect-[0.72] overflow-hidden rounded-[20px] bg-(--color-surface-subtle)"
            >
              <SafeCover
                path={book.coverPath ?? ''}
                alt={`Portada de ${book.title}`}
                className="h-full w-full object-cover"
              >
                {#snippet fallback()}
                  <div
                    class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] px-6 text-center text-xs uppercase tracking-[0.18em] text-(--color-primary)"
                  >
                    Sin portada
                  </div>
                {/snippet}
              </SafeCover>
            </div>

            <div class="space-y-1">
              <h3 class="line-clamp-2 text-sm font-semibold text-(--color-primary)">
                {book.title}
              </h3>
              <p class="line-clamp-1 text-xs text-(--color-text-muted)">
                {book.author || 'Autor desconocido'}
              </p>
            </div>

            <div
              class="mt-4 space-y-2"
              role="progressbar"
              aria-valuenow={getSafeProgressPercentage(book)}
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div class="h-2 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                <div
                  class="h-full rounded-full bg-(--gradient-accent-h)"
                  style={`width: ${formatPercent(book)};`}
                ></div>
              </div>
              <div class="flex items-center justify-between text-xs text-(--color-text-muted)">
                <span>{formatPercent(book)} leido</span>
                <span>{book.minutesRead} min</span>
              </div>
            </div>

            <div class="mt-auto grid grid-cols-2 gap-2 pt-4">
              <Button
                variant="secondary"
                size="sm"
                class="rounded-xl"
                onclick={() => onOpenBook?.(book)}
              >
                Abrir libro
              </Button>
              <Button
                size="sm"
                class="rounded-xl bg-(--gradient-accent) !text-[#07111d]"
                onclick={() => onContinueReading?.(book)}
              >
                {getSafeProgressPercentage(book) > 0 ? 'Continuar' : 'Empezar'}
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
          <div
            class="flex h-16 w-16 items-center justify-center rounded-full border border-(--color-border) bg-(--color-surface-subtle)"
          >
            <svg
              class="h-6 w-6"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            >
              <path d="M12 5V19"></path>
              <path d="M5 12H19"></path>
            </svg>
          </div>
          <div>
            <p class="text-sm font-semibold text-(--color-primary)">Añadir libro</p>
            <p class="mt-1 text-xs">Importa un nuevo archivo a tu biblioteca.</p>
          </div>
        </button>
      </li>
    </ul>
  {:else}
    <ul class="space-y-3 list-none p-0 m-0">
      {#each filteredBooks as book}
        <li>
          <article
            class="flex flex-col gap-4 rounded-(--radius-xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(20,32,49,0.92),rgba(12,20,33,0.94))] p-4 shadow-(--shadow-panel) md:flex-row md:items-center"
          >
            <div class="flex items-start gap-4 md:min-w-0 md:flex-1">
              <div
                class="h-28 w-20 shrink-0 overflow-hidden rounded-[18px] bg-(--color-surface-subtle)"
              >
                <SafeCover
                  path={book.coverPath ?? ''}
                  alt={`Portada de ${book.title}`}
                  className="h-full w-full object-cover"
                >
                  {#snippet fallback()}
                    <div
                      class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] px-2 text-center text-(--text-micro) uppercase tracking-[0.16em] text-(--color-primary)"
                    >
                      Sin portada
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
                    class="rounded-full border border-(--color-border) px-2 py-1 text-(--text-micro) uppercase tracking-[0.12em] text-(--color-text-muted)"
                  >
                    {getStateLabel(book)}
                  </span>
                </div>
                <p class="mt-1 text-sm text-(--color-text-muted)">
                  {book.author || 'Autor desconocido'}
                </p>

                <div
                  class="mt-4 max-w-xl space-y-2"
                  role="progressbar"
                  aria-valuenow={getSafeProgressPercentage(book)}
                  aria-valuemin="0"
                  aria-valuemax="100"
                >
                  <div class="h-2 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                    <div
                      class="h-full rounded-full bg-(--gradient-accent-h)"
                      style={`width: ${formatPercent(book)};`}
                    ></div>
                  </div>
                  <div
                    class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-(--color-text-muted)"
                  >
                    <span>{formatPercent(book)} leido</span>
                    <span>{book.minutesRead} min registrados</span>
                    <span>{book.currentPage}/{book.totalPages || '-'}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-2 md:justify-end">
              <Button
                variant="secondary"
                size="sm"
                class="rounded-xl"
                onclick={() => onOpenBook?.(book)}>Abrir</Button
              >
              <Button
                size="sm"
                class="rounded-xl bg-(--gradient-accent) !text-[#07111d]"
                onclick={() => onContinueReading?.(book)}
              >
                {getSafeProgressPercentage(book) > 0 ? 'Continuar lectura' : 'Empezar lectura'}
              </Button>
              <DropMenu position="bottom-right">
                {#snippet trigger()}
                  <button
                    type="button"
                    class="flex h-10 w-10 items-center justify-center rounded-xl border border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted)"
                    aria-label={`Opciones para ${book.title}`}
                  >
                    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                      <circle cx="5" cy="12" r="1.8"></circle>
                      <circle cx="12" cy="12" r="1.8"></circle>
                      <circle cx="19" cy="12" r="1.8"></circle>
                    </svg>
                  </button>
                {/snippet}
                <button
                  class="w-full px-4 py-2 text-left text-sm text-(--color-primary) hover:bg-(--color-border)"
                  onclick={() => onOpenBook?.(book)}>Abrir libro</button
                >
                <button
                  class="w-full px-4 py-2 text-left text-sm text-(--color-primary) hover:bg-(--color-border)"
                  onclick={() => onToggleFavorite?.(book)}
                >
                  {book.isFavorite ? 'Quitar de favoritos' : 'Marcar como favorito'}
                </button>
                <button
                  class="w-full px-4 py-2 text-left text-sm text-(--color-primary) hover:bg-(--color-border)"
                  onclick={() => onMarkCompleted?.(book)}>Marcar como completado</button
                >
                <button
                  class="w-full px-4 py-2 text-left text-sm text-(--color-border)"
                  onclick={() => onViewDetails?.(book)}>Ver detalles</button
                >
                <button
                  class="w-full px-4 py-2 text-left text-sm text-(--color-danger) hover:bg-(--color-border)"
                  onclick={() => onRemoveBook?.(book)}>Eliminar de la biblioteca</button
                >
              </DropMenu>
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
          <div
            class="flex h-14 w-14 items-center justify-center rounded-full border border-(--color-border) bg-(--color-surface-subtle)"
          >
            <svg
              class="h-5 w-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            >
              <path d="M12 5V19"></path>
              <path d="M5 12H19"></path>
            </svg>
          </div>
          <div>
            <p class="text-sm font-semibold text-(--color-primary)">Añadir libro</p>
            <p class="mt-1 text-xs">Importa nuevos archivos y manten tu biblioteca al dia.</p>
          </div>
        </button>
      </li>
    </ul>
  {/if}
</section>
