import { describe, expect, it } from 'vitest';
import { CompositeCatalogProvider } from '$lib/shared/services/catalog/CompositeCatalogProvider';
import {
  DETAIL_TTL_S,
  InMemoryDiscoverCache,
  PAGE_TTL_S,
  detailCacheKey,
  pageCacheKey,
} from '$lib/shared/services/catalog/DiscoverCache';
import { GutendexDataSource } from '$lib/shared/services/catalog/GutendexDataSource';
import { OpenLibraryDataSource } from '$lib/shared/services/catalog/OpenLibraryDataSource';
import gutendexFixture from '$lib/shared/services/catalog/fixtures/gutendex-search.json';
import openLibraryFixture from '$lib/shared/services/catalog/fixtures/openlibrary-search.json';

function stubFetch(handler: (url: string) => { status: number; body: unknown }): typeof fetch {
  return (async (input: unknown) => {
    const { status, body } = handler(String(input));
    return new Response(JSON.stringify(body), { status });
  }) as typeof fetch;
}

function stubbedSources(calls: { g: number; o: number }): {
  g: GutendexDataSource;
  o: OpenLibraryDataSource;
} {
  const g = new GutendexDataSource(
    stubFetch(() => {
      calls.g += 1;
      return { status: 200, body: gutendexFixture };
    }),
  );
  const o = new OpenLibraryDataSource(
    stubFetch(() => {
      calls.o += 1;
      return { status: 200, body: openLibraryFixture };
    }),
  );
  return { g, o };
}

describe('DiscoverCache keys and TTL', () => {
  it('uses p:{provider}:{query}:{page} page keys with 24h TTL', () => {
    expect(pageCacheKey('Pride ', 1)).toBe('p:composite:pride:1');
    expect(PAGE_TTL_S).toBe(86_400);
  });

  it('uses d:{provider}:{id} detail keys with 7d TTL', () => {
    expect(detailCacheKey('gutendex:1342')).toBe('d:composite:gutendex:1342');
    expect(DETAIL_TTL_S).toBe(604_800);
  });

  it('returns hits within TTL and evicts expired rows eagerly', () => {
    const cache = new InMemoryDiscoverCache();
    cache.put('p:composite:pride:1', '{"n":1}', 1_000, PAGE_TTL_S);
    expect(cache.get('p:composite:pride:1', 1_000 + 3_600)).toBe('{"n":1}');
    expect(cache.get('p:composite:pride:1', 1_000 + PAGE_TTL_S + 1)).toBeNull();
    expect(cache.size()).toBe(0);
  });

  it('misses on unknown keys without writes', () => {
    const cache = new InMemoryDiscoverCache();
    expect(cache.get('p:composite:missing:1', 1_000)).toBeNull();
    expect(cache.size()).toBe(0);
  });

  it('overwrites existing keys on re-put', () => {
    const cache = new InMemoryDiscoverCache();
    cache.put('p:composite:pride:1', '{"n":1}', 1_000, PAGE_TTL_S);
    cache.put('p:composite:pride:1', '{"n":2}', 2_000, PAGE_TTL_S);
    expect(cache.get('p:composite:pride:1', 2_001)).toBe('{"n":2}');
  });
});

describe('CompositeCatalogProvider cache read-through', () => {
  it('serves a repeated search from cache without network I/O', async () => {
    const calls = { g: 0, o: 0 };
    const { g, o } = stubbedSources(calls);
    const cache = new InMemoryDiscoverCache();
    const provider = new CompositeCatalogProvider(g, o, {
      debounceMs: 0,
      cache,
      nowEpochSecs: () => 1_000,
    });
    const first = await provider.search('pride', 1);
    expect(calls).toEqual({ g: 1, o: 1 });
    const second = await provider.search('pride', 1);
    expect(second).toEqual(first);
    expect(calls).toEqual({ g: 1, o: 1 });
  });

  it('refetches expired pages and replaces the entry', async () => {
    const calls = { g: 0, o: 0 };
    const { g, o } = stubbedSources(calls);
    const cache = new InMemoryDiscoverCache();
    let now = 1_000;
    const provider = new CompositeCatalogProvider(g, o, {
      debounceMs: 0,
      cache,
      nowEpochSecs: () => now,
    });
    await provider.search('pride', 1);
    now += PAGE_TTL_S + 1;
    await provider.search('pride', 1);
    expect(calls).toEqual({ g: 2, o: 2 });
  });

  it('caches details 7d and keeps the pure download path I/O-free', async () => {
    const detailFetch = stubFetch((url) =>
      url.endsWith('/books/1342/')
        ? { status: 200, body: gutendexFixture.results[0] }
        : { status: 404, body: {} },
    );
    const calls = { g: 0, o: 0 };
    const counting: typeof fetch = (async (...args: Parameters<typeof fetch>) => {
      calls.g += 1;
      return detailFetch(...args);
    }) as typeof fetch;
    const cache = new InMemoryDiscoverCache();
    let now = 5_000;
    const provider = new CompositeCatalogProvider(
      new GutendexDataSource(counting),
      stubbedSources(calls).o,
      {
        debounceMs: 0,
        cache,
        nowEpochSecs: () => now,
      },
    );
    const first = await provider.getDetails('gutendex:1342');
    expect(first.id).toBe('gutendex:1342');
    now += 3_600;
    await provider.getDetails('gutendex:1342');
    expect(calls.g).toBe(1);
    // Pure path: no I/O, no cache interaction.
    expect(provider.resolveDownloadUrl({ 'text/plain': 'https://example.com/b.txt' }, true)).toBe(
      'https://example.com/b.txt',
    );
    expect(calls.g).toBe(1);
  });

  it('merges cover fallback identically on cache miss and hit', async () => {
    const calls = { g: 0, o: 0 };
    const { g, o } = stubbedSources(calls);
    const provider = new CompositeCatalogProvider(g, o, {
      debounceMs: 0,
      cache: new InMemoryDiscoverCache(),
      nowEpochSecs: () => 1_000,
    });
    const miss = await provider.search('pride', 1);
    const hit = await provider.search('pride', 1);
    const prideMiss = miss.results.find((b) => b.id === 'gutendex:1342');
    const prideHit = hit.results.find((b) => b.id === 'gutendex:1342');
    expect(prideMiss?.coverUrl).toBe('https://covers.openlibrary.org/b/id/6794977-M.jpg');
    expect(prideHit).toEqual(prideMiss);
  });
});
