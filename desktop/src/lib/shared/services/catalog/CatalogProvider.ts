/**
 * CatalogProvider port — identical contract on desktop (TS) and Android (Kotlin).
 * Gutendex is metadata/download authority; Open Library enriches + cover fallback.
 */
import { catalogError, type CatalogErrorCode } from './errors';

/**
 * Strict catalog source ids: 'builtin:<name>' for first-party sources,
 * 'addon:<addonId>' for registry addons (addonId = sha256(url)[0..16]).
 * Exact-prefix parsing only — no tolerant parsing, malformed ids rejected.
 */
export type CatalogSource = string & { readonly __catalogSource: true };

export const BUILTIN_GUTENDEX = 'builtin:gutendex' as CatalogSource;
export const BUILTIN_OPENLIBRARY = 'builtin:openlibrary' as CatalogSource;

/** Closed registry of first-party built-in source names (curated bundle included). */
const KNOWN_BUILTIN_NAMES = [
  'gutendex',
  'openlibrary',
  'standard-ebooks',
  'librivox',
  'wikisource',
  'faded-page',
] as const;

/** addonId = sha256(url) first 16 hex chars (design A6) — lowercase only. */
const ADDON_ID_RE = /^[0-9a-f]{16}$/;

export function addonSource(addonId: string): CatalogSource {
  return `addon:${addonId}` as CatalogSource;
}

/** Extract the addonId from an addon source id; null for non-addon sources. */
export function addonSourceIdOf(source: string): string | null {
  if (!source.startsWith('addon:')) return null;
  const id = source.slice('addon:'.length);
  return ADDON_ID_RE.test(id) ? id : null;
}

/** Exact-prefix parse: built-ins are a closed registry; addons require 16-hex ids. */
export function parseCatalogSource(raw: string): CatalogSource {
  if (raw.startsWith('builtin:')) {
    const name = raw.slice('builtin:'.length);
    if ((KNOWN_BUILTIN_NAMES as readonly string[]).includes(name)) {
      return raw as CatalogSource;
    }
  } else if (raw.startsWith('addon:') && ADDON_ID_RE.test(raw.slice('addon:'.length))) {
    return raw as CatalogSource;
  }
  throw catalogError('NOT_FOUND', `unknown catalog source ${raw}`);
}

export type CatalogSourceKind = 'builtin' | 'curated' | 'addon';

export interface CatalogSourceInfo {
  sourceId: CatalogSource;
  name: string;
  kind: CatalogSourceKind;
}

export interface CatalogBook {
  id: string;
  provider: string;
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

  /** Pure function (no I/O): the sources this provider can serve, in order. */
  listSources(): CatalogSourceInfo[];
}

export type { CatalogErrorCode };
