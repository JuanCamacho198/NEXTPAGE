<script lang="ts">
  import type { SearchBookTextResponse, SearchNavigationTarget, SearchResult } from "../../types";

  type Props = {
    bookId?: string | null;
    disabledReason?: string | null;
    isSearching?: boolean;
    response: SearchBookTextResponse | null;
    onSearch?: (query: string, page: number) => void;
    onJump?: (target: SearchNavigationTarget) => void;
  };

  let {
    bookId = null,
    disabledReason = null,
    isSearching = false,
    response,
    onSearch,
    onJump,
  }: Props = $props();

  let query = $state("");

  const hasResults = $derived((response?.items.length ?? 0) > 0);
  const isNoMatch = $derived(Boolean(response) && (response?.items.length ?? 0) === 0);
  const currentPage = $derived(response?.page ?? 1);
  const pageSize = $derived(response?.pageSize ?? 20);
  const total = $derived(response?.total ?? 0);
  const hasMore = $derived(currentPage * pageSize < total);

  const runSearch = (page = 1) => {
    if (!bookId || !query.trim()) {
      return;
    }

    onSearch?.(query.trim(), page);
  };

  const jumpTo = (item: SearchResult, index: number) => {
    onJump?.({
      resultId: `${item.chunkId}:${index}`,
      locator: item.locator,
      snippet: item.snippet,
    });
  };
</script>

<section class="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
  <h3 class="mb-3 text-base font-semibold text-slate-800">In-Book Search</h3>

  {#if disabledReason}
    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      {disabledReason}
    </div>
  {:else}
    <form
      class="mb-3 flex gap-2"
      onsubmit={(event) => {
        event.preventDefault();
        runSearch(1);
      }}
    >
      <input
        type="text"
        class="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm"
        bind:value={query}
        placeholder="Search text in this book"
        disabled={isSearching || !bookId}
      />
      <button
        type="submit"
        class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
        disabled={isSearching || !bookId || !query.trim()}
      >
        {isSearching ? "Searching..." : "Search"}
      </button>
    </form>

    {#if hasResults}
      <ul class="space-y-2">
        {#each response?.items ?? [] as item, index}
          <li>
            <button
              type="button"
              class="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-left hover:bg-slate-100"
              onclick={() => jumpTo(item, index)}
            >
              <p class="text-sm text-slate-800">{item.snippet}</p>
              <p class="mt-1 text-xs text-slate-500">Locator: {item.locator}</p>
            </button>
          </li>
        {/each}
      </ul>

      <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
        <span>Page {currentPage} · {Math.min(total, pageSize * currentPage)} / {total} matches</span>
        <div class="flex gap-1">
          <button
            type="button"
            class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50 disabled:opacity-50"
            onclick={() => runSearch(currentPage - 1)}
            disabled={currentPage <= 1 || isSearching}
          >
            Prev
          </button>
          <button
            type="button"
            class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50 disabled:opacity-50"
            onclick={() => runSearch(currentPage + 1)}
            disabled={!hasMore || isSearching}
          >
            Next
          </button>
        </div>
      </div>
    {:else if isNoMatch}
      <p class="text-sm text-slate-500">No matches found for this query.</p>
    {/if}
  {/if}
</section>
