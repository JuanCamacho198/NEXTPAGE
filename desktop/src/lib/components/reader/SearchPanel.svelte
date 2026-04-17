<script lang="ts">
  import type { SearchBookTextResponse, SearchNavigationTarget, SearchResult } from "../../types";
  import type { MessageKey } from "../../i18n";

  type Props = {
    bookId?: string | null;
    disabledReason?: string | null;
    isSearching?: boolean;
    response: SearchBookTextResponse | null;
    onSearch?: (query: string, page: number) => void;
    onJump?: (target: SearchNavigationTarget) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    bookId = null,
    disabledReason = null,
    isSearching = false,
    response,
    onSearch,
    onJump,
    t,
  }: Props = $props();

  let query = $state("");

  const resultCount = () => response?.items.length ?? 0;
  const hasResults = () => resultCount() > 0;
  const isNoMatch = () => Boolean(response) && resultCount() === 0;
  const currentPage = () => response?.page ?? 1;
  const pageSize = () => response?.pageSize ?? 20;
  const total = () => response?.total ?? 0;
  const hasMore = () => currentPage() * pageSize() < total();

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

<section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
  <h3 class="mb-3 text-base font-semibold text-[var(--color-primary)]">{t("search.title")}</h3>

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
        class="min-w-0 flex-1 rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-primary)]"
        bind:value={query}
        placeholder={t("search.placeholder")}
        disabled={isSearching || !bookId}
      />
      <button
        type="submit"
        class="rounded-md bg-[var(--color-primary)] px-3 py-2 text-sm font-medium text-[var(--color-background)] hover:opacity-90 disabled:opacity-60"
        disabled={isSearching || !bookId || !query.trim()}
      >
        {isSearching ? t("search.searching") : t("search.search")}
      </button>
    </form>

    {#if hasResults()}
      <ul class="space-y-2">
        {#each response?.items ?? [] as item, index}
          <li>
            <button
              type="button"
              class="w-full rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] px-3 py-2 text-left hover:bg-[color:var(--color-border)]"
              onclick={() => jumpTo(item, index)}
            >
              <p class="text-sm text-[var(--color-primary)]">{item.snippet}</p>
              <p class="mt-1 text-xs text-[var(--color-text-muted)]">{t("search.locator")}: {item.locator}</p>
            </button>
          </li>
        {/each}
      </ul>

      <div class="mt-3 flex items-center justify-between text-xs text-[var(--color-text-muted)]">
        <span>{t("search.page")} {currentPage()} · {Math.min(total(), pageSize() * currentPage())} / {total()} {t("search.matches")}</span>
        <div class="flex gap-1">
          <button
            type="button"
            class="rounded border border-[color:var(--color-border)] px-2 py-1 text-[var(--color-primary)] hover:bg-[color:var(--color-border)] disabled:opacity-50"
            onclick={() => runSearch(currentPage() - 1)}
            disabled={currentPage() <= 1 || isSearching}
          >
            {t("search.prev")}
          </button>
          <button
            type="button"
            class="rounded border border-[color:var(--color-border)] px-2 py-1 text-[var(--color-primary)] hover:bg-[color:var(--color-border)] disabled:opacity-50"
            onclick={() => runSearch(currentPage() + 1)}
            disabled={!hasMore() || isSearching}
          >
            {t("search.next")}
          </button>
        </div>
      </div>
    {:else if isNoMatch()}
      <p class="text-sm text-[var(--color-text-muted)]">{t("search.noMatches")}</p>
    {/if}
  {/if}
</section>
