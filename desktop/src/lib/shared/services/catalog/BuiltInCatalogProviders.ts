/**
 * Built-in CatalogProvider adapters over the raw datasources.
 * Gutendex is metadata/download authority; Open Library enriches + cover
 * fallback at the composite level. Book-id formats stay byte-for-byte:
 * `gutendex:<numericId>` / `openlibrary:<key>`.
 */
import { catalogError } from './errors';
import { BUILTIN_GUTENDEX, BUILTIN_OPENLIBRARY } from './CatalogProvider';
import type {
  CatalogBook,
  CatalogFeaturedSort,
  CatalogProvider,
  CatalogSource,
  CatalogSourceInfo,
  PagedResult,
} from './CatalogProvider';
import { GutendexDataSource } from './GutendexDataSource';
import { OpenLibraryDataSource } from './OpenLibraryDataSource';
import { resolveDownloadUrl, toPagedResult } from './mappers';
import { MIN_PAGE_SIZE } from './policy';

const GUTENDEX_ID_PREFIX = 'gutendex:';

export class GutendexCatalogProvider implements CatalogProvider {
  constructor(private readonly ds: GutendexDataSource = new GutendexDataSource()) {}

  async search(query: string, page: number): Promise<PagedResult> {
    const { books, totalCount } = await this.ds.search(query, page);
    return toPagedResult(books, page, totalCount);
  }

  /** Gutendex detail by numeric id; malformed ids reject NOT_FOUND. */
  async getDetails(id: string): Promise<CatalogBook> {
    if (!id.startsWith(GUTENDEX_ID_PREFIX)) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    const numericId = Number(id.slice(GUTENDEX_ID_PREFIX.length));
    if (!Number.isInteger(numericId) || numericId < 1) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    return this.ds.getById(numericId);
  }

  /** Featured rails are served by Gutendex alone (POPULAR / NEWEST). */
  async featured(sort: CatalogFeaturedSort, limit: number): Promise<PagedResult> {
    if (!Number.isInteger(limit) || limit < 1) {
      throw catalogError('INVALID_PAGE', `limit must be >= 1, got ${limit}`);
    }
    // The courtesy window clamps small fetches up to MIN_PAGE_SIZE; slice
    // back to the requested rail size so rails never over-render.
    const { books, totalCount } = await this.ds.featured(sort, 1, Math.max(limit, MIN_PAGE_SIZE));
    const sliced = books.slice(0, limit);
    return toPagedResult(sliced, 1, totalCount);
  }

  supportsFeatured(_sort: CatalogFeaturedSort): boolean {
    return true;
  }

  /**
   * Gutendex owns exactly one source; anything else fails closed with an
   * empty page (never a crash, never a silent composite search).
   */
  async searchSource(sourceId: CatalogSource, query: string, page: number): Promise<PagedResult> {
    if (sourceId !== BUILTIN_GUTENDEX) {
      return { results: [], nextPage: null, totalCount: 0 };
    }
    return this.search(query, page);
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }

  listSources(): CatalogSourceInfo[] {
    return [{ sourceId: BUILTIN_GUTENDEX, name: 'Gutendex', kind: 'builtin' }];
  }
}

export class OpenLibraryCatalogProvider implements CatalogProvider {
  constructor(private readonly ds: OpenLibraryDataSource = new OpenLibraryDataSource()) {}

  async search(query: string, page: number): Promise<PagedResult> {
    const { books, totalCount } = await this.ds.search(query, page);
    return toPagedResult(books, page, totalCount);
  }

  /** OL ids are not detail-resolvable (NOT_FOUND — preserved contract). */
  async getDetails(id: string): Promise<CatalogBook> {
    throw catalogError('NOT_FOUND', `openlibrary ids are not detail-resolvable: ${id}`);
  }

  // `featured` / `supportsFeatured` are deliberately left fail-closed.
  // `/trending/*.json` returns 100 works but is dominated by
  // borrow-restricted in-copyright titles that mapOpenLibraryDoc drops, its
  // payloads are 274-319 KB, and the endpoint proved flaky — so no OL rail,
  // no new OL endpoint, and the rail simply auto-hides. Mirrors Android.
  async featured(_sort: CatalogFeaturedSort, limit: number): Promise<PagedResult> {
    if (!Number.isInteger(limit) || limit < 1) {
      throw catalogError('INVALID_PAGE', `limit must be >= 1, got ${limit}`);
    }
    return { results: [], nextPage: null, totalCount: 0 };
  }

  supportsFeatured(_sort: CatalogFeaturedSort): boolean {
    return false;
  }

  /**
   * Open Library owns exactly one source; anything else fails closed with
   * an empty page (never a crash, never a silent composite search).
   */
  async searchSource(sourceId: CatalogSource, query: string, page: number): Promise<PagedResult> {
    if (sourceId !== BUILTIN_OPENLIBRARY) {
      return { results: [], nextPage: null, totalCount: 0 };
    }
    return this.search(query, page);
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }

  listSources(): CatalogSourceInfo[] {
    return [{ sourceId: BUILTIN_OPENLIBRARY, name: 'Open Library', kind: 'builtin' }];
  }
}
