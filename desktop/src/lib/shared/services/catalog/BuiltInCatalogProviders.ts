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
  CatalogProvider,
  CatalogSourceInfo,
  PagedResult,
} from './CatalogProvider';
import { GutendexDataSource } from './GutendexDataSource';
import { OpenLibraryDataSource } from './OpenLibraryDataSource';
import { resolveDownloadUrl, toPagedResult } from './mappers';

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

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }

  listSources(): CatalogSourceInfo[] {
    return [{ sourceId: BUILTIN_OPENLIBRARY, name: 'Open Library', kind: 'builtin' }];
  }
}
