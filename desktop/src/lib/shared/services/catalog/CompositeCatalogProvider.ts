/**
 * CompositeCatalogProvider — single hardcoded composite behind the port.
 * Parallel fan-out to both sources; Gutendex wins, OL fills cover gaps.
 * Burst searches are trailing-edge debounced; page < 1 rejects before I/O.
 */
import { catalogError } from './errors';
import type { CatalogBook, CatalogProvider, PagedResult } from './CatalogProvider';
import {
  DETAIL_TTL_S,
  PAGE_TTL_S,
  detailCacheKey,
  pageCacheKey,
  type DiscoverCacheStore,
} from './DiscoverCache';
import { GutendexDataSource } from './GutendexDataSource';
import { OpenLibraryDataSource } from './OpenLibraryDataSource';
import { mergeResults, resolveDownloadUrl, resolveTotalCount, toPagedResult } from './mappers';
import { DEBOUNCE_MS, DEFAULT_PAGE_SIZE, clampPageSize, createSearchDebouncer } from './policy';

export interface CompositeOptions {
  pageSize?: number;
  debounceMs?: number;
  cache?: DiscoverCacheStore | null;
  nowEpochSecs?: () => number;
}

const GUTENDEX_ID_PREFIX = 'gutendex:';

export class CompositeCatalogProvider implements CatalogProvider {
  private readonly pageSize: number;
  private readonly debounced: { search: (query: string, page: number) => Promise<PagedResult> };
  private readonly cache: DiscoverCacheStore | null;
  private readonly nowEpochSecs: () => number;

  constructor(
    private readonly gutendex: GutendexDataSource = new GutendexDataSource(),
    private readonly openLibrary: OpenLibraryDataSource = new OpenLibraryDataSource(),
    options: CompositeOptions = {},
  ) {
    this.pageSize = clampPageSize(options.pageSize ?? DEFAULT_PAGE_SIZE);
    this.cache = options.cache ?? null;
    this.nowEpochSecs = options.nowEpochSecs ?? (() => Math.floor(Date.now() / 1000));
    this.debounced = createSearchDebouncer(
      (query, page) => this.executeSearch(query, page),
      options.debounceMs ?? DEBOUNCE_MS,
    );
  }

  /**
   * Debounced entry point: only the latest burst query issues network I/O.
   * Fresh cache entries return synchronously without waiting for debounce.
   */
  search(query: string, page: number): Promise<PagedResult> {
    if (!Number.isInteger(page) || page < 1) {
      return Promise.reject(catalogError('INVALID_PAGE', `page must be >= 1, got ${page}`));
    }
    const cached = this.readPageCache(query, page);
    if (cached) return Promise.resolve(cached);
    return this.debounced.search(query, page);
  }

  private async executeSearch(query: string, page: number): Promise<PagedResult> {
    // Re-check inside the debounce window: a concurrent caller may have filled it.
    const cached = this.readPageCache(query, page);
    if (cached) return cached;
    const [g, o] = await Promise.all([
      this.gutendex.search(query, page, this.pageSize),
      this.openLibrary.search(query, page, this.pageSize),
    ]);
    const results = mergeResults(g.books, o.books);
    const paged = toPagedResult(results, page, resolveTotalCount(g.totalCount, o.totalCount));
    this.cache?.put(pageCacheKey(query, page), JSON.stringify(paged), this.nowEpochSecs(), PAGE_TTL_S);
    return paged;
  }

  /**
   * Gutendex detail by numeric id; OL ids are not detail-resolvable (NOT_FOUND).
   * Details are cached 7d; the pure `resolveDownloadUrl` path never hits I/O.
   */
  async getDetails(id: string): Promise<CatalogBook> {
    if (!id.startsWith(GUTENDEX_ID_PREFIX)) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    const numericId = Number(id.slice(GUTENDEX_ID_PREFIX.length));
    if (!Number.isInteger(numericId) || numericId < 1) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    if (this.cache) {
      const hit = this.cache.get(detailCacheKey(id), this.nowEpochSecs());
      if (hit) return JSON.parse(hit) as CatalogBook;
    }
    const book = await this.gutendex.getById(numericId);
    this.cache?.put(detailCacheKey(id), JSON.stringify(book), this.nowEpochSecs(), DETAIL_TTL_S);
    return book;
  }

  private readPageCache(query: string, page: number): PagedResult | null {
    if (!this.cache) return null;
    const hit = this.cache.get(pageCacheKey(query, page), this.nowEpochSecs());
    return hit ? (JSON.parse(hit) as PagedResult) : null;
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }
}
