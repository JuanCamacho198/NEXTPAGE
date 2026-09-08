/**
 * CatalogProvider port — identical contract on desktop (TS) and Android (Kotlin).
 * Gutendex is metadata/download authority; Open Library enriches + cover fallback.
 */
import type { CatalogErrorCode } from './errors';

export type CatalogSource = 'gutendex' | 'openlibrary';

export interface CatalogBook {
  id: string;
  provider: CatalogSource;
  title: string;
  authors: string[];
  coverUrl: string | null;
  languages: string[];
  subjects: string[];
  downloadUrl: string | null;
}

export interface PagedResult {
  results: CatalogBook[];
  /** Next 1-based page, or null when the last page was reached. */
  nextPage: number | null;
  totalCount: number;
}

export interface CatalogProvider {
  /** `page` is 1-based; `page < 1` rejects with INVALID_PAGE before any I/O. */
  search(query: string, page: number): Promise<PagedResult>;
  /** Unknown id rejects with NOT_FOUND. */
  getDetails(id: string): Promise<CatalogBook>;
  /**
   * Pure function (no I/O): pick a download URL from a Gutendex `formats` map.
   * Throws UNAVAILABLE_DOWNLOAD when no usable URL exists.
   */
  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string;
}

export type { CatalogErrorCode };
