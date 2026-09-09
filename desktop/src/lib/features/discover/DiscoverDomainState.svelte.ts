import {
  isCatalogError,
  liveCatalogProvider,
} from '$lib/shared/services/catalog';
import type {
  CatalogBook,
  CatalogErrorCode,
  CatalogProvider,
} from '$lib/shared/services/catalog';

export type DiscoverStatus =
  | 'idle'
  | 'loading'
  | 'loadingMore'
  | 'loaded'
  | 'empty'
  | 'error'
  | 'offline';

export type DiscoverDetailStatus = 'closed' | 'loading' | 'loaded' | 'notFound' | 'error';

function statusForCode(code: CatalogErrorCode): DiscoverStatus {
  return code === 'NETWORK_ERROR' || code === 'RATE_LIMITED' ? 'offline' : 'error';
}

function codeOf(err: unknown): CatalogErrorCode {
  return isCatalogError(err) ? err.code : 'UPSTREAM_ERROR';
}

class DiscoverDomainState {
  query = $state('');
  status = $state<DiscoverStatus>('idle');
  books = $state<CatalogBook[]>([]);
  totalCount = $state(0);
  nextPage = $state<number | null>(null);
  activePage = $state(0);
  errorCode = $state<CatalogErrorCode | null>(null);
  detail = $state<CatalogBook | null>(null);
  detailStatus = $state<DiscoverDetailStatus>('closed');

  private lastAttemptedPage = 0;

  constructor(private readonly provider: CatalogProvider = liveCatalogProvider) {}

  setQuery(query: string): void {
    this.query = query;
    if (query.trim() === '') {
      this.resetToIdle();
    }
  }

  async searchFirstPage(): Promise<void> {
    if (this.query.trim() === '') {
      this.resetToIdle();
      return;
    }
    this.status = 'loading';
    this.errorCode = null;
    this.lastAttemptedPage = 1;
    try {
      const page = await this.provider.search(this.query, 1);
      this.books = page.results;
      this.totalCount = page.totalCount;
      this.nextPage = page.nextPage;
      this.activePage = 1;
      this.status = page.results.length === 0 ? 'empty' : 'loaded';
    } catch (err) {
      const code = codeOf(err);
      this.errorCode = code;
      this.status = statusForCode(code);
    }
  }

  async loadNextPage(): Promise<void> {
    const page = this.nextPage;
    if (page == null || this.status === 'loading' || this.status === 'loadingMore') {
      return;
    }
    this.status = 'loadingMore';
    this.lastAttemptedPage = page;
    try {
      const result = await this.provider.search(this.query, page);
      const seen = new Set(this.books.map((b) => b.id));
      for (const book of result.results) {
        if (!seen.has(book.id)) {
          seen.add(book.id);
          this.books.push(book);
        }
      }
      this.totalCount = result.totalCount;
      this.nextPage = result.nextPage;
      this.activePage = page;
      this.errorCode = null;
      this.status = 'loaded';
    } catch (err) {
      const code = codeOf(err);
      this.errorCode = code;
      this.status = statusForCode(code);
    }
  }

  async openDetail(id: string): Promise<void> {
    this.detailStatus = 'loading';
    this.detail = null;
    try {
      this.detail = await this.provider.getDetails(id);
      this.detailStatus = 'loaded';
    } catch (err) {
      const code = codeOf(err);
      this.detailStatus = code === 'NOT_FOUND' ? 'notFound' : 'error';
    }
  }

  dismissDetail(): void {
    this.detail = null;
    this.detailStatus = 'closed';
  }

  async retry(): Promise<void> {
    if (this.lastAttemptedPage <= 1) {
      await this.searchFirstPage();
      return;
    }
    this.nextPage = this.lastAttemptedPage;
    await this.loadNextPage();
  }

  private resetToIdle(): void {
    this.books = [];
    this.totalCount = 0;
    this.nextPage = null;
    this.activePage = 0;
    this.errorCode = null;
    this.status = 'idle';
    this.lastAttemptedPage = 0;
  }
}

export const discoverState = new DiscoverDomainState();
export { DiscoverDomainState };
