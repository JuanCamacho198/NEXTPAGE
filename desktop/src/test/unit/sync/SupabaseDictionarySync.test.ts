/**
 * REQ-DSI-001 / REQ-DSI-002 — dictionary sync identity.
 *
 * The fake client simulates the `user_dictionary_words` table with its
 * `(user_id, normalized_word)` unique constraint and a server-generated id, so
 * these tests prove the payload contract rather than mirror the implementation.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import type { SupabaseDictionaryRow } from '$lib/shared/sync/SupabaseDictionarySync';

let mockHasLiveSession = vi.fn<() => boolean>();
let mockUserId = vi.fn<() => string | null>();

type RemoteRow = Record<string, unknown>;

let remoteRows: RemoteRow[] = [];
let idCounter = 0;
let upsertPayloads: Record<string, unknown>[] = [];
let upsertOptions: unknown[] = [];
let updatePayloads: Record<string, unknown>[] = [];
let eqCalls: Array<[string, unknown]> = [];

function naturalKey(row: Record<string, unknown>): string {
  return `${row.user_id}|${row.normalized_word}`;
}

const fakeClient = {
  from: (_table: string) => ({
    upsert: (payload: Record<string, unknown>, options: unknown) => {
      upsertPayloads.push(payload);
      upsertOptions.push(options);
      const key = naturalKey(payload);
      const existing = remoteRows.find((r) => naturalKey(r) === key);
      const ignoreDuplicates = Boolean(
        (options as { ignoreDuplicates?: boolean })?.ignoreDuplicates,
      );
      if (existing && !ignoreDuplicates) {
        // PostgREST merges the payload into the conflicting row. The server id
        // survives only because the payload no longer carries one.
        Object.assign(existing, payload);
      } else {
        remoteRows.push({ ...payload, id: `server-${++idCounter}` });
      }
      return Promise.resolve({ error: null });
    },
    update: (patch: Record<string, unknown>) => {
      updatePayloads.push(patch);
      return {
        eq: (column: string, value: unknown) => {
          eqCalls.push([column, value]);
          return {
            eq: (column2: string, value2: unknown) => {
              eqCalls.push([column2, value2]);
              return Promise.resolve({ error: null });
            },
          };
        },
      };
    },
  }),
};

vi.mock('$lib/services/supabase', () => ({
  getSessionClient: () => fakeClient,
  hasLiveSession: () => mockHasLiveSession(),
}));

vi.mock('$lib/shared/stores/AuthState.svelte', () => ({
  authState: {
    get userId(): string | null {
      return mockUserId();
    },
  },
}));

let SupabaseDictionarySync: typeof import('$lib/shared/sync/SupabaseDictionarySync').SupabaseDictionarySync;

beforeAll(async () => {
  const mod = await import('$lib/shared/sync/SupabaseDictionarySync');
  SupabaseDictionarySync = mod.SupabaseDictionarySync;
});

function makeRow(overrides: Partial<SupabaseDictionaryRow> = {}): SupabaseDictionaryRow {
  return {
    id: 'local-1',
    userId: 'user-1',
    word: 'Abyss',
    normalizedWord: 'abyss',
    tags: [],
    isFavorite: false,
    srsStage: 0,
    updatedAt: '2025-06-01T00:00:00Z',
    deletedAt: null,
    createdAt: '2025-01-01T00:00:00Z',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockHasLiveSession.mockReturnValue(true);
  mockUserId.mockReturnValue('user-1');
  remoteRows = [];
  idCounter = 0;
  upsertPayloads = [];
  upsertOptions = [];
  updatePayloads = [];
  eqCalls = [];
});

describe('SupabaseDictionarySync — upsert identity (REQ-DSI-001)', () => {
  it('omits the local id from the upsert payload and keeps the natural-key conflict target', async () => {
    const sync = new SupabaseDictionarySync('user-1');

    await sync.upsert(makeRow({ id: 'local-1' }));

    expect(upsertPayloads).toHaveLength(1);
    expect(upsertPayloads[0]).not.toHaveProperty('id');
    expect(upsertPayloads[0].user_id).toBe('user-1');
    expect(upsertPayloads[0].normalized_word).toBe('abyss');
    expect(upsertOptions[0]).toEqual({
      onConflict: 'user_id, normalized_word',
      ignoreDuplicates: false,
    });
  });

  it('re-upserting the same natural key from another local id keeps one row and the server id', async () => {
    const first = new SupabaseDictionarySync('user-1');
    await first.upsert(makeRow({ id: 'local-1' }));

    expect(remoteRows).toHaveLength(1);
    const serverId = remoteRows[0].id;
    expect(serverId).toBe('server-1');

    const second = new SupabaseDictionarySync('user-1');
    await second.upsert(makeRow({ id: 'local-2', tags: ['cross-device'] }));

    expect(remoteRows).toHaveLength(1);
    expect(remoteRows[0].id).toBe(serverId);
    expect(remoteRows[0].tags).toEqual(['cross-device']);
  });
});

describe('SupabaseDictionarySync — delete by natural key (REQ-DSI-002)', () => {
  it('soft-deletes by (user_id, normalized_word) and never filters on id', async () => {
    const sync = new SupabaseDictionarySync('user-1');

    await sync.delete('abyss');

    expect(updatePayloads).toHaveLength(1);
    expect(updatePayloads[0]).toHaveProperty('deleted_at');
    expect(updatePayloads[0]).toHaveProperty('updated_at');
    expect(eqCalls).toEqual([
      ['user_id', 'user-1'],
      ['normalized_word', 'abyss'],
    ]);
    expect(eqCalls.some(([column]) => column === 'id')).toBe(false);
  });
});
