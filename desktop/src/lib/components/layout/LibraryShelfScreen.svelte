<script lang="ts">
  import Button from "../ui/Button.svelte";
  import DropMenu from "../ui/DropMenu.svelte";
  import SafeCover from "../library/SafeCover.svelte";
  import { getSafeProgressPercentage } from "$lib/stores/homeState";
  import type { LibraryBookDto } from "$lib/types";

  type ShelfBook = LibraryBookDto & {
    isFavorite?: boolean;
    toRead?: boolean;
    completed?: boolean;
  };

  type ShelfFilter = "all" | "reading" | "pending" | "completed" | "favorites";
  type ShelfSort = "date_added" | "last_read" | "progress" | "title";
  type ShelfView = "grid" | "list";

  type Props = {
    books: ShelfBook[];
    isImporting?: boolean;
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
    onImportBook,
    onOpenBook,
    onContinueReading,
    onToggleFavorite,
    onMarkCompleted,
    onViewDetails,
    onRemoveBook,
  }: Props = $props();

  let searchQuery = $state("");
  let activeFilter = $state<ShelfFilter>("all");
  let activeSort = $state<ShelfSort>("date_added");
  let activeView = $state<ShelfView>("grid");

  const FILTER_OPTIONS: Array<{ key: ShelfFilter; label: string }> = [
    { key: "all", label: "Todos" },
    { key: "reading", label: "Leyendo" },
    { key: "pending", label: "Pendientes" },
    { key: "completed", label: "Completados" },
    { key: "favorites", label: "Favoritos" },
  ];

  const SORT_OPTIONS: Array<{ key: ShelfSort; label: string }> = [
    { key: "date_added", label: "Fecha agregada" },
    { key: "last_read", label: "Ultima lectura" },
    { key: "progress", label: "Progreso" },
    { key: "title", label: "Titulo" },
  ];

  const getBookState = (book: ShelfBook): ShelfFilter => {
    const progress = getSafeProgressPercentage(book);

    if (book.completed || progress >= 100) {
      return "completed";
    }

    if (progress > 0) {
      return "reading";
    }

    if (book.isFavorite) {
      return "favorites";
    }

    return "pending";
  };

  const getStateLabel = (book: ShelfBook) => {
    if (book.completed || getSafeProgressPercentage(book) >= 100) {
      return "Completado";
    }

    if (getSafeProgressPercentage(book) > 0) {
      return "En lectura";
    }

    if (book.isFavorite) {
      return "Favorito";
    }

    return "Pendiente";
  };

  const getTimestamp = (book: ShelfBook) => {
    const parsed = Date.parse(book.updatedAt);
    return Number.isFinite(parsed) ? parsed : 0;
  };

  const totalBooks = $derived(books.length);
  const readingBooks = $derived(books.filter((book) => getSafeProgressPercentage(book) > 0 && getSafeProgressPercentage(book) < 100).length);
  const completedBooks = $derived(books.filter((book) => book.completed || getSafeProgressPercentage(book) >= 100).length);

  const filteredBooks = $derived.by(() => {
    const query = searchQuery.trim().toLowerCase();

    const visible = books.filter((book) => {
      const progress = getSafeProgressPercentage(book);
      const matchesSearch =
        query.length === 0 ||
        book.title.toLowerCase().includes(query) ||
        (book.author ?? "").toLowerCase().includes(query);

      if (!matchesSearch) {
        return false;
      }

      if (activeFilter === "all") {
        return true;
      }

      if (activeFilter === "favorites") {
        return Boolean(book.isFavorite);
      }

      if (activeFilter === "reading") {
        return progress > 0 && progress < 100;
      }

      if (activeFilter === "completed") {
        return Boolean(book.completed) || progress >= 100;
      }

      return progress === 0;
    });

    return visible.toSorted((left, right) => {
      if (activeSort === "title") {
        return left.title.localeCompare(right.title, "es");
      }

      if (activeSort === "progress") {
        return getSafeProgressPercentage(right) - getSafeProgressPercentage(left);
      }

      if (activeSort === "last_read") {
        return getTimestamp(right) - getTimestamp(left);
      }

      return getTimestamp(right) - getTimestamp(left);
    });
  });

  const formatPercent = (book: ShelfBook) => `${Math.round(getSafeProgressPercentage(book))}%`;
</script>

<section class="space-y-5">
  <div class="rounded-[28px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(17,30,48,0.94),rgba(10,18,31,0.94))] p-5 shadow-[0_24px_80px_rgba(3,10,20,0.38)]">
    <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div class="space-y-2">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight text-[var(--color-primary)]">Estantería</h1>
          <p class="mt-1 text-sm text-[var(--color-text-muted)]">Todos tus libros organizados en un solo lugar.</p>
        </div>

        <div class="flex flex-wrap gap-3 text-xs text-[var(--color-text-muted)]">
          <div class="rounded-full border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-1.5">
            {totalBooks} libros
          </div>
          <div class="rounded-full border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-1.5">
            {readingBooks} leyendo
          </div>
          <div class="rounded-full border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-1.5">
            {completedBooks} completados
          </div>
        </div>
      </div>

      <div class="flex w-full flex-col gap-3 xl:max-w-[640px]">
        <div class="flex flex-col gap-3 md:flex-row">
          <label class="group relative flex-1">
            <span class="sr-only">Buscar libros</span>
            <svg class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-muted)]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <circle cx="11" cy="11" r="7"></circle>
              <path d="M20 20L17 17"></path>
            </svg>
            <input
              type="text"
              class="h-11 w-full rounded-2xl border border-[color:var(--color-border)] bg-[rgba(8,17,31,0.72)] pl-10 pr-4 text-sm text-[var(--color-primary)] outline-none placeholder:text-[var(--color-text-muted)]"
              placeholder="Buscar por titulo o autor..."
              bind:value={searchQuery}
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 rounded-md border border-[color:var(--color-border)] px-1.5 py-0.5 text-[10px] text-[var(--color-text-muted)]">
              Ctrl K
            </span>
          </label>

          <Button onclick={onImportBook} disabled={isImporting} class="h-11 min-w-[170px] rounded-2xl bg-[linear-gradient(135deg,#4e8cff,#49d4ff)] !text-[#07111d] shadow-[0_18px_40px_rgba(73,212,255,0.2)]">
            {isImporting ? "Importando..." : "Importar libro"}
          </Button>
        </div>
      </div>
    </div>
  </div>

  <div class="rounded-[28px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_20px_64px_rgba(2,10,18,0.28)]">
    <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
      <div class="flex flex-wrap gap-2">
        {#each FILTER_OPTIONS as option}
          <button
            type="button"
            class={`rounded-2xl border px-3 py-2 text-xs font-medium transition ${activeFilter === option.key ? "border-[rgba(82,143,255,0.4)] bg-[rgba(78,140,255,0.22)] text-[var(--color-primary)]" : "border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] text-[var(--color-text-muted)] hover:text-[var(--color-primary)]"}`}
            onclick={() => {
              activeFilter = option.key;
            }}
          >
            {option.label}
          </button>
        {/each}
      </div>

      <div class="flex flex-col gap-3 md:flex-row md:items-center">
        <label class="flex items-center gap-2 rounded-2xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] px-3 py-2 text-xs text-[var(--color-text-muted)]">
          <span>Ordenar por</span>
          <select
            class="bg-transparent text-sm text-[var(--color-primary)] outline-none"
            bind:value={activeSort}
          >
            {#each SORT_OPTIONS as option}
              <option value={option.key}>{option.label}</option>
            {/each}
          </select>
        </label>

        <div class="inline-flex rounded-2xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] p-1">
          <button
            type="button"
            class={`flex h-9 w-10 items-center justify-center rounded-xl ${activeView === "grid" ? "bg-[rgba(78,140,255,0.2)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
            aria-label="Vista en cuadrícula"
            onclick={() => {
              activeView = "grid";
            }}
          >
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="4" y="4" width="6" height="6" rx="1"></rect>
              <rect x="14" y="4" width="6" height="6" rx="1"></rect>
              <rect x="4" y="14" width="6" height="6" rx="1"></rect>
              <rect x="14" y="14" width="6" height="6" rx="1"></rect>
            </svg>
          </button>
          <button
            type="button"
            class={`flex h-9 w-10 items-center justify-center rounded-xl ${activeView === "list" ? "bg-[rgba(78,140,255,0.2)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
            aria-label="Vista en lista"
            onclick={() => {
              activeView = "list";
            }}
          >
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M8 6H20"></path>
              <path d="M8 12H20"></path>
              <path d="M8 18H20"></path>
              <circle cx="4" cy="6" r="1"></circle>
              <circle cx="4" cy="12" r="1"></circle>
              <circle cx="4" cy="18" r="1"></circle>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>

  {#if activeView === "grid"}
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4 2xl:grid-cols-5">
      {#each filteredBooks as book}
        <article class="group flex min-h-[360px] flex-col rounded-[24px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(20,32,49,0.92),rgba(12,20,33,0.94))] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.22)]">
          <div class="mb-3 flex items-start justify-between gap-3">
            <span class="rounded-full border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-2.5 py-1 text-[10px] uppercase tracking-[0.16em] text-[var(--color-text-muted)]">
              {getStateLabel(book)}
            </span>
            <DropMenu position="bottom-right">
              {#snippet trigger()}
                <button
                  type="button"
                  class="flex h-9 w-9 items-center justify-center rounded-xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] text-[var(--color-text-muted)]"
                  aria-label={`Opciones para ${book.title}`}
                >
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                    <circle cx="5" cy="12" r="1.8"></circle>
                    <circle cx="12" cy="12" r="1.8"></circle>
                    <circle cx="19" cy="12" r="1.8"></circle>
                  </svg>
                </button>
              {/snippet}
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onOpenBook?.(book)}>Abrir libro</button>
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onToggleFavorite?.(book)}>
                {book.isFavorite ? "Quitar de favoritos" : "Marcar como favorito"}
              </button>
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onMarkCompleted?.(book)}>Marcar como completado</button>
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onViewDetails?.(book)}>Ver detalles</button>
              <button class="w-full px-4 py-2 text-left text-sm text-[#ff9fa5] hover:bg-[color:var(--color-border)]" onclick={() => onRemoveBook?.(book)}>Eliminar de la biblioteca</button>
            </DropMenu>
          </div>

          <div class="relative mb-4 aspect-[0.72] overflow-hidden rounded-[20px] bg-[rgba(255,255,255,0.03)]">
            <SafeCover path={book.coverPath ?? ""} alt={`Portada de ${book.title}`} className="h-full w-full object-cover">
              {#snippet fallback()}
                <div class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] px-6 text-center text-xs uppercase tracking-[0.18em] text-[var(--color-primary)]">
                  Sin portada
                </div>
              {/snippet}
            </SafeCover>
          </div>

          <div class="space-y-1">
            <h3 class="line-clamp-2 text-sm font-semibold text-[var(--color-primary)]">{book.title}</h3>
            <p class="line-clamp-1 text-xs text-[var(--color-text-muted)]">{book.author || "Autor desconocido"}</p>
          </div>

          <div class="mt-4 space-y-2">
            <div class="h-2 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
              <div
                class="h-full rounded-full bg-[linear-gradient(90deg,#4e8cff,#49d4ff)]"
                style={`width: ${formatPercent(book)};`}
              ></div>
            </div>
            <div class="flex items-center justify-between text-xs text-[var(--color-text-muted)]">
              <span>{formatPercent(book)} leido</span>
              <span>{book.minutesRead} min</span>
            </div>
          </div>

          <div class="mt-auto grid grid-cols-2 gap-2 pt-4">
            <Button variant="secondary" size="sm" class="rounded-xl" onclick={() => onOpenBook?.(book)}>
              Abrir libro
            </Button>
            <Button size="sm" class="rounded-xl bg-[linear-gradient(135deg,#4e8cff,#49d4ff)] !text-[#07111d]" onclick={() => onContinueReading?.(book)}>
              {getSafeProgressPercentage(book) > 0 ? "Continuar" : "Empezar"}
            </Button>
          </div>
        </article>
      {/each}

      <button
        type="button"
        class="flex min-h-[360px] flex-col items-center justify-center gap-4 rounded-[24px] border border-dashed border-[color:var(--color-border-strong)] bg-[rgba(255,255,255,0.02)] p-6 text-center text-[var(--color-text-muted)] transition hover:border-[rgba(78,140,255,0.5)] hover:text-[var(--color-primary)]"
        onclick={onImportBook}
      >
        <div class="flex h-16 w-16 items-center justify-center rounded-full border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)]">
          <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M12 5V19"></path>
            <path d="M5 12H19"></path>
          </svg>
        </div>
        <div>
          <p class="text-sm font-semibold text-[var(--color-primary)]">Añadir libro</p>
          <p class="mt-1 text-xs">Importa un nuevo archivo a tu biblioteca.</p>
        </div>
      </button>
    </div>
  {:else}
    <div class="space-y-3">
      {#each filteredBooks as book}
        <article class="flex flex-col gap-4 rounded-[24px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(20,32,49,0.92),rgba(12,20,33,0.94))] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.18)] md:flex-row md:items-center">
          <div class="flex items-start gap-4 md:min-w-0 md:flex-1">
            <div class="h-28 w-20 flex-shrink-0 overflow-hidden rounded-[18px] bg-[rgba(255,255,255,0.03)]">
              <SafeCover path={book.coverPath ?? ""} alt={`Portada de ${book.title}`} className="h-full w-full object-cover">
                {#snippet fallback()}
                  <div class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] px-2 text-center text-[10px] uppercase tracking-[0.16em] text-[var(--color-primary)]">
                    Sin portada
                  </div>
                {/snippet}
              </SafeCover>
            </div>

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="line-clamp-1 text-base font-semibold text-[var(--color-primary)]">{book.title}</h3>
                <span class="rounded-full border border-[color:var(--color-border)] px-2 py-1 text-[10px] uppercase tracking-[0.12em] text-[var(--color-text-muted)]">
                  {getStateLabel(book)}
                </span>
              </div>
              <p class="mt-1 text-sm text-[var(--color-text-muted)]">{book.author || "Autor desconocido"}</p>

              <div class="mt-4 max-w-xl space-y-2">
                <div class="h-2 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                  <div class="h-full rounded-full bg-[linear-gradient(90deg,#4e8cff,#49d4ff)]" style={`width: ${formatPercent(book)};`}></div>
                </div>
                <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[var(--color-text-muted)]">
                  <span>{formatPercent(book)} leido</span>
                  <span>{book.minutesRead} min registrados</span>
                  <span>{book.currentPage}/{book.totalPages || "-"}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2 md:justify-end">
            <Button variant="secondary" size="sm" class="rounded-xl" onclick={() => onOpenBook?.(book)}>Abrir</Button>
            <Button size="sm" class="rounded-xl bg-[linear-gradient(135deg,#4e8cff,#49d4ff)] !text-[#07111d]" onclick={() => onContinueReading?.(book)}>
              {getSafeProgressPercentage(book) > 0 ? "Continuar lectura" : "Empezar lectura"}
            </Button>
            <DropMenu position="bottom-right">
              {#snippet trigger()}
                <button
                  type="button"
                  class="flex h-10 w-10 items-center justify-center rounded-xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] text-[var(--color-text-muted)]"
                  aria-label={`Opciones para ${book.title}`}
                >
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                    <circle cx="5" cy="12" r="1.8"></circle>
                    <circle cx="12" cy="12" r="1.8"></circle>
                    <circle cx="19" cy="12" r="1.8"></circle>
                  </svg>
                </button>
              {/snippet}
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onOpenBook?.(book)}>Abrir libro</button>
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onToggleFavorite?.(book)}>
                {book.isFavorite ? "Quitar de favoritos" : "Marcar como favorito"}
              </button>
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onMarkCompleted?.(book)}>Marcar como completado</button>
              <button class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" onclick={() => onViewDetails?.(book)}>Ver detalles</button>
              <button class="w-full px-4 py-2 text-left text-sm text-[#ff9fa5] hover:bg-[color:var(--color-border)]" onclick={() => onRemoveBook?.(book)}>Eliminar de la biblioteca</button>
            </DropMenu>
          </div>
        </article>
      {/each}

      <button
        type="button"
        class="flex min-h-[120px] items-center justify-center gap-4 rounded-[24px] border border-dashed border-[color:var(--color-border-strong)] bg-[rgba(255,255,255,0.02)] p-6 text-left text-[var(--color-text-muted)] transition hover:border-[rgba(78,140,255,0.5)] hover:text-[var(--color-primary)]"
        onclick={onImportBook}
      >
        <div class="flex h-14 w-14 items-center justify-center rounded-full border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)]">
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M12 5V19"></path>
            <path d="M5 12H19"></path>
          </svg>
        </div>
        <div>
          <p class="text-sm font-semibold text-[var(--color-primary)]">Añadir libro</p>
          <p class="mt-1 text-xs">Importa nuevos archivos y manten tu biblioteca al dia.</p>
        </div>
      </button>
    </div>
  {/if}
</section>
