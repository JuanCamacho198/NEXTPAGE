/**
 * Normalizers, PD predicates, download resolution, merge and pagination math.
 * Pure functions — fully testable offline with fixture JSON.
 */
import { catalogError } from './errors';
import { BUILTIN_GUTENDEX, BUILTIN_OPENLIBRARY } from './CatalogProvider';
import type { CatalogBook, PagedResult } from './CatalogProvider';

export interface GutendexAuthor {
  name: string;
}
export interface GutendexRecord {
  id: number;
  title: string;
  authors?: GutendexAuthor[];
  copyright?: boolean | null;
  languages?: string[];
  subjects?: string[];
  formats?: Record<string, string>;
}

export interface OpenLibraryDoc {
  key: string;
  title: string;
  author_name?: string[];
  cover_i?: number;
  ebook_access?: string;
  language?: string[];
  subject?: string[];
}

/** Gutendex record is public-domain only when `copyright === false`. */
export function isGutendexPublicDomain(record: GutendexRecord): boolean {
  return record.copyright === false;
}

/** OL doc is usable only with full public ebook access (never borrowable). */
export function isOpenLibraryPublic(doc: OpenLibraryDoc): boolean {
  return doc.ebook_access === 'public';
}

export function openLibraryCoverUrl(coverId: number | undefined): string | null {
  if (typeof coverId !== 'number') return null;
  return `https://covers.openlibrary.org/b/id/${coverId}-M.jpg`;
}

/** Map a Gutendex record; null when in-copyright (excluded, never surfaced). */
export function mapGutendexBook(record: GutendexRecord): CatalogBook | null {
  if (!isGutendexPublicDomain(record)) return null;
  return {
    id: `gutendex:${record.id}`,
    provider: BUILTIN_GUTENDEX,
    title: record.title,
    authors: (record.authors ?? []).map((a) => a.name),
    coverUrl: null,
    languages: record.languages ?? [],
    subjects: record.subjects ?? [],
    downloadUrl: null,
  };
}

/** Map an OL doc; null when borrow-restricted or non-public. */
export function mapOpenLibraryDoc(doc: OpenLibraryDoc): CatalogBook | null {
  if (!isOpenLibraryPublic(doc)) return null;
  return {
    id: `openlibrary:${doc.key}`,
    provider: BUILTIN_OPENLIBRARY,
    title: doc.title,
    authors: doc.author_name ?? [],
    coverUrl: openLibraryCoverUrl(doc.cover_i),
    languages: doc.language ?? [],
    subjects: (doc.subject ?? []).slice(0, 8),
    downloadUrl: null,
  };
}

const EPUB_MIMES = ['application/epub+zip', 'application/x-mobipocket-ebook'];
const FALLBACK_MIMES = ['text/plain', 'text/html', 'application/pdf'];

/**
 * Deterministic download priority: EPUB-first (or fallback-first when
 * `preferEpub` is false). First `https://` match wins, else UNAVAILABLE_DOWNLOAD.
 */
export function resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
  const order = preferEpub
    ? [...EPUB_MIMES, ...FALLBACK_MIMES]
    : [...FALLBACK_MIMES, ...EPUB_MIMES];
  for (const mime of order) {
    const url = formats[mime];
    if (typeof url === 'string' && url.startsWith('https://')) return url;
  }
  throw catalogError('UNAVAILABLE_DOWNLOAD', 'no usable format');
}

/** Normalized title+author key used to pair OL docs with Gutendex records. */
export function normalizeMatchKey(title: string, authors: string[]): string {
  const norm = (s: string): string =>
    s
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();
  // Author names arrive in different orders per source ("Austen, Jane" vs
  // "Jane Austen"), so compare sorted word bags instead of raw strings.
  const authorKey = (s: string): string => norm(s).split(' ').filter(Boolean).sort().join(' ');
  return `${norm(title)}|${authors.map(authorKey).sort().join(',')}`;
}

/**
 * Merge sources: every Gutendex field wins; an empty Gutendex `coverUrl`
 * is filled from the matching OL doc. Unmatched OL books are appended.
 */
export function mergeResults(gutendexBooks: CatalogBook[], olBooks: CatalogBook[]): CatalogBook[] {
  const olByKey = new Map(olBooks.map((b) => [normalizeMatchKey(b.title, b.authors), b]));
  const usedOlKeys = new Set<string>();
  const merged = gutendexBooks.map((g) => {
    const key = normalizeMatchKey(g.title, g.authors);
    const match = olByKey.get(key);
    if (match && !g.coverUrl && match.coverUrl) {
      usedOlKeys.add(key);
      return { ...g, coverUrl: match.coverUrl };
    }
    if (match) usedOlKeys.add(key);
    return g;
  });
  for (const ol of olBooks) {
    if (!usedOlKeys.has(normalizeMatchKey(ol.title, ol.authors))) merged.push(ol);
  }
  return merged;
}

/** Gutendex `count` wins when present, else OL `numFound`. */
export function resolveTotalCount(gutendexCount: number | null, olNumFound: number): number {
  if (typeof gutendexCount === 'number' && gutendexCount >= 0) return gutendexCount;
  return Math.max(0, olNumFound);
}

/** Next 1-based page, or null when `offset + pageLength` reached `totalCount`. */
export function computeNextPage(
  page: number,
  pageLength: number,
  totalCount: number,
): number | null {
  const offset = (page - 1) * pageLength;
  return offset + pageLength < totalCount ? page + 1 : null;
}

export function toPagedResult(
  results: CatalogBook[],
  page: number,
  totalCount: number,
): PagedResult {
  return { results, nextPage: computeNextPage(page, results.length, totalCount), totalCount };
}
