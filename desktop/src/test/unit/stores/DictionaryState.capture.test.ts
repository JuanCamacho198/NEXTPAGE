/**
 * REQ-DSI-003 / REQ-DRE-007 / REQ-DRE-008 — `DictionaryState` add/capture
 * wiring, the outbox full-row snapshot (Decision 6) and the realtime spread.
 *
 * The three write paths (`add`, `update`, `capture`) must enqueue exactly one
 * payload shape: all 18 columns the outbox can carry, with evidence as an
 * explicit null rather than an omitted key.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import type { SupabaseDictionaryRow } from '$lib/shared/sync/SupabaseDictionarySync';
import type { DictionaryEvidence } from '$lib/shared/dictionary/captureFromSelection';
import type { DictionaryWordDto } from '$lib/shared/types';

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
let dictionaryOutboxPayload: typeof import('$lib/shared/stores/DictionaryState.svelte').dictionaryOutboxPayload;

beforeAll(async () => {
  const mod = await import('$lib/shared/stores/DictionaryState.svelte');
  createDictionaryState = mod.createDictionaryState;
  dictionaryOutboxPayload = mod.dictionaryOutboxPayload;
});

/** The 17 columns the outbox sends; `userId` is added by the outbox enqueue. */
const SNAPSHOT_KEYS = [
  'createdAt',
  'definition',
  'deletedAt',
  'example',
  'normalizedWord',
  'partOfSpeech',
  'phonetic',
  'quote',
  'srsStage',
  'sourceBookAuthor',
  'sourceBookId',
  'sourceBookTitle',
  'sourceChapter',
  'sourceLocator',
  'tags',
  'updatedAt',
  'word',
].sort();

function fullEntry(overrides: Partial<DictionaryWordDto> = {}): DictionaryWordDto {
  return {
    id: 'local-1',
    word: 'Abyss',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
    normalizedWord: 'abyss',
    tags: ['classic'],
    srsStage: 3,
    definition: 'A deep void',
    partOfSpeech: 'noun',
    phonetic: '/əˈbɪs/',
    example: 'The abyss stared back.',
    quote: null,
    sourceBookId: null,
    sourceBookTitle: null,
    sourceBookAuthor: null,
    sourceChapter: null,
    sourceLocator: null,
    ...overrides,
  };
}

const EVIDENCE: DictionaryEvidence = {
  quote: 'The abyss stared back.',
  sourceBookId: 'book-7',
  sourceBookTitle: 'Deep Water',
  sourceBookAuthor: 'A. Author',
  sourceChapter: 'Chapter 3',
  sourceLocator: 'epubcfi(/6/4!/4/2)',
};

function outboxPayload(operation: 'UPSERT' | 'DELETE'): Record<string, unknown> {
  const call = outboxCalls.find((c) => c.operation === operation);
  expect(call).toBeDefined();
  return JSON.parse(call!.payloadJson) as Record<string, unknown>;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUserId.mockReturnValue('user-1');
  mockHasLiveSession.mockReturnValue(true);
  outboxCalls = [];
  realtimeCallback = null;
  invokeMock.mockImplementation((command: string, args?: Record<string, unknown>) => {
    if (command === 'listDictionaryWords') {
      return Promise.resolve([fullEntry()]);
    }
    if (command === 'addCoalescedSyncOutboxItem') {
      outboxCalls.push(args as (typeof outboxCalls)[number]);
      return Promise.resolve(undefined);
    }
    if (command === 'addDictionaryWord') {
      const payload = (args as { payload: Record<string, unknown> }).payload;
      return Promise.resolve(
        fullEntry({
          id: 'local-2',
          word: String(payload.word),
          tags: Array.isArray(payload.tags) ? (payload.tags as string[]) : [],
          quote: (payload.quote as string | null) ?? null,
          sourceBookId: (payload.sourceBookId as string | null) ?? null,
          sourceBookTitle: (payload.sourceBookTitle as string | null) ?? null,
          sourceBookAuthor: (payload.sourceBookAuthor as string | null) ?? null,
          sourceChapter: (payload.sourceChapter as string | null) ?? null,
          sourceLocator: (payload.sourceLocator as string | null) ?? null,
        }),
      );
    }
    if (command === 'updateDictionaryEvidence') {
      const payload = (args as { payload: Record<string, unknown> }).payload;
      return Promise.resolve(
        fullEntry({
          id: String(payload.id),
          quote: (payload.quote as string | null) ?? null,
          sourceBookId: (payload.sourceBookId as string | null) ?? null,
          sourceBookTitle: (payload.sourceBookTitle as string | null) ?? null,
          sourceBookAuthor: (payload.sourceBookAuthor as string | null) ?? null,
          sourceChapter: (payload.sourceChapter as string | null) ?? null,
          sourceLocator: (payload.sourceLocator as string | null) ?? null,
        }),
      );
    }
    return Promise.resolve(undefined);
  });
});

async function seededState() {
  const state = createDictionaryState();
  await state.load();
  return state;
}

describe('dictionaryOutboxPayload — Decision 6 full-row snapshot', () => {
  it('produces exactly the 18 outbox columns and never omits an evidence key', () => {
    const payload = dictionaryOutboxPayload(fullEntry({ definition: null }));

    expect(Object.keys(payload).sort()).toEqual(SNAPSHOT_KEYS);
    expect(payload.definition).toBeNull();
    expect(payload).toHaveProperty('quote', null);
    expect(payload).toHaveProperty('sourceLocator', null);
    expect(payload.normalizedWord).toBe('abyss');
  });

  it('normalizes the natural key from the word rather than trusting the dto', () => {
    const payload = dictionaryOutboxPayload(fullEntry({ word: '  Café  ' }));
    expect(payload.normalizedWord).toBe('cafe');
  });
});

describe('DictionaryState.add — evidence forwarding and snapshot enqueue', () => {
  it('forwards the six evidence fields to addDictionaryWord and enqueues them in the snapshot', async () => {
    const state = await seededState();

    await state.add('Abyss', { evidence: EVIDENCE });

    expect(invokeMock).toHaveBeenCalledWith('addDictionaryWord', {
      payload: {
        word: 'Abyss',
        tags: undefined,
        srsStage: undefined,
        userId: 'user-1',
        quote: EVIDENCE.quote,
        sourceBookId: EVIDENCE.sourceBookId,
        sourceBookTitle: EVIDENCE.sourceBookTitle,
        sourceBookAuthor: EVIDENCE.sourceBookAuthor,
        sourceChapter: EVIDENCE.sourceChapter,
        sourceLocator: EVIDENCE.sourceLocator,
      },
    });

    const payload = outboxPayload('UPSERT');
    expect(Object.keys(payload).sort()).toEqual([...SNAPSHOT_KEYS, 'userId'].sort());
    expect(payload.quote).toBe(EVIDENCE.quote);
    expect(payload.sourceBookId).toBe(EVIDENCE.sourceBookId);
    expect(payload.sourceChapter).toBe(EVIDENCE.sourceChapter);
    expect(payload.sourceLocator).toBe(EVIDENCE.sourceLocator);
  });

  it('sends explicit nulls for an evidence-less create instead of dropping the keys', async () => {
    const state = await seededState();

    await state.add('Solitude', { tags: ['quiet'] });

    const payload = outboxPayload('UPSERT');
    expect(Object.keys(payload).sort()).toEqual([...SNAPSHOT_KEYS, 'userId'].sort());
    expect(payload.quote).toBeNull();
    expect(payload.sourceBookId).toBeNull();
    expect(payload.tags).toEqual(['quiet']);
  });
});

describe('DictionaryState.capture — REQ-DRE-008 re-capture', () => {
  it('invokes updateDictionaryEvidence, replaces the entry and enqueues the full snapshot', async () => {
    const state = await seededState();
    expect(state.words[0].id).toBe('local-1');

    const updated = await state.capture('local-1', EVIDENCE);

    expect(invokeMock).toHaveBeenCalledWith('updateDictionaryEvidence', {
      payload: { id: 'local-1', ...EVIDENCE },
    });
    expect(updated.quote).toBe(EVIDENCE.quote);

    // Only the evidence fields changed; the user-authored fields survive.
    expect(state.words).toHaveLength(1);
    expect(state.words[0].definition).toBe('A deep void');
    expect(state.words[0].partOfSpeech).toBe('noun');
    expect(state.words[0].phonetic).toBe('/əˈbɪs/');
    expect(state.words[0].example).toBe('The abyss stared back.');
    expect(state.words[0].quote).toBe(EVIDENCE.quote);

    const call = outboxCalls.find((c) => c.operation === 'UPSERT');
    expect(call!.entityId).toBe('local-1');
    expect(call!.entityType).toBe('DICTIONARY_WORD');
    const payload = outboxPayload('UPSERT');
    expect(payload.word).toBe('Abyss');
    expect(payload.definition).toBe('A deep void');
    expect(payload.sourceBookTitle).toBe(EVIDENCE.sourceBookTitle);
  });

  it('rejects without corrupting words and without enqueueing an outbox row', async () => {
    const state = await seededState();
    const before = state.words.map((w) => ({ ...w }));
    invokeMock.mockImplementation((command: string) => {
      if (command === 'updateDictionaryEvidence') {
        return Promise.reject(new Error('command failed'));
      }
      return Promise.resolve(undefined);
    });

    await expect(state.capture('local-1', EVIDENCE)).rejects.toThrow('command failed');

    expect(state.words).toEqual(before);
    expect(outboxCalls).toHaveLength(0);
  });
});

describe('DictionaryState realtime spread — REQ-DSI-003 scenario 2', () => {
  it('applies all ten rich-entry fields when a newer remote row wins', async () => {
    const state = await seededState();
    state.subscribeToRemoteChanges();
    expect(realtimeCallback).not.toBeNull();

    realtimeCallback!({
      id: 'local-1',
      userId: 'user-1',
      word: 'Abyss',
      normalizedWord: 'abyss',
      tags: ['remote'],
      srsStage: 0,
      updatedAt: '2025-06-01T00:00:00Z',
      deletedAt: null,
      createdAt: '2025-01-01T00:00:00Z',
      definition: 'Remote definition',
      partOfSpeech: 'adjective',
      phonetic: '/rɪˈməʊt/',
      example: 'A remote example.',
      quote: 'A remote quote.',
      sourceBookId: 'book-9',
      sourceBookTitle: 'Remote Book',
      sourceBookAuthor: 'R. Author',
      sourceChapter: 'Chapter 9',
      sourceLocator: 'epubcfi(/6/10!/4/2)',
    });

    const entry = state.words[0];
    expect(entry.definition).toBe('Remote definition');
    expect(entry.partOfSpeech).toBe('adjective');
    expect(entry.phonetic).toBe('/rɪˈməʊt/');
    expect(entry.example).toBe('A remote example.');
    expect(entry.quote).toBe('A remote quote.');
    expect(entry.sourceBookId).toBe('book-9');
    expect(entry.sourceBookTitle).toBe('Remote Book');
    expect(entry.sourceBookAuthor).toBe('R. Author');
    expect(entry.sourceChapter).toBe('Chapter 9');
    expect(entry.sourceLocator).toBe('epubcfi(/6/10!/4/2)');
    expect(entry.tags).toEqual(['remote']);
  });

  it('clears a field the remote row reports as null', async () => {
    const state = await seededState();
    state.subscribeToRemoteChanges();

    realtimeCallback!({
      id: 'local-1',
      userId: 'user-1',
      word: 'Abyss',
      normalizedWord: 'abyss',
      tags: [],
      srsStage: 0,
      updatedAt: '2025-06-01T00:00:00Z',
      deletedAt: null,
      createdAt: '2025-01-01T00:00:00Z',
      quote: null,
    });

    expect(state.words[0].quote).toBeNull();
    expect(state.words[0].definition).toBeNull();
  });
});
