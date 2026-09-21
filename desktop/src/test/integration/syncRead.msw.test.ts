import { invoke } from '@tauri-apps/api/core';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { setupServer } from 'msw/node';
import { SupabaseBookCatalogSync } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { invokeStubCalls, stubInvoke, useInvokeStubs } from '../doubles/invokeStub';
import {
  supabaseAuthErrorHandlers,
  supabaseAuthHandlers,
  supabaseCatalogHandlers,
} from '../doubles/supabaseHandlers';

vi.mock('$lib/services/supabase', async () => {
  const { createClient } = await import('@supabase/supabase-js');
  const { TEST_SUPABASE_ANON_KEY, TEST_SUPABASE_URL } = await import('../doubles/supabaseHandlers');
  return {
    getSessionClient: () => createClient(TEST_SUPABASE_URL, TEST_SUPABASE_ANON_KEY),
    hasLiveSession: () => true,
    recheckLiveSession: async () => true,
  };
});

const server = setupServer(...supabaseCatalogHandlers(), ...supabaseAuthHandlers());

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('sync read over MSW Supabase doubles', () => {
  it('returns MSW fixture rows on the catalog read path with no real network', async () => {
    const sync = new SupabaseBookCatalogSync('user-1');

    const rows = await sync.fetchCatalog();

    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      id: 'book-msw-1',
      userId: 'user-1',
      title: 'MSW Fixture Book One',
      format: 'epub',
    });
    expect(rows[1].title).toBe('MSW Fixture Book Two');
  });

  it('follows the existing error path on an Auth 401 fixture', async () => {
    server.use(...supabaseAuthErrorHandlers());
    const sync = new SupabaseBookCatalogSync('user-1');

    await expect(sync.fetchCatalog()).rejects.toThrow(/401/);
  });
});

describe('invoke-stub per-test isolation', () => {
  useInvokeStubs();

  it('observes only its own stub for a shared channel', async () => {
    stubInvoke('listBooks', [{ id: 'book-from-first-test' }]);

    const result = await invoke('listBooks');

    expect(result).toEqual([{ id: 'book-from-first-test' }]);
    expect(invokeStubCalls('listBooks')).toHaveLength(1);
  });

  it('does not leak stubs or call history from the previous test', async () => {
    stubInvoke('listBooks', [{ id: 'book-from-second-test' }]);

    const result = await invoke('listBooks');

    expect(result).toEqual([{ id: 'book-from-second-test' }]);
    expect(invokeStubCalls('listBooks')).toHaveLength(1);
  });
});
