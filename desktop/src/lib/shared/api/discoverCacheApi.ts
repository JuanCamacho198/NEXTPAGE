/**
 * Thin Tauri invoke wrappers for the durable Discover cache (table
 * `discover_cache`, migration 0016).
 *
 * `discoverCacheRead` deliberately does NOT judge TTL and never deletes: the
 * Rust `discover_cache_get` helper evicts expired rows eagerly, which would
 * destroy a stale-but-resident entry before stale-while-revalidate could serve
 * it. The frontend owns TTL judgement (`PersistentDiscoverCache`), so the seam
 * must return the resident row verbatim. `discoverCachePut` upserts.
 */
import { invoke } from '$lib/shared/api/invokeWrapper';

/** Mirrors Rust `DiscoverCacheRowDto` (camelCase IPC). */
export interface DiscoverCacheRowDto {
  payload: string;
  fetchedAt: number;
  ttlS: number;
}

export async function discoverCacheRead(key: string): Promise<DiscoverCacheRowDto | null> {
  return invoke<DiscoverCacheRowDto | null>('discoverCacheRead', { key });
}

export async function discoverCachePut(
  key: string,
  payload: string,
  fetchedAt: number,
  ttlS: number,
): Promise<void> {
  await invoke('discoverCachePut', { key, payload, fetchedAt, ttlS });
}
