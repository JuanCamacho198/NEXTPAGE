<script lang="ts">
  import SafeCover from "./SafeCover.svelte";
  import DropMenu from "../ui/DropMenu.svelte";
  import CollectionBadge from "./CollectionBadge.svelte";
  import type { LibraryBookDto, CollectionDto } from "../../types";
  import type { MessageKey } from "../../i18n";

  const LIBRARY_VIEW_MODE = {
    LIST: "list",
    GRID: "grid",
  } as const;

  type LibraryViewMode = (typeof LIBRARY_VIEW_MODE)[keyof typeof LIBRARY_VIEW_MODE];

  type Props = {
    books: LibraryBookDto[];
    collections?: CollectionDto[];
    selectedBookId?: string | null;
    selectedCollectionId?: string | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    viewMode?: LibraryViewMode;
    onSelect?: (book: LibraryBookDto) => void;
    onOpen?: (book: LibraryBookDto) => void;
    onHide?: (book: LibraryBookDto) => void;
    onEdit?: (book: LibraryBookDto) => void;
    onToggleView?: (mode: LibraryViewMode) => void;
    onCollectionSelect?: (collectionId: string | null) => void;
    onManageCollections?: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    books,
    collections = [],
    selectedBookId = null,
    selectedCollectionId = null,
    isLoading = false,
    disabledReason = null,
    viewMode = LIBRARY_VIEW_MODE.LIST,
    onSelect,
    onOpen,
    onHide,
    onEdit,
    onToggleView,
    onCollectionSelect,
    onManageCollections,
    t,
  }: Props = $props();

  let searchQuery = $state("");
  let debounceTimer: ReturnType<typeof setTimeout> | undefined = $state(undefined);

  function handleSearchInput(e: Event) {
    const target = e.target as HTMLInputElement;
    searchQuery = target.value;
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {}, 300);
  }

  function clearSearch() {
    searchQuery = "";
    clearTimeout(debounceTimer);
  }

  let filteredBooks = $derived.by(() => {
    const query = searchQuery.toLowerCase().trim();
    return books.filter((b) => {
      const matchesSearch =
        !query ||
        b.title.toLowerCase().includes(query) ||
        (b.author?.toLowerCase().includes(query) ?? false);
      const matchesCollection =
        !selectedCollectionId || b.collectionIds?.includes(Number(selectedCollectionId));
      return matchesSearch && matchesCollection;
    });
  });

  const formatUpdatedAt = (iso: string) => {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) {
      return t("settings.unknownBook");
    }
    return parsed.toLocaleDateString();
  };

  const formatProgress = (progress: number) => `${Math.round(progress)}%`;

</script>

<section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
  <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
    <div class="flex items-center gap-3">
      <h2 class="text-lg font-semibold text-[var(--color-primary)]">{t("library.title")}</h2>
      {#if collections.length > 0}
        <select
          class="rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] px-2 py-1 text-sm text-[var(--color-primary)]"
          value={selectedCollectionId ?? ""}
          onchange={(e) => {
            const value = (e.target as HTMLSelectElement).value;
            onCollectionSelect?.(value ? value : null);
          }}
        >
          <option value="">All</option>
          {#each collections as collection}
            <option value={collection.id}>{collection.name}</option>
          {/each}
        </select>
        <button
          type="button"
          class="text-xs text-[var(--color-text-muted)] hover:text-[var(--color-primary)]"
          onclick={onManageCollections}
        >
          Manage
        </button>
      {/if}
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <div class="relative">
        <svg class="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          type="text"
          placeholder={t("library.searchPlaceholder")}
          class="h-8 w-40 rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] pl-9 pr-8 text-sm text-[var(--color-text)] placeholder-[var(--color-text-muted)] focus:border-[var(--color-primary)] focus:outline-none"
          value={searchQuery}
          oninput={handleSearchInput}
        />
        {#if searchQuery}
          <button
            type="button"
            class="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] hover:text-[var(--color-text)]"
            aria-label="Clear search"
            onclick={clearSearch}
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        {/if}
      </div>
      <div class="inline-flex rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] p-1">
      <button
        type="button"
        class={`rounded px-3 py-1 text-xs font-medium ${viewMode === LIBRARY_VIEW_MODE.LIST ? "bg-[var(--color-surface)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
        onclick={() => onToggleView?.(LIBRARY_VIEW_MODE.LIST)}
      >
        {t("library.list")}
      </button>
      <button
        type="button"
        class={`rounded px-3 py-1 text-xs font-medium ${viewMode === LIBRARY_VIEW_MODE.GRID ? "bg-[var(--color-surface)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
        onclick={() => onToggleView?.(LIBRARY_VIEW_MODE.GRID)}
      >
        {t("library.grid")}
      </button>
    </div>
  </div>

  {#if disabledReason}
    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      {disabledReason}
    </div>
  {:else if isLoading}
    <p class="text-sm text-[var(--color-text-muted)]">{t("library.loading")}</p>
{:else if books.length === 0}
    <p class="text-sm text-[var(--color-text-muted)]">{t("library.empty")}</p>
  {:else if filteredBooks.length === 0}
    <p class="text-sm text-[var(--color-text-muted)]">{searchQuery ? t("library.searchNoResults") : t("library.empty")}</p>
  {:else}
    <p class="mb-3 text-xs text-[var(--color-text-muted)]">
      {t("library.searchResults", { count: filteredBooks.length, total: books.length })}
    </p>
    {#if viewMode === LIBRARY_VIEW_MODE.GRID}
      <ul class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {#each filteredBooks as book}
          <li class={`group relative min-w-0 rounded-xl border p-3 ${selectedBookId === book.id ? "border-[var(--color-primary)] bg-[color:color-mix(in_srgb,var(--color-primary)_10%,var(--color-surface))]" : "border-[color:var(--color-border)] bg-[var(--color-surface)]"}`}>
            <button
              type="button"
              class="absolute right-2 top-2 z-10 hidden rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-2 py-1 text-xs text-[var(--color-text-muted)] opacity-0 transition-opacity group-hover:opacity-100 hover:bg-[color:var(--color-border)]"
              onclick={(e) => {
                e.stopPropagation();
                onEdit?.(book);
              }}
            >
              {t("library.editMetadata.title")}
            </button>
            <div class="mb-2 flex items-start justify-end">
              <DropMenu position="bottom-right">
                {#snippet trigger()}
                  <button
                    type="button"
                    class="rounded-md border border-[color:var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"
                  >
                    ...
                  </button>
                {/snippet}
                <button
                  type="button"
                  class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                  onclick={() => onEdit?.(book)}
                >
                  {t("library.editMetadata.title")}
                </button>
                <button
                  type="button"
                  class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                  onclick={() => onHide?.(book)}
                >
                  {t("library.hide")}
                </button>
              </DropMenu>
            </div>
            <button type="button" class="w-full text-left" onclick={() => onSelect?.(book)}>
              <SafeCover 
                path={book.coverPath ?? ""} 
                alt={`Cover for ${book.title}`} 
                className="mb-2 h-32 w-full rounded object-cover"
              >
                {#snippet fallback()}
                  <div class="mb-2 flex h-32 w-full items-center justify-center rounded bg-[var(--color-background)] text-sm text-[var(--color-text-muted)]">
                    {t("library.noCover")}
                  </div>
                {/snippet}
              </SafeCover>
              <p class="line-clamp-2 min-w-0 break-words text-sm font-semibold text-[var(--color-primary)]">{book.title}</p>
              <p class="line-clamp-1 min-w-0 truncate text-xs text-[var(--color-text-muted)]">{book.author || t("app.unknownAuthor")}</p>
              <p class="mt-2 min-w-0 truncate text-xs text-[var(--color-text-muted)]">{formatProgress(book.progressPercentage)} · {book.minutesRead} {t("library.min")}</p>
              {#if book.collectionIds && book.collectionIds.length > 0}
                <div class="mt-2 flex flex-wrap gap-1">
                  {#each collections.filter(c => book.collectionIds?.includes(c.id)) as collection}
                    <CollectionBadge {collection} />
                  {/each}
                </div>
              {/if}
            </button>
            <button
              type="button"
              class="mt-3 w-full rounded-md bg-[var(--color-primary)] px-3 py-1.5 text-xs font-medium text-[var(--color-background)] hover:opacity-90"
              onclick={() => onOpen?.(book)}
            >
              {t("library.open")}
            </button>
          </li>
        {/each}
      </ul>
    {:else}
      <ul class="space-y-2">
        {#each filteredBooks as book}
          <li class={`min-w-0 rounded-xl border p-3 ${selectedBookId === book.id ? "border-[var(--color-primary)] bg-[color:color-mix(in_srgb,var(--color-primary)_10%,var(--color-surface))]" : "border-[color:var(--color-border)] bg-[var(--color-surface)]"}`}>
            <div class="flex items-start gap-3">
              <button type="button" class="min-w-0 flex-1 text-left" onclick={() => onSelect?.(book)}>
                <div class="flex items-start gap-3">
                  <SafeCover 
                    path={book.coverPath ?? ""} 
                    alt={`Cover for ${book.title}`} 
                    className="h-14 w-10 rounded object-cover"
                  >
                    {#snippet fallback()}
                      <div class="flex h-14 w-10 items-center justify-center rounded bg-[var(--color-background)] text-[10px] uppercase text-[var(--color-text-muted)]">
                        {t("library.cover")}
                      </div>
                    {/snippet}
                  </SafeCover>
                  <div class="min-w-0 flex-1">
                    <p class="line-clamp-2 min-w-0 break-words text-sm font-semibold text-[var(--color-primary)]">{book.title}</p>
                    <p class="truncate text-xs text-[var(--color-text-muted)]">{book.author || t("app.unknownAuthor")} · {book.format.toUpperCase()}</p>
                    <p class="mt-1 min-w-0 truncate text-xs text-[var(--color-text-muted)]">
                      {book.currentPage}/{book.totalPages || "-"} · {formatProgress(book.progressPercentage)} · {t("library.updated")} {formatUpdatedAt(book.updatedAt)}
                    </p>
                    {#if book.collectionIds && book.collectionIds.length > 0}
                      <div class="mt-1 flex flex-wrap gap-1">
                        {#each collections.filter(c => book.collectionIds?.includes(c.id)) as collection}
                          <CollectionBadge {collection} />
                        {/each}
                      </div>
                    {/if}
                  </div>
                </div>
              </button>
              <button
                type="button"
                class="rounded-md bg-[var(--color-primary)] px-3 py-1.5 text-xs font-medium text-[var(--color-background)] hover:opacity-90"
                onclick={() => onOpen?.(book)}
              >
                {t("library.open")}
              </button>
              <DropMenu position="bottom-right">
                {#snippet trigger()}
                  <button
                    type="button"
                    class="rounded-md border border-[color:var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-muted)]"
                  >
                    ...
                  </button>
                {/snippet}
                <button
                  type="button"
                  class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                  onclick={() => onEdit?.(book)}
                >
                  {t("library.editMetadata.title")}
                </button>
                <button
                  type="button"
                  class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                  onclick={() => onHide?.(book)}
                >
                  {t("library.hide")}
                </button>
              </DropMenu>
            </div>
          </li>
        {/each}
      </ul>
    {/if}
  {/if}
</section>
