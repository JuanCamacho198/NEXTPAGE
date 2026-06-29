import { searchBookText } from '$lib/shared/api/tauriClient';
import type { SearchBookTextResponse, SearchNavigationTarget } from '$lib/shared/types';

type MaybeCommandError = Error & {
  commandError?: { code: string; message: string; recoverable: boolean };
};

class SearchDomainState {
  // ─── State ───
  searchResponse = $state<SearchBookTextResponse | null>(null);
  searchTargetLocator = $state<string | null>(null);
  isSearching = $state(false);
  searchUnavailableReason = $state<string | null>(null);

  // ─── Methods ───

  async handleSearch(activeBookId: string, query: string, page: number): Promise<void> {
    this.isSearching = true;

    try {
      this.searchResponse = await searchBookText({
        bookId: activeBookId,
        query,
        page,
        pageSize: 200,
      });
      this.searchUnavailableReason = null;
    } catch (error) {
      const typed = error as MaybeCommandError;
      if (typed.commandError?.recoverable) {
        this.searchUnavailableReason = typed.commandError.message;
      }
      this.searchResponse = null;
    } finally {
      this.isSearching = false;
    }
  }

  handleSearchJump(target: SearchNavigationTarget): void {
    this.searchTargetLocator = target.locator;
  }

  resetSearch(): void {
    this.searchResponse = null;
    this.searchTargetLocator = null;
    this.isSearching = false;
    this.searchUnavailableReason = null;
  }
}

export const searchState = new SearchDomainState();
export { SearchDomainState };
