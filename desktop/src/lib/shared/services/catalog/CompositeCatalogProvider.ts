/**
 * CompositeCatalogProvider — single hardcoded composite behind the port.
 * Parallel fan-out to both sources; Gutendex wins, OL fills cover gaps.
 * Burst searches are trailing-edge debounced; page < 1 rejects before I/O.
 */
import { catalogError } from './errors';
import type { CatalogBook, CatalogProvider, PagedResult } from './CatalogProvider';
import { GutendexDataSource } from './GutendexDataSource';
import { OpenLibraryDataSource } from './OpenLibraryDataSource';
import { mergeResults, resolveDownloadUrl, resolveTotalCount, toPagedResult } from './mappers';
import { DEBOUNCE_MS, DEFAULT_PAGE_SIZE, clampPageSize, createSearchDebouncer } from './policy';

export interface CompositeOptions {
  pageSize?: number;
  debounceMs?: number;
}

const GUTENDEX_ID_PREFIX = 'gutendex:';

export class CompositeCatalogProvider implements CatalogProvider {
  private readonly pageSize: number;
  private readonly debounced: { search: (query: string, page: number) => Promise<PagedResult> };

  constructor(
    private readonly gutendex: GutendexDataSource = new GutendexDataSource(),
    private readonly openLibrary: OpenLibraryDataSource = new OpenLibraryDataSource(),
    options: CompositeOptions = {},
  ) {
    this.pageSize = clampPageSize(options.pageSize ?? DEFAULT_PAGE_SIZE);
    this.debounced = createSearchDebouncer(
      (query, page) => this.executeSearch(query, page),
      options.debounceMs ?? DEBOUNCE_MS,
    );
  }

  /** Debounced entry point: only the latest burst query issues network I/O. */
  search(query: string, page: number): Promise<PagedResult> {
    if (!Number.isInteger(page) || page < 1) {
      return Promise.reject(catalogError('INVALID_PAGE', `page must be >= 1, got ${page}`));
    }
    return this.debounced.search(query, page);
  }

  private async executeSearch(query: string, page: number): Promise<PagedResult> {
    const [g, o] = await Promise.all([
      this.gutendex.search(query, page, this.pageSize),
      this.openLibrary.search(query, page, this.pageSize),
    ]);
    const results = mergeResults(g.books, o.books);
    return toPagedResult(results, page, resolveTotalCount(g.totalCount, o.totalCount));
  }

  /** Gutendex detail by numeric id; OL ids are not detail-resolvable (NOT_FOUND). */
  async getDetails(id: string): Promise<CatalogBook> {
    if (!id.startsWith(GUTENDEX_ID_PREFIX)) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    const numericId = Number(id.slice(GUTENDEX_ID_PREFIX.length));
    if (!Number.isInteger(numericId) || numericId < 1) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    return this.gutendex.getById(numericId);
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }
}
