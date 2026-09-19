/**
 * REQ-DSI-001 / REQ-DSI-002 — cross-device dictionary identity.
 *
 * The remote row's server id differs from the local id for the same word, which
 * is exactly the case the previous id-only delete match missed.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import type { SupabaseDictionaryRow } from '$lib/shared/sync/SupabaseDictionarySync';

let mockUserId = vi.fn<() => string | null>();
let mockHasLiveSession = vi.fn<() => boolean>();
let invokeMock = vi.fn();
let outboxCalls: Array<{
  entityType: string;
  entityId: string;
  operation: string;
  payloadJson: string;
}> = [];
let realtimeCallback: ((row: SupabaseDictionaryRow) => void) | null = null;

vi.mock('$lib/shared/api/invokeWrapper', () => ({
  invoke: (...args: unknown[]) => invokeMock(...args),
}));

vi.mock('$lib/shared/stores/AuthState.svelte', () => ({
  authState: {
    get userId(): string | null {
      return mockUserId();
    },
  },
}));

vi.mock('$lib/services/supabase', () => ({
  hasLiveSession: () => mockHasLiveSession(),
}));

vi.mock('$lib/shared/sync/SupabaseDictionarySync', () => ({
  SupabaseDictionarySync: vi.fn(function () {
    return {
      subscribeToDictionary: (cb: (row: SupabaseDictionaryRow) => void) => {
        realtimeCallback = cb;
        return () => {
          realtimeCallback = null;
        };
      },
      destroy: () => undefined,
    };
  }),
}));

let createDictionaryState: typeof import('$lib/shared/stores/DictionaryState.svelte').createDictionaryState;

beforeAll(async () => {
  const mod = await import('$lib/shared/stores/DictionaryState.svelte');
  createDictionaryState = mod.createDictionaryState;
});

function localEntry(id: string, word: string) {
  return {
    id,
    word,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
    normalizedWord: word.trim().toLowerCase(),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUserId.mockReturnValue('user-1');
  mockHasLiveSession.mockReturnValue(true);
  outboxCalls = [];
  realtimeCallback = null;
  invokeMock.mockImplementation((command: string, args?: Record<string, unknown>) => {
    if (command === 'listDictionaryWords') {
      return Promise.resolve([localEntry('local-1', 'Abyss')]);
    }
    if (command === 'addCoalescedSyncOutboxItem') {
      outboxCalls.push(args as (typeof outboxCalls)[number]);
      return Promise.resolve(undefined);
    }
    return Promise.resolve(undefined);
  });
});

async function seededState() {
  const state = createDictionaryState();
  await state.load();
  state.subscribeToRemoteChanges();
  expect(realtimeCallback).not.toBeNull();
  return state;
}

describe('DictionaryState — remote delete identity (REQ-DSI-002)', () => {
  it('removes the local entry when the remote soft-delete carries a different server id', async () => {
    const state = await seededState();
    expect(state.words.map((w) => w.id)).toEqual(['local-1']);

    realtimeCallback!({
      id: 'server-99',
      userId: 'user-1',
      word: 'Abyss',
      normalizedWord: 'abyss',
      tags: [],
      isFavorite: false,
      srsStage: 0,
      updatedAt: '2025-06-01T00:00:00Z',
      deletedAt: '2025-06-01T00:00:00Z',
      createdAt: '2025-01-01T00:00:00Z',
    });

    expect(state.words).toHaveLength(0);
  });

  it('keeps the local entry when the remote delete is for a different natural key', async () => {
    const state = await seededState();

    realtimeCallback!({
      id: 'server-99',
      userId: 'user-1',
      word: 'Other',
      normalizedWord: 'other',
      tags: [],
      isFavorite: false,
      srsStage: 0,
      updatedAt: '2025-06-01T00:00:00Z',
      deletedAt: '2025-06-01T00:00:00Z',
      createdAt: '2025-01-01T00:00:00Z',
    });

    expect(state.words.map((w) => w.id)).toEqual(['local-1']);
  });
});

describe('DictionaryState — local remove enqueues the natural key (REQ-DSI-002)', () => {
  it('carries normalizedWord in the DELETE outbox payload and keeps the local id as entityId', async () => {
    const state = await seededState();

    await state.remove('local-1');

    expect(state.words).toHaveLength(0);
    const del = outboxCalls.find((c) => c.operation === 'DELETE');
    expect(del).toBeDefined();
    expect(del!.entityType).toBe('DICTIONARY_WORD');
    expect(del!.entityId).toBe('local-1');
    const payload = JSON.parse(del!.payloadJson) as Record<string, unknown>;
    expect(payload.normalizedWord).toBe('abyss');
    expect(payload.userId).toBe('user-1');
    expect(payload).not.toHaveProperty('id');
  });

  it('normalizes accents and surrounding whitespace into the enqueued natural key', async () => {
    invokeMock.mockImplementation((command: string, args?: Record<string, unknown>) => {
      if (command === 'listDictionaryWords') {
        return Promise.resolve([localEntry('local-2', '  Café  ')]);
      }
      if (command === 'addCoalescedSyncOutboxItem') {
        outboxCalls.push(args as (typeof outboxCalls)[number]);
        return Promise.resolve(undefined);
      }
      return Promise.resolve(undefined);
    });

    const state = await seededState();
    await state.remove('local-2');

    const del = outboxCalls.find((c) => c.operation === 'DELETE');
    expect(del).toBeDefined();
    const payload = JSON.parse(del!.payloadJson) as Record<string, unknown>;
    expect(payload.normalizedWord).toBe('cafe');
  });
});
