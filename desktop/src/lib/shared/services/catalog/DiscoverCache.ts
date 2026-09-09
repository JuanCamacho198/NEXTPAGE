/**
 * DiscoverCache — isolated TTL cache for public-domain catalog pages/details.
 * Mirrors the Rust `discover_cache` table (key, payload, fetched_at, ttl_s)
 * and the Android `DiscoverCache` store key-for-key: page keys live 24h,
 * detail keys live 7d. Payloads are opaque JSON. The default store is
 * in-memory; the durable Rust table is the future Tauri-command seam, so
 * this module never touches user_books/outbox by construction.
 */

export const PAGE_TTL_S = 86_400;
export const DETAIL_TTL_S = 604_800;

/**
 * Discover cache format version (design A7): prefixed into every key.
 * v2 — keys carry the full source id; v1 rows age out unread.
 */
export const DISCOVER_CACHE_VERSION = 'v2';

const PAGE_KEY_PREFIX = `p:${DISCOVER_CACHE_VERSION}:`;
const DETAIL_KEY_PREFIX = `d:${DISCOVER_CACHE_VERSION}:`;

/** Page cache key: `p:v2:{sourceId}:{query}:{page}` (24h). Query is normalized. */
export function pageCacheKey(sourceId: string, query: string, page: number): string {
  return `${PAGE_KEY_PREFIX}${sourceId}:${query.trim().toLowerCase()}:${page}`;
}

/** Detail cache key: `d:v2:{sourceId}:{id}` (7d). */
export function detailCacheKey(sourceId: string, id: string): string {
  return `${DETAIL_KEY_PREFIX}${sourceId}:${id}`;
}

export interface DiscoverCacheStore {
  get(key: string, nowEpochSecs: number): string | null;
  put(key: string, payload: string, fetchedAtEpochSecs: number, ttlS: number): void;
}

interface CacheEntry {
  payload: string;
  fetchedAt: number;
  ttlS: number;
}

/**
 * In-memory TTL store with lazy expiry: reads past TTL return null and
 * evict the row eagerly so the map cannot grow unbounded. Mirrors
 * `discover_cache_get` in `desktop/src-tauri/src/db.rs`.
 */
export class InMemoryDiscoverCache implements DiscoverCacheStore {
  private readonly entries = new Map<string, CacheEntry>();

  get(key: string, nowEpochSecs: number): string | null {
    const entry = this.entries.get(key);
    if (!entry) return null;
    if (nowEpochSecs - entry.fetchedAt > entry.ttlS) {
      this.entries.delete(key);
      return null;
    }
    return entry.payload;
  }

  put(key: string, payload: string, fetchedAtEpochSecs: number, ttlS: number): void {
    this.entries.set(key, { payload, fetchedAt: fetchedAtEpochSecs, ttlS });
  }

  size(): number {
    return this.entries.size;
  }
}
