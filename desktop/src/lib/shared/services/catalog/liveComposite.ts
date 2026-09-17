/**
 * LiveCatalogProvider — desktop composition seam for Discover: the composite
 * rebuilds from installed addon rows whenever the registry mutates, so
 * install/enable/disable/uninstall change the active source set immediately,
 * and rows are re-read after restart (design A1/A4 wiring; no Discover UI
 * changes needed — DiscoverDomainState just consumes this provider).
 */
import {
  createRebuildingCatalogProvider,
  type CatalogProviderSupplier,
} from './CompositeCatalogProvider';
import { PersistentDiscoverCache, TauriDiscoverCachePort } from './DiscoverCache';
import type {
  CatalogBook,
  CatalogFeaturedSort,
  CatalogProvider,
  CatalogSource,
  CatalogSourceInfo,
  PagedResult,
} from './CatalogProvider';
import { googleBooksKeyFromEnv } from './BuiltInCatalogProviders';
import { resolveDownloadUrl } from './mappers';
import { AddonRegistry, getAddonRegistry } from '../addons/AddonRegistry';

// Slice 7 single-state-source: the live composite listens on the SHARED
// registry instance (the same one the addons singleton mutates), so screen
// install/enable/disable/uninstall drive the existing invalidation below.
const registry: AddonRegistry = getAddonRegistry();

/**
 * Production Discover cache: an in-memory mirror over the durable
 * `discover_cache` table (best-effort write-through, bounded featured preload).
 * Constructed once so every composite rebuild shares one mirror — this is what
 * makes rail caching real in production instead of test-only.
 */
export const discoverCache = new PersistentDiscoverCache(new TauriDiscoverCachePort());

// Google Books is registered only when `VITE_GOOGLE_BOOKS_KEY` is non-blank;
// a blank key omits the provider and the app keeps working on Gutendex + OL.
// Every rebuild re-runs the cache preload, so addon changes re-seed the mirror.
const supplier: CatalogProviderSupplier = createRebuildingCatalogProvider(
  () => registry.listInstalled(),
  undefined,
  googleBooksKeyFromEnv(),
  { cache: discoverCache },
);
registry.onChanged(() => supplier.invalidate());

class LiveCatalogProvider implements CatalogProvider {
  constructor(private readonly supplier: CatalogProviderSupplier) {}

  search(query: string, page: number): Promise<PagedResult> {
    return supplier.current().then((c) => c.search(query, page));
  }

  getDetails(id: string): Promise<CatalogBook> {
    return supplier.current().then((c) => c.getDetails(id));
  }

  featured(sort: CatalogFeaturedSort, limit: number): Promise<PagedResult> {
    return supplier.current().then((c) => c.featured(sort, limit));
  }

  supportsFeatured(sort: CatalogFeaturedSort): boolean {
    return supplier.peek()?.supportsFeatured(sort) ?? false;
  }

  searchSource(sourceId: CatalogSource, query: string, page: number): Promise<PagedResult> {
    return supplier.current().then((c) => c.searchSource(sourceId, query, page));
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }

  listSources(): CatalogSourceInfo[] {
    return supplier.peek()?.listSources() ?? [];
  }
}

export const liveCatalogProvider: CatalogProvider = new LiveCatalogProvider(supplier);
