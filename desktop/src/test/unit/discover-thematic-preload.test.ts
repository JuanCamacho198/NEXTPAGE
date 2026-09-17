/**
 * G6 — the thematic rail page survives a restart: the startup preload covers
 * exactly the current day's thematic page-1 key, and only that.
 */
import { describe, expect, it } from 'vitest';
import { discoverPreloadPageKeys } from '$lib/shared/services/catalog/liveComposite';
import { pageCacheKey } from '$lib/shared/services/catalog/DiscoverCache';
import { BUILTIN_GUTENDEX } from '$lib/shared/services/catalog/CatalogProvider';
import { thematicEntryFor } from '$lib/features/discover/railRotation';

describe('thematic rail startup preload (G6)', () => {
  it('preloads exactly the current day thematic page-1 key', () => {
    const day = new Date(2026, 5, 10, 12);
    expect(discoverPreloadPageKeys(day)).toEqual([
      pageCacheKey(BUILTIN_GUTENDEX, thematicEntryFor(day).term, 1),
    ]);
    expect(discoverPreloadPageKeys(day)).toEqual(['p:v2:builtin:gutendex:fiction:1']);
  });

  it('rolls with the local day and always stays one page-1 key', () => {
    const today = discoverPreloadPageKeys(new Date(2026, 5, 10));
    const tomorrow = discoverPreloadPageKeys(new Date(2026, 5, 11));

    expect(today).toHaveLength(1);
    expect(tomorrow).toHaveLength(1);
    expect(today[0]).not.toBe(tomorrow[0]);
    expect(today[0]?.endsWith(':1')).toBe(true);
    expect(tomorrow[0]?.endsWith(':1')).toBe(true);
  });
});
