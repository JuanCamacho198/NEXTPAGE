<script lang="ts">
  import type { LibraryBookDto } from "../../types";

  const LIBRARY_VIEW_MODE = {
    LIST: "list",
    GRID: "grid",
  } as const;

  type LibraryViewMode = (typeof LIBRARY_VIEW_MODE)[keyof typeof LIBRARY_VIEW_MODE];

  type Props = {
    books: LibraryBookDto[];
    selectedBookId?: string | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    viewMode?: LibraryViewMode;
    onSelect?: (book: LibraryBookDto) => void;
    onOpen?: (book: LibraryBookDto) => void;
    onToggleView?: (mode: LibraryViewMode) => void;
  };

  let {
    books,
    selectedBookId = null,
    isLoading = false,
    disabledReason = null,
    viewMode = LIBRARY_VIEW_MODE.LIST,
    onSelect,
    onOpen,
    onToggleView,
  }: Props = $props();

  const formatUpdatedAt = (iso: string) => {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) {
      return "Unknown";
    }

    return parsed.toLocaleDateString();
  };

  const formatProgress = (progress: number) => `${Math.round(progress)}%`;
</script>

<section class="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
  <div class="mb-4 flex items-center justify-between">
    <h2 class="text-lg font-semibold text-slate-800">Library</h2>
    <div class="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-1">
      <button
        type="button"
        class="rounded px-3 py-1 text-xs font-medium"
        class:bg-white={viewMode === LIBRARY_VIEW_MODE.LIST}
        class:text-slate-800={viewMode === LIBRARY_VIEW_MODE.LIST}
        class:text-slate-500={viewMode !== LIBRARY_VIEW_MODE.LIST}
        onclick={() => onToggleView?.(LIBRARY_VIEW_MODE.LIST)}
      >
        List
      </button>
      <button
        type="button"
        class="rounded px-3 py-1 text-xs font-medium"
        class:bg-white={viewMode === LIBRARY_VIEW_MODE.GRID}
        class:text-slate-800={viewMode === LIBRARY_VIEW_MODE.GRID}
        class:text-slate-500={viewMode !== LIBRARY_VIEW_MODE.GRID}
        onclick={() => onToggleView?.(LIBRARY_VIEW_MODE.GRID)}
      >
        Grid
      </button>
    </div>
  </div>

  {#if disabledReason}
    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      {disabledReason}
    </div>
  {:else if isLoading}
    <p class="text-sm text-slate-500">Loading library...</p>
  {:else if books.length === 0}
    <p class="text-sm text-slate-500">No books available.</p>
  {:else if viewMode === LIBRARY_VIEW_MODE.GRID}
    <ul class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {#each books as book}
        <li class="rounded-xl border p-3" class:border-blue-500={selectedBookId === book.id} class:bg-blue-50={selectedBookId === book.id} class:border-slate-200={selectedBookId !== book.id} class:bg-white={selectedBookId !== book.id}>
          <button type="button" class="w-full text-left" onclick={() => onSelect?.(book)}>
            {#if book.coverPath}
              <img src={book.coverPath} alt={`Cover for ${book.title}`} class="mb-2 h-32 w-full rounded object-cover" />
            {:else}
              <div class="mb-2 flex h-32 w-full items-center justify-center rounded bg-slate-100 text-sm text-slate-500">
                No cover
              </div>
            {/if}
            <p class="line-clamp-1 text-sm font-semibold text-slate-800">{book.title}</p>
            <p class="line-clamp-1 text-xs text-slate-500">{book.author || "Unknown author"}</p>
            <p class="mt-2 text-xs text-slate-500">{formatProgress(book.progressPercentage)} · {book.minutesRead} min</p>
          </button>
          <button
            type="button"
            class="mt-3 w-full rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700"
            onclick={() => onOpen?.(book)}
          >
            Open
          </button>
        </li>
      {/each}
    </ul>
  {:else}
    <ul class="space-y-2">
      {#each books as book}
        <li class="rounded-xl border p-3" class:border-blue-500={selectedBookId === book.id} class:bg-blue-50={selectedBookId === book.id} class:border-slate-200={selectedBookId !== book.id} class:bg-white={selectedBookId !== book.id}>
          <div class="flex items-start gap-3">
            <button type="button" class="min-w-0 flex-1 text-left" onclick={() => onSelect?.(book)}>
              <div class="flex items-start gap-3">
                {#if book.coverPath}
                  <img src={book.coverPath} alt={`Cover for ${book.title}`} class="h-14 w-10 rounded object-cover" />
                {:else}
                  <div class="flex h-14 w-10 items-center justify-center rounded bg-slate-100 text-[10px] uppercase text-slate-500">
                    Cover
                  </div>
                {/if}
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-slate-800">{book.title}</p>
                  <p class="truncate text-xs text-slate-500">{book.author || "Unknown author"} · {book.format.toUpperCase()}</p>
                  <p class="mt-1 text-xs text-slate-500">
                    {book.currentPage}/{book.totalPages || "-"} · {formatProgress(book.progressPercentage)} · Updated {formatUpdatedAt(book.updatedAt)}
                  </p>
                </div>
              </div>
            </button>
            <button
              type="button"
              class="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700"
              onclick={() => onOpen?.(book)}
            >
              Open
            </button>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</section>
