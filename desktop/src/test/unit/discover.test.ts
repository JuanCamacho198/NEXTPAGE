import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { DiscoverDomainState } from '$lib/features/discover/DiscoverDomainState.svelte';
import { CompositeCatalogProvider } from '$lib/shared/services/catalog/CompositeCatalogProvider';
import {
  GutendexCatalogProvider,
  OpenLibraryCatalogProvider,
} from '$lib/shared/services/catalog/BuiltInCatalogProviders';
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

function stateWith(handler: (url: string) => { status: number; body: unknown }): {
  state: DiscoverDomainState;
  calls: { n: number };
} {
  const calls = { n: 0 };
  const counting = stubFetch((url) => {
    calls.n += 1;
    return handler(url);
  });
  const provider = new CompositeCatalogProvider(
    [
      new GutendexCatalogProvider(new GutendexDataSource(counting)),
      new OpenLibraryCatalogProvider(new OpenLibraryDataSource(counting)),
    ],
    { debounceMs: 0 },
  );
  return { state: new DiscoverDomainState(provider), calls };
}

function searchBodies() {
  return (url: string) => {
    if (url.includes('/books/1342/')) return { status: 200, body: gutendexFixture.results[0] };
    if (url.includes('gutendex')) return { status: 200, body: gutendexFixture };
    return { status: 200, body: openLibraryFixture };
  };
}

describe('DiscoverDomainState (PR2 RED)', () => {
  it('blank query resets to idle without I/O', async () => {
    const { state, calls } = stateWith(searchBodies());
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(calls.n).toBeGreaterThan(0);
    state.setQuery('   ');
    expect(state.status).toBe('idle');
    expect(state.books).toEqual([]);
    const afterBlank = calls.n;
    await state.searchFirstPage();
    expect(calls.n).toBe(afterBlank);
    expect(state.status).toBe('idle');
  });

  it('search loads books with Gutendex authority and OL cover fallback', async () => {
    const { state } = stateWith(searchBodies());
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(state.status).toBe('loaded');
    expect(state.totalCount).toBe(3);
    const pride = state.books.find((b) => b.id === 'gutendex:1342');
    expect(pride?.coverUrl).toBe('https://covers.openlibrary.org/b/id/6794977-M.jpg');
    expect(state.books.some((b) => b.title === 'Borrow Restricted Title')).toBe(false);
  });

  it('loadNextPage appends without duplicates and guards null nextPage', async () => {
    const page1Records = Array.from({ length: 21 }, (_, i) => ({
      ...gutendexFixture.results[0],
      id: 1000 + i,
      title: `Page One Book ${i}`,
    }));
    const page2Records = Array.from({ length: 5 }, (_, i) => ({
      ...gutendexFixture.results[0],
      id: 2000 + i,
      title: `Page Two Book ${i}`,
    }));
    const handler = (url: string) => {
      if (url.includes('gutendex')) {
        const page2 = url.includes('page=2');
        const records = page2 ? page2Records : page1Records;
        return { status: 200, body: { count: 50, results: records } };
      }
      return { status: 200, body: { numFound: 50, docs: [] } };
    };
    const { state, calls } = stateWith(handler);
    state.setQuery('史诗');
    await state.searchFirstPage();
    expect(state.status).toBe('loaded');
    expect(state.nextPage).toBe(2);
    const firstCount = state.books.length;
    await state.loadNextPage();
    expect(state.activePage).toBe(2);
    expect(state.books.length).toBeGreaterThan(firstCount);
    const ids = state.books.map((b) => b.id);
    expect(new Set(ids).size).toBe(ids.length);
    const afterEnd = calls.n;
    state.nextPage = null;
    await state.loadNextPage();
    expect(calls.n).toBe(afterEnd);
  });

  it('NETWORK_ERROR maps to offline and retry recovers', async () => {
    let attempt = 0;
    const { state } = stateWith((url) => {
      attempt += 1;
      if (attempt <= 2) throw new TypeError('fetch failed');
      return searchBodies()(url);
    });
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(state.status).toBe('offline');
    expect(state.errorCode).toBe('NETWORK_ERROR');
    await state.retry();
    expect(state.status).toBe('loaded');
    expect(state.errorCode).toBeNull();
    expect(state.books.length).toBeGreaterThan(0);
  });

  it('zero results map to empty, distinct from error', async () => {
    const { state } = stateWith((url) =>
      url.includes('gutendex')
        ? { status: 200, body: { count: 0, results: [] } }
        : { status: 200, body: { numFound: 0, docs: [] } },
    );
    state.setQuery('no-such-book-xyz');
    await state.searchFirstPage();
    expect(state.status).toBe('empty');
    expect(state.errorCode).toBeNull();
  });

  it('bad-id detail maps to notFound and dismiss preserves the list', async () => {
    const { state } = stateWith(searchBodies());
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(state.status).toBe('loaded');
    const preserved = state.books.length;
    await state.openDetail('openlibrary:/works/OL11W');
    expect(state.detailStatus).toBe('notFound');
    expect(state.detail).toBeNull();
    state.dismissDetail();
    expect(state.detailStatus).toBe('closed');
    expect(state.books.length).toBe(preserved);
  });

  it('imports no user_books/outbox/sync/addon surface', () => {
    const here = dirname(fileURLToPath(import.meta.url));
    const source = readFileSync(
      resolve(here, '../../lib/features/discover/DiscoverDomainState.svelte.ts'),
      'utf8',
    );
    for (const banned of ['user_books', 'outbox', 'addon']) {
      expect(source).not.toContain(banned);
    }
    expect(source).not.toMatch(/\bsync\b/);
  });
});

describe('DiscoverDomainState (PR3 grid/detail state mapping)', () => {
  it('UPSTREAM_ERROR maps to error (not offline) and retry recovers', async () => {
    let failing = true;
    const { state } = stateWith((url) => {
      if (failing) return { status: 500, body: { error: 'boom' } };
      return searchBodies()(url);
    });
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(state.status).toBe('error');
    expect(state.errorCode).toBe('UPSTREAM_ERROR');
    failing = false;
    await state.retry();
    expect(state.status).toBe('loaded');
    expect(state.errorCode).toBeNull();
  });

  it('RATE_LIMITED maps to offline (retryable)', async () => {
    const { state } = stateWith(() => ({ status: 429, body: { error: 'slow down' } }));
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(state.status).toBe('offline');
    expect(state.errorCode).toBe('RATE_LIMITED');
  });

  it('retry after failed loadNextPage re-requests the failed page', async () => {
    const page1Records = Array.from({ length: 21 }, (_, i) => ({
      ...gutendexFixture.results[0],
      id: 1000 + i,
      title: `Page One Book ${i}`,
    }));
    const page2Records = Array.from({ length: 3 }, (_, i) => ({
      ...gutendexFixture.results[0],
      id: 2000 + i,
      title: `Page Two Book ${i}`,
    }));
    let page2Failing = true;
    const handler = (url: string) => {
      if (url.includes('gutendex')) {
        const page2 = url.includes('page=2');
        if (page2) {
          if (page2Failing) return { status: 500, body: { error: 'boom' } };
          return { status: 200, body: { count: 50, results: page2Records } };
        }
        return { status: 200, body: { count: 50, results: page1Records } };
      }
      if (url.includes('page=2') && page2Failing) {
        return { status: 500, body: { error: 'boom' } };
      }
      return { status: 200, body: { numFound: 50, docs: [] } };
    };
    const { state } = stateWith(handler);
    state.setQuery('pride');
    await state.searchFirstPage();
    await state.loadNextPage();
    expect(state.status).toBe('error');
    expect(state.errorCode).toBe('UPSTREAM_ERROR');
    page2Failing = false;
    await state.retry();
    expect(state.status).toBe('loaded');
    expect(state.activePage).toBe(2);
  });

  it('successful detail load populates detail without touching the list', async () => {
    const { state } = stateWith(searchBodies());
    state.setQuery('pride');
    await state.searchFirstPage();
    const preserved = state.books.length;
    await state.openDetail('gutendex:1342');
    expect(state.detailStatus).toBe('loaded');
    expect(state.detail?.id).toBe('gutendex:1342');
    expect(state.detail?.title.length).toBeGreaterThan(0);
    expect(state.books.length).toBe(preserved);
  });

  it('UNAVAILABLE_DOWNLOAD detail failure maps to error status (code-only, redacted)', async () => {
    const { state } = stateWith((url) => {
      if (url.includes('/books/1342/')) return { status: 500, body: { error: 'boom' } };
      if (url.includes('gutendex')) return { status: 200, body: gutendexFixture };
      return { status: 200, body: openLibraryFixture };
    });
    state.setQuery('pride');
    await state.searchFirstPage();
    await state.openDetail('gutendex:1342');
    expect(state.detailStatus).toBe('error');
    expect(state.detail).toBeNull();
  });
});

describe('DiscoverDomainState (PR4 hardening)', () => {
  it('INVALID_PAGE (page < 1) rejects before any I/O and maps to error', async () => {
    const { state, calls } = stateWith(searchBodies());
    state.setQuery('pride');
    await state.searchFirstPage();
    expect(state.status).toBe('loaded');
    const callsAfterLoad = calls.n;
    state.nextPage = 0;
    await state.loadNextPage();
    expect(state.status).toBe('error');
    expect(state.errorCode).toBe('INVALID_PAGE');
    expect(calls.n).toBe(callsAfterLoad);
  });

});
