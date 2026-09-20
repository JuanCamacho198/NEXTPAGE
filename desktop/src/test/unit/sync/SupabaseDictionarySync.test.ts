/**
 * REQ-DSI-001 / REQ-DSI-002 — dictionary sync identity.
 * REQ-DSI-003 — evidence propagation through the remote row, the upsert payload
 * and `mapRow`.
 * REQ-DSI-005 — no cold-start remote pull.
 *
 * The fake client simulates the `user_dictionary_words` table with its
 * `(user_id, normalized_word)` unique constraint and a server-generated id, so
 * these tests prove the payload contract rather than mirror the implementation.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
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
    select: (_columns: string) => ({
      eq: (column: string, value: unknown) => ({
        is: (column2: string, value2: unknown) => {
          const data = remoteRows.filter(
            (r) =>
              r[column] === value && (value2 === null ? r[column2] == null : r[column2] === value2),
          );
          return Promise.resolve({ data, error: null });
        },
      }),
    }),
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
    srsStage: 0,
    updatedAt: '2025-06-01T00:00:00Z',
    deletedAt: null,
    createdAt: '2025-01-01T00:00:00Z',
    definition: null,
    partOfSpeech: null,
    phonetic: null,
    example: null,
    quote: null,
    sourceBookId: null,
    sourceBookTitle: null,
    sourceBookAuthor: null,
    sourceChapter: null,
    sourceLocator: null,
    ...overrides,
  };
}

/** Every rich-entry column the upsert payload and `mapRow` must carry (REQ-DSI-003). */
const EVIDENCE_SNAPSHOT = {
  definition: 'a deep hole',
  partOfSpeech: 'noun',
  phonetic: '/əˈbɪs/',
  example: 'the abyss stared back',
  quote: 'He gazed into the abyss, and the abyss gazed back.',
  sourceBookId: 'book-1',
  sourceBookTitle: 'Meditations',
  sourceBookAuthor: 'Marcus Aurelius',
  sourceChapter: 'Book IV',
  sourceLocator: 'epubcfi(/6/14!/4/2/2)',
} as const;

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

describe('SupabaseDictionarySync — evidence propagation (REQ-DSI-003)', () => {
  it('sends all ten rich-entry columns on upsert, still without the local id', async () => {
    const sync = new SupabaseDictionarySync('user-1');

    await sync.upsert(makeRow(EVIDENCE_SNAPSHOT));

    expect(upsertPayloads).toHaveLength(1);
    expect(upsertPayloads[0]).not.toHaveProperty('id');
    expect(upsertPayloads[0]).toMatchObject({
      definition: 'a deep hole',
      part_of_speech: 'noun',
      phonetic: '/əˈbɪs/',
      example: 'the abyss stared back',
      quote: 'He gazed into the abyss, and the abyss gazed back.',
      source_book_id: 'book-1',
      source_book_title: 'Meditations',
      source_book_author: 'Marcus Aurelius',
      source_chapter: 'Book IV',
      source_locator: 'epubcfi(/6/14!/4/2/2)',
    });
  });

  it('sends an explicit null for every absent evidence value — no omitted keys', async () => {
    const sync = new SupabaseDictionarySync('user-1');

    await sync.upsert(makeRow());

    const payload = upsertPayloads[0];
    for (const column of [
      'definition',
      'part_of_speech',
      'phonetic',
      'example',
      'quote',
      'source_book_id',
      'source_book_title',
      'source_book_author',
      'source_chapter',
      'source_locator',
    ]) {
      expect(payload).toHaveProperty(column, null);
    }
  });

  it('maps all ten columns back through mapRow, byte-identical', async () => {
    remoteRows = [
      {
        id: 'server-7',
        user_id: 'user-1',
        word: 'Abyss',
        normalized_word: 'abyss',
        tags: [],
        srs_stage: 0,
        updated_at: '2025-06-01T00:00:00Z',
        created_at: '2025-01-01T00:00:00Z',
        deleted_at: null,
        definition: 'a deep hole',
        part_of_speech: 'noun',
        phonetic: '/əˈbɪs/',
        example: 'the abyss stared back',
        quote: 'He gazed into the abyss, and the abyss gazed back.',
        source_book_id: 'book-1',
        source_book_title: 'Meditations',
        source_book_author: 'Marcus Aurelius',
        source_chapter: 'Book IV',
        source_locator: 'epubcfi(/6/14!/4/2/2)',
      },
    ];
    const sync = new SupabaseDictionarySync('user-1');

    const rows = await sync.fetchAll();

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject(EVIDENCE_SNAPSHOT);
  });

  it('is total for absent keys and null-safe for an explicit null', async () => {
    remoteRows = [
      {
        id: 'server-8',
        user_id: 'user-1',
        word: 'Abyss',
        normalized_word: 'abyss',
        quote: null,
      },
    ];
    const sync = new SupabaseDictionarySync('user-1');

    const rows = await sync.fetchAll();

    expect(rows[0]).toMatchObject({
      definition: null,
      partOfSpeech: null,
      phonetic: null,
      example: null,
      quote: null,
      sourceBookId: null,
      sourceBookTitle: null,
      sourceBookAuthor: null,
      sourceChapter: null,
      sourceLocator: null,
    });
  });

  it('coerces a non-string primitive without erasing a present value', async () => {
    remoteRows = [
      {
        id: 'server-9',
        user_id: 'user-1',
        word: 'Abyss',
        normalized_word: 'abyss',
        example: 0,
        source_chapter: 42,
      },
    ];
    const sync = new SupabaseDictionarySync('user-1');

    const rows = await sync.fetchAll();

    expect(rows[0].example).toBe('0');
    expect(rows[0].sourceChapter).toBe('42');
  });
});

describe('SupabaseDictionarySync — no cold-start remote pull (REQ-DSI-005)', () => {
  it('never invokes fetchAll from the upsert or delete paths', async () => {
    const fetchSpy = vi.spyOn(SupabaseDictionarySync.prototype, 'fetchAll');
    const sync = new SupabaseDictionarySync('user-1');

    await sync.upsert(makeRow(EVIDENCE_SNAPSHOT));
    await sync.delete('abyss');

    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('leaves fetchAll unwired in the sync service, the dictionary store and the IPC client', () => {
    const sharedRoot = resolve(process.cwd(), 'src', 'lib', 'shared');
    for (const relative of [
      'services/SyncService.ts',
      'stores/DictionaryState.svelte.ts',
      'api/tauriClient.ts',
    ]) {
      const source = readFileSync(resolve(sharedRoot, relative), 'utf8');
      expect(source, `${relative} must not wire a remote dictionary pull`).not.toContain(
        'fetchAll',
      );
    }
  });

  it('exposes no remote-pull dictionary command through the IPC surface', () => {
    const clientSource = readFileSync(
      resolve(process.cwd(), 'src', 'lib', 'shared', 'api', 'tauriClient.ts'),
      'utf8',
    );
    const dictionaryCommands = [
      ...clientSource.matchAll(/invoke(?:<[^>]*>)?\('([A-Za-z]*[Dd]ictionary[A-Za-z]*)'/g),
    ].map((match) => match[1]);

    // The guard is only meaningful while the scan still finds the dictionary surface.
    expect(dictionaryCommands.length).toBeGreaterThanOrEqual(6);
    expect(dictionaryCommands.filter((name) => /pull|fetch|download|remote/i.test(name))).toEqual(
      [],
    );
  });
});
