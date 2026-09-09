/**
 * CompositeCatalogProvider — ordered dynamic composite behind the port.
 * Providers are searched in construction order (built-ins first, curated,
 * then enabled addons in install order); results concat-merge in that order.
 * Page/details caching is per-source with v2 keys carrying the full source
 * string, and reads enforce the existence check (design A1): entries whose
 * source id is not in the active source set at read time are never served.
 * Burst searches are trailing-edge debounced; page < 1 rejects before I/O.
 */
import { catalogError } from './errors';
import { bookIdPrefixForSource } from './CatalogProvider';
import type {
  CatalogBook,
  CatalogProvider,
  CatalogSourceInfo,
  PagedResult,
} from './CatalogProvider';
import {
  DETAIL_TTL_S,
  PAGE_TTL_S,
  detailCacheKey,
  pageCacheKey,
  type DiscoverCacheStore,
} from './DiscoverCache';
import { GutendexCatalogProvider } from './BuiltInCatalogProviders';
import { OpenLibraryCatalogProvider } from './BuiltInCatalogProviders';
import { mergeResults, resolveDownloadUrl, resolveTotalCount, toPagedResult } from './mappers';
import { DEBOUNCE_MS, createSearchDebouncer } from './policy';

export interface CompositeOptions {
  debounceMs?: number;
  cache?: DiscoverCacheStore | null;
  nowEpochSecs?: () => number;
}

/**
 * Book-id prefix owned by a source: built-ins drop the `builtin:` namespace
 * (`builtin:gutendex` -> `gutendex:`); addons use the source id itself
 * (`addon:<id>` -> `addon:<id>:`).
 */
export function bookIdPrefixForSource(sourceId: string): string | null {
  if (sourceId.startsWith('builtin:')) return `${sourceId.slice('builtin:'.length)}:`;
  if (sourceId.startsWith('addon:')) return `${sourceId}:`;
  return null;
}

/** Providers exposing exactly one source are page-cacheable under that source. */
function singleSource(provider: CatalogProvider): CatalogSourceInfo | null {
  const sources = provider.listSources();
  return sources.length === 1 ? sources[0] : null;
}

import { CuratedCatalogProvider } from '../addons/CuratedCatalogProvider';
import { AddonCatalogProvider } from '../addons/AddonCatalogProvider';
import type { InstalledAddonRow } from '../addons/AddonRegistry';

/** Default provider list: built-ins first, then the curated bundle, then
 * enabled addons in install order (disabled rows are excluded). */
export function defaultCatalogProviders(installedAddons: InstalledAddonRow[] = []): CatalogProvider[] {
  const addonProviders = installedAddons
    .filter((row) => row.enabled)
    .map((row) => new AddonCatalogProvider(row.manifest, row.id));
  return [
    new GutendexCatalogProvider(),
    new OpenLibraryCatalogProvider(),
    new CuratedCatalogProvider(),
    ...addonProviders,
  ];
}

interface RoutedDetails {
  provider: CatalogProvider;
  source: CatalogSourceInfo;
}

export class CompositeCatalogProvider implements CatalogProvider {
  private readonly debounced: { search: (query: string, page: number) => Promise<PagedResult> };
  private readonly cache: DiscoverCacheStore | null;
  private readonly nowEpochSecs: () => number;

  constructor(
    private readonly providers: CatalogProvider[] = defaultCatalogProviders(),
    options: CompositeOptions = {},
  ) {
    this.cache = options.cache ?? null;
    this.nowEpochSecs = options.nowEpochSecs ?? (() => Math.floor(Date.now() / 1000));
    this.debounced = createSearchDebouncer(
      (query, page) => this.executeSearch(query, page),
      options.debounceMs ?? DEBOUNCE_MS,
    );
  }

  /** Sources in provider order, deduped by sourceId (first occurrence wins). */
  listSources(): CatalogSourceInfo[] {
    const seen = new Set<string>();
    const out: CatalogSourceInfo[] = [];
    for (const provider of this.providers) {
      for (const source of provider.listSources()) {
        if (!seen.has(source.sourceId)) {
          seen.add(source.sourceId);
          out.push(source);
        }
      }
    }
    return out;
  }

  /**
   * Debounced entry point: only the latest burst query issues network I/O.
   * When every I/O-capable provider has a fresh cache entry, the merged page
   * returns synchronously without waiting for debounce.
   */
  search(query: string, page: number): Promise<PagedResult> {
    if (!Number.isInteger(page) || page < 1) {
      return Promise.reject(catalogError('INVALID_PAGE', `page must be >= 1, got ${page}`));
    }
    const active = this.activeSourceIds();
    const parts = this.searchableProviders().map((provider) => {
      const cached = this.readPageHit(provider, query, page, active);
      return cached !== null ? { cached: true as const, value: cached } : { cached: false as const, provider };
    });
    const needsFetch = parts.some((part) => {
      if (part.cached) return false;
      const source = singleSource(part.provider);
      return source !== null && active.has(source.sourceId);
    });
    if (!needsFetch) {
      // Only I/O-free providers (no single routable source) remain to call.
      return Promise.all(
        parts.map((part) => (part.cached ? Promise.resolve(part.value) : part.provider.search(query, page))),
      ).then((pages) => this.mergePaged(pages, page));
    }
    return this.debounced.search(query, page);
  }

  private async executeSearch(query: string, page: number): Promise<PagedResult> {
    // Re-check inside the debounce window: a concurrent caller may have filled it.
    const active = this.activeSourceIds();
    const pages = await Promise.all(
      this.searchableProviders().map(async (provider) => {
        const cached = this.readPageHit(provider, query, page, active);
        if (cached !== null) return cached;
        const result = await provider.search(query, page);
        this.cachePage(provider, query, page, result, active);
        return result;
      }),
    );
    return this.mergePaged(pages, page);
  }

  /** Providers without sources (disabled) contribute nothing and are never called. */
  private searchableProviders(): CatalogProvider[] {
    return this.providers.filter((p) => p.listSources().length > 0);
  }

  private activeSourceIds(): Set<string> {
    const ids = new Set<string>();
    for (const provider of this.searchableProviders()) {
      for (const source of provider.listSources()) ids.add(source.sourceId);
    }
    return ids;
  }

  /** Existence check (design A1): only sources active at read time may hit. */
  private readPageHit(
    provider: CatalogProvider,
    query: string,
    page: number,
    active: Set<string>,
  ): PagedResult | null {
    if (!this.cache) return null;
    const source = singleSource(provider);
    if (!source || !active.has(source.sourceId)) return null;
    const hit = this.cache.get(pageCacheKey(source.sourceId, query, page), this.nowEpochSecs());
    return hit ? (JSON.parse(hit) as PagedResult) : null;
  }

  private cachePage(
    provider: CatalogProvider,
    query: string,
    page: number,
    result: PagedResult,
    active: Set<string>,
  ): void {
    if (!this.cache) return;
    const source = singleSource(provider);
    if (!source || !active.has(source.sourceId)) return;
    this.cache.put(
      pageCacheKey(source.sourceId, query, page),
      JSON.stringify(result),
      this.nowEpochSecs(),
      PAGE_TTL_S,
    );
  }

  /**
   * Ordered merge: left-fold the provider pages — earlier providers win fields,
   * later ones fill cover gaps and append unmatched books (the [Gutendex,
   * OpenLibrary] fold reproduces the legacy hardcoded-pair merge exactly).
   */
  private mergePaged(pages: PagedResult[], page: number): PagedResult {
    if (pages.length === 0) return toPagedResult([], page, 0);
    let results = pages[0]!.results;
    let totalCount = pages[0]!.totalCount;
    for (let i = 1; i < pages.length; i++) {
      results = mergeResults(results, pages[i]!.results);
      totalCount = resolveTotalCount(totalCount, pages[i]!.totalCount);
    }
    return toPagedResult(results, page, totalCount);
  }

  /**
   * Exact-prefix routing over the active source set: `gutendex:`/`openlibrary:`
   * book ids hit the built-ins, `addon:<addonId>:<bookId>` the owning addon.
   * Unroutable ids reject NOT_FOUND without any I/O.
   */
  async getDetails(id: string): Promise<CatalogBook> {
    const route = this.routeDetails(id);
    if (!route) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    const { provider, source } = route;
    const active = this.activeSourceIds();
    if (this.cache && active.has(source.sourceId)) {
      const hit = this.cache.get(detailCacheKey(source.sourceId, id), this.nowEpochSecs());
      if (hit) return JSON.parse(hit) as CatalogBook;
    }
    const book = await provider.getDetails(id);
    if (this.cache && active.has(source.sourceId)) {
      this.cache.put(
        detailCacheKey(source.sourceId, id),
        JSON.stringify(book),
        this.nowEpochSecs(),
        DETAIL_TTL_S,
      );
    }
    return book;
  }

  private routeDetails(id: string): RoutedDetails | null {
    let best: { provider: CatalogProvider; source: CatalogSourceInfo; prefix: string } | null = null;
    for (const provider of this.searchableProviders()) {
      for (const source of provider.listSources()) {
        const prefix = bookIdPrefixForSource(source.sourceId);
        if (!prefix || !id.startsWith(prefix) || id.length <= prefix.length) continue;
        if (!best || prefix.length > best.prefix.length) {
          best = { provider, source, prefix };
        }
      }
    }
    return best ? { provider: best.provider, source: best.source } : null;
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }
}
