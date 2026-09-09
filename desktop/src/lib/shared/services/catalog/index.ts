export {
  BUILTIN_GUTENDEX,
  BUILTIN_OPENLIBRARY,
  addonSource,
  addonSourceIdOf,
  parseCatalogSource,
} from './CatalogProvider';
export type {
  CatalogBook,
  CatalogProvider,
  CatalogSource,
  CatalogSourceInfo,
  CatalogSourceKind,
  PagedResult,
} from './CatalogProvider';
export { CatalogError, catalogError, isCatalogError, mapHttpStatusToCode } from './errors';
export type { CatalogErrorCode } from './errors';
export { GutendexDataSource, GUTENDEX_BASE_URL } from './GutendexDataSource';
export { OpenLibraryDataSource, OPEN_LIBRARY_BASE_URL } from './OpenLibraryDataSource';
export { CompositeCatalogProvider, createRebuildingCatalogProvider, defaultCatalogProviders } from './CompositeCatalogProvider';
export type { CatalogProviderSupplier, CompositeOptions } from './CompositeCatalogProvider';
export { liveCatalogProvider } from './liveComposite';
export {
  DETAIL_TTL_S,
  InMemoryDiscoverCache,
  PAGE_TTL_S,
  detailCacheKey,
  pageCacheKey,
} from './DiscoverCache';
export type { DiscoverCacheStore } from './DiscoverCache';
export {
  backoffDelayMs,
  clampPageSize,
  createRateLimiter,
  createSearchDebouncer,
  fetchWithRetry,
  shouldRetryStatus,
  toCatalogError,
  buildUserAgent,
  DEBOUNCE_MS,
  DEFAULT_PAGE_SIZE,
  DESKTOP_USER_AGENT,
  MAX_DELAYED_RETRIES,
  MAX_PAGE_SIZE,
  MIN_PAGE_SIZE,
  OL_MIN_GAP_MS,
  RETRY_BASE_DELAY_MS,
} from './policy';
export {
  computeNextPage,
  isGutendexPublicDomain,
  isOpenLibraryPublic,
  mapGutendexBook,
  mapOpenLibraryDoc,
  mergeResults,
  normalizeMatchKey,
  openLibraryCoverUrl,
  resolveDownloadUrl,
  resolveTotalCount,
  toPagedResult,
} from './mappers';
export type { GutendexRecord, OpenLibraryDoc } from './mappers';
