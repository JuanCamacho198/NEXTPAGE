import {
  BUILTIN_GUTENDEX,
  isCatalogError,
  liveCatalogProvider,
} from '$lib/shared/services/catalog';
import type {
  CatalogBook,
  CatalogErrorCode,
  CatalogFeaturedSort,
  CatalogProvider,
  CatalogSourceInfo,
} from '$lib/shared/services/catalog';

export type DiscoverStatus =
  'idle' | 'loading' | 'loadingMore' | 'loaded' | 'empty' | 'error' | 'offline';

export type DiscoverDetailStatus = 'closed' | 'loading' | 'loaded' | 'notFound' | 'error';

/** Rail visibility machine: unserved/error rails collapse to Hidden. */
export type DiscoverRailState =
  | { kind: 'Hidden' }
  | { kind: 'Loading' }
  | { kind: 'Loaded'; books: CatalogBook[]; totalCount: number };

export interface DiscoverRailSpec {
  title: string;
  sort: CatalogFeaturedSort;
  limit: number;
}

export const DISCOVER_RAIL_COUNT = 4;
export const DISCOVER_RAIL_LIMIT = 6;

/** Rail order: Recién agregados (NEWEST), Populares (POPULAR), Recomendados, Gutenberg. */
export const DISCOVER_RAIL_SPECS: readonly DiscoverRailSpec[] = [
  { title: 'Recién agregados', sort: 'NEWEST', limit: DISCOVER_RAIL_LIMIT },
  { title: 'Populares', sort: 'POPULAR', limit: DISCOVER_RAIL_LIMIT },
  { title: 'Recomendados', sort: 'POPULAR', limit: DISCOVER_RAIL_LIMIT },
  { title: 'Gutenberg', sort: 'NEWEST', limit: DISCOVER_RAIL_LIMIT },
];

/** Seven static chips; selection filters loaded rails client-side only (no catalog call). */
export const TRENDING_CHIPS: readonly string[] = [
  'Ficción',
  'Clásicos',
  'Aventura',
  'Misterio',
  'Romance',
  'Ciencia ficción',
  'Historia',
];

/**
 * Spanish chip label → English subject keywords. Catalog subjects arrive in
 * English (Gutendex/Open Library), so a normalized substring check alone
 * would miss (`ficción` vs `fiction`); keywords bridge the locale gap.
 * Pure client-side filter — never triggers a catalog call.
 */
const CHIP_KEYWORDS: Record<string, readonly string[]> = {
  Ficción: ['fiction'],
  Clásicos: ['classic'],
  Aventura: ['adventure'],
  Misterio: ['mystery', 'detective'],
  Romance: ['romance', 'love'],
  'Ciencia ficción': ['science'],
  Historia: ['history'],
};

function normalizeHaystack(value: string): string {
  return value.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/** True when a book matches a trending chip (label substring or keyword hit). */
export function matchesChip(book: CatalogBook, chip: string): boolean {
  const haystack = normalizeHaystack(
    `${book.title} ${book.authors.join(' ')} ${book.subjects.join(' ')}`,
  );
  const needle = normalizeHaystack(chip);
  if (needle !== '' && haystack.includes(needle)) return true;
  const keywords = CHIP_KEYWORDS[chip] ?? [];
  return keywords.some((keyword) => haystack.includes(keyword));
}

/** Client-side rail filter; `null` chip returns the slice untouched. */
export function filterBooksByChip(books: CatalogBook[], chip: string | null): CatalogBook[] {
  if (chip === null) return books;
  return books.filter((book) => matchesChip(book, chip));
}

/**
 * Static curated first slice for the Recomendados rail: visual parity without
 * blocking on sort-literal verification. Swapped for live data after the
 * Gutendex `featured()` literals prove out.
 */
const CURATED_FIRST_SLICE: readonly CatalogBook[] = [
  {
    id: 'curated:pride-and-prejudice',
    provider: 'curated',
    title: 'Pride and Prejudice',
    authors: ['Jane Austen'],
    coverUrl: null,
    languages: ['en'],
    subjects: ['Classic fiction'],
    downloadUrl: null,
  },
  {
    id: 'curated:moby-dick',
    provider: 'curated',
    title: 'Moby Dick; Or, The Whale',
    authors: ['Herman Melville'],
    coverUrl: null,
    languages: ['en'],
    subjects: ['Adventure fiction'],
    downloadUrl: null,
  },
  {
    id: 'curated:frankenstein',
    provider: 'curated',
    title: 'Frankenstein; Or, The Modern Prometheus',
    authors: ['Mary Wollstonecraft Shelley'],
    coverUrl: null,
    languages: ['en'],
    subjects: ['Gothic fiction'],
    downloadUrl: null,
  },
  {
    id: 'curated:sherlock-holmes',
    provider: 'curated',
    title: 'The Adventures of Sherlock Holmes',
    authors: ['Arthur Conan Doyle'],
    coverUrl: null,
    languages: ['en'],
    subjects: ['Mystery fiction'],
    downloadUrl: null,
  },
  {
    id: 'curated:dracula',
    provider: 'curated',
    title: 'Dracula',
    authors: ['Bram Stoker'],
    coverUrl: null,
    languages: ['en'],
    subjects: ['Gothic fiction'],
    downloadUrl: null,
  },
  {
    id: 'curated:jane-eyre',
    provider: 'curated',
    title: 'Jane Eyre: An Autobiography',
    authors: ['Charlotte Brontë'],
    coverUrl: null,
    languages: ['en'],
    subjects: ['Classic fiction'],
    downloadUrl: null,
  },
];

function statusForCode(code: CatalogErrorCode): DiscoverStatus {
  return isOfflineCode(code) ? 'offline' : 'error';
}

function isOfflineCode(code: CatalogErrorCode): boolean {
  return code === 'NETWORK_ERROR' || code === 'RATE_LIMITED';
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

  /** Four browse rails; Hidden rails render nothing (fail-closed). */
  rails = $state<DiscoverRailState[]>([
    { kind: 'Hidden' },
    { kind: 'Hidden' },
    { kind: 'Hidden' },
    { kind: 'Hidden' },
  ]);
  /** Static chip labels for client-side filtering over loaded rails. */
  trending = $state<string[]>([...TRENDING_CHIPS]);
  /** False once any rail observes a connectivity failure. */
  isOnline = $state(true);

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

  /**
   * Live source list for the hero pill (`En línea · N fuentes` where
   * N = the returned length). Pure read-through, no I/O.
   */
  refreshSources(): CatalogSourceInfo[] {
    return this.provider.listSources();
  }

  /**
   * Load all four rails. Each rail is isolated: success maps to Loaded
   * (truncated to the rail limit, short rails render as-is, empty rails
   * collapse to Hidden) and any throw maps to Hidden without affecting the
   * other rails. Connectivity throws flip `isOnline` to false.
   */
  async refreshRails(): Promise<void> {
    this.rails = DISCOVER_RAIL_SPECS.map(() => ({ kind: 'Loading' }) as DiscoverRailState);
    this.isOnline = true;
    let offlineSeen = false;
    const settled = await Promise.all([
      this.loadFeaturedRail('NEWEST', DISCOVER_RAIL_LIMIT),
      this.loadFeaturedRail('POPULAR', DISCOVER_RAIL_LIMIT),
      this.loadCuratedRail(),
      this.loadGutenbergRail(),
    ]);
    for (const rail of settled) {
      if (rail.kind === 'Hidden' && rail.offline) offlineSeen = true;
    }
    this.rails = settled.map((rail) => (rail.kind === 'Hidden' ? { kind: 'Hidden' } : rail.state));
    if (offlineSeen) this.isOnline = false;
  }

  private async loadFeaturedRail(
    sort: CatalogFeaturedSort,
    limit: number,
  ): Promise<{ kind: 'Hidden'; offline: boolean } | { kind: 'Loaded'; state: DiscoverRailState }> {
    try {
      const page = await this.provider.featured(sort, limit);
      const books = page.results.slice(0, limit);
      if (books.length === 0) return { kind: 'Hidden', offline: false };
      return {
        kind: 'Loaded',
        state: { kind: 'Loaded', books, totalCount: page.totalCount },
      };
    } catch (err) {
      return { kind: 'Hidden', offline: isOfflineCode(codeOf(err)) };
    }
  }

  /** Static first slice: no I/O, always Loaded. */
  private loadCuratedRail(): { kind: 'Loaded'; state: DiscoverRailState } {
    const books = CURATED_FIRST_SLICE.slice(0, DISCOVER_RAIL_LIMIT);
    return {
      kind: 'Loaded',
      state: { kind: 'Loaded', books: [...books], totalCount: books.length },
    };
  }

  private async loadGutenbergRail(): Promise<
    { kind: 'Hidden'; offline: boolean } | { kind: 'Loaded'; state: DiscoverRailState }
  > {
    try {
      const page = await this.provider.searchSource(BUILTIN_GUTENDEX, '', 1);
      const books = page.results.slice(0, DISCOVER_RAIL_LIMIT);
      if (books.length === 0) return { kind: 'Hidden', offline: false };
      return {
        kind: 'Loaded',
        state: { kind: 'Loaded', books, totalCount: page.totalCount },
      };
    } catch (err) {
      return { kind: 'Hidden', offline: isOfflineCode(codeOf(err)) };
    }
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
