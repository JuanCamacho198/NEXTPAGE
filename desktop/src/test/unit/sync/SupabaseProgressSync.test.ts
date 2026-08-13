/**
 * Unit tests for the SupabaseProgressSync live-session gate (WU2 — desktop-session-persistence).
 *
 * SR-1.2: every PostgREST method must no-op when the live-session gate fails —
 * no request fires, no throw, no markFailed. The gate is `hasLiveSession()`
 * AND `this.userId === authState.userId` (D1/D3, stale-instance protection).
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import type { SupabaseProgressRow } from '$lib/shared/sync/SupabaseProgressSync';

// ---- Mock control variables (set before each test) ----
let mockHasLiveSession = vi.fn<() => boolean>();
let mockAuthUserId = vi.fn<() => string | null>();

// The client the mocked getSessionClient() returns. Replaced per test; the
// sync constructor caches it, so assertions read this same instance.
let currentClient: ReturnType<typeof makeClient>;

vi.mock('$lib/services/supabase', () => ({
  getSessionClient: () => currentClient.client,
  hasLiveSession: () => mockHasLiveSession(),
}));

vi.mock('$lib/stores/authState.svelte', () => ({
  authState: {
    get userId(): string | null {
      return mockAuthUserId();
    },
  },
}));

// Fake supabase client: `.from(table)` returns a chainable query. If any
// gated test reaches `from`, the "not.toHaveBeenCalled()" assertion fails.
function makeClient() {
  const chain = {
    select: vi.fn().mockReturnThis(),
    eq: vi.fn().mockReturnThis(),
    maybeSingle: vi.fn().mockResolvedValue({ data: null, error: null }),
    upsert: vi.fn().mockResolvedValue({ data: null, error: null }),
    update: vi.fn().mockReturnThis(),
    order: vi.fn().mockReturnThis(),
    limit: vi.fn().mockReturnThis(),
  };
  const channel = {
    on: vi.fn().mockReturnThis(),
    subscribe: vi.fn().mockReturnThis(),
    unsubscribe: vi.fn().mockReturnThis(),
  };
  return {
    client: {
      from: vi.fn().mockReturnValue(chain),
      channel: vi.fn().mockReturnValue(channel),
    },
    chain,
    channel,
  };
}

let SupabaseProgressSync: typeof import('$lib/shared/sync/SupabaseProgressSync').SupabaseProgressSync;

beforeAll(async () => {
  const mod = await import('$lib/shared/sync/SupabaseProgressSync');
  SupabaseProgressSync = mod.SupabaseProgressSync;
});

beforeEach(() => {
  vi.clearAllMocks();
  mockHasLiveSession.mockReturnValue(false);
  mockAuthUserId.mockReturnValue('u1');
  currentClient = makeClient();
});

function makeSync(userId = 'u1') {
  return new SupabaseProgressSync(userId);
}

const progressRow: SupabaseProgressRow = {
  userId: 'u1',
  bookId: 'book-1',
  cfiLocation: '/6/4',
  percentage: 42,
  updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('SupabaseProgressSync — live-session gate', () => {
  it('upsertProgress no-ops without a live session: no request, no throw', async () => {
    mockHasLiveSession.mockReturnValue(false);
    mockAuthUserId.mockReturnValue('u1');

    await expect(makeSync().upsertProgress(progressRow)).resolves.toBeUndefined();

    expect(currentClient.client.from).not.toHaveBeenCalled();
    expect(currentClient.chain.upsert).not.toHaveBeenCalled();
  });

  it('upsertProgress no-ops when the instance userId differs from the current user (stale instance)', async () => {
    mockHasLiveSession.mockReturnValue(true); // live session is for u2
    mockAuthUserId.mockReturnValue('u2');

    await expect(makeSync('u1').upsertProgress(progressRow)).resolves.toBeUndefined();

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('upsertProgress fires a request when the gate passes', async () => {
    mockHasLiveSession.mockReturnValue(true);
    mockAuthUserId.mockReturnValue('u1');

    await makeSync().upsertProgress(progressRow);

    expect(currentClient.client.from).toHaveBeenCalledWith('reading_progress');
    expect(currentClient.chain.upsert).toHaveBeenCalledTimes(1);
  });

  it('fetchProgress returns [] when gated (no request)', async () => {
    mockHasLiveSession.mockReturnValue(false);

    await expect(makeSync().fetchProgress()).resolves.toEqual([]);

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('fetchBookState returns an empty shape when gated', async () => {
    mockHasLiveSession.mockReturnValue(false);

    await expect(makeSync().fetchBookState('book-1')).resolves.toEqual({
      progress: null,
      bookmarks: [],
      highlights: [],
    });

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('fetchProgressForBook returns null when gated', async () => {
    mockHasLiveSession.mockReturnValue(false);

    await expect(makeSync().fetchProgressForBook('book-1')).resolves.toBeNull();

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('fetchBookmarks and fetchHighlights return [] when gated', async () => {
    mockHasLiveSession.mockReturnValue(false);
    const sync = makeSync();

    await expect(sync.fetchBookmarks('book-1')).resolves.toEqual([]);
    await expect(sync.fetchHighlights('book-1')).resolves.toEqual([]);

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('upsertTag returns the unpersisted input when gated (no request, no fabricated id)', async () => {
    mockHasLiveSession.mockReturnValue(false);

    const tag = { userId: 'u1', name: 'philosophy', color: '#fff' };
    const result = await makeSync().upsertTag(tag);

    expect(result).toEqual({ userId: 'u1', name: 'philosophy', color: '#fff' });
    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('linkTagToHighlight no-ops when gated', async () => {
    mockHasLiveSession.mockReturnValue(false);

    await expect(makeSync().linkTagToHighlight('h1', 't1')).resolves.toBeUndefined();

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('importFromDrive no-ops when gated (rows untouched, no chunked upsert)', async () => {
    mockHasLiveSession.mockReturnValue(false);

    await expect(makeSync().importFromDrive([progressRow])).resolves.toBeUndefined();

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('importBookmarksFromDrive and importHighlightsFromDrive no-op when gated', async () => {
    mockHasLiveSession.mockReturnValue(false);
    const sync = makeSync();

    await expect(
      sync.importBookmarksFromDrive([
        {
          userId: 'u1',
          bookId: 'book-1',
          cfiLocation: '/6/4',
          updatedAt: '2026-01-01T00:00:00.000Z',
        },
      ]),
    ).resolves.toBeUndefined();
    await expect(
      sync.importHighlightsFromDrive([
        {
          userId: 'u1',
          bookId: 'book-1',
          cfiRange: '/6/4',
          textContent: 'x',
          color: 'yellow',
          updatedAt: '2026-01-01T00:00:00.000Z',
        },
      ]),
    ).resolves.toBeUndefined();

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });
});

describe('SupabaseProgressSync — reading sessions pull (D11/D14)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHasLiveSession.mockReturnValue(false);
    mockAuthUserId.mockReturnValue('u1');
    currentClient = makeClient();
  });

  it('mapReadingSessionRow converts snake_case to camelCase with epoch updatedAt', () => {
    const sync = makeSync();

    const mapped = sync.mapReadingSessionRow({
      id: 'sess_x',
      user_id: 'u1',
      book_id: 'b1',
      started_at: '2026-08-13T10:00:00.000Z',
      duration_minutes: 25,
      date: '2026-08-13T00:00:00.000Z',
      updated_at: '2026-08-13T10:30:00.000Z',
      start_percentage: 10,
      end_percentage: 35,
    });

    expect(mapped).toEqual({
      id: 'sess_x',
      userId: 'u1',
      bookId: 'b1',
      startedAt: '2026-08-13T10:00:00.000Z',
      durationMinutes: 25,
      date: '2026-08-13T00:00:00.000Z',
      updatedAtEpochMillis: Date.parse('2026-08-13T10:30:00.000Z'),
      startPercentage: 10,
      endPercentage: 35,
    });
  });

  it('mapReadingSessionRow keeps nullable percentages null and coerces duration', () => {
    const sync = makeSync();

    const mapped = sync.mapReadingSessionRow({
      id: 'sess_y',
      user_id: 'u1',
      book_id: 'b2',
      started_at: '2026-08-12T09:00:00.000Z',
      duration_minutes: 0,
      date: '2026-08-12T00:00:00.000Z',
      updated_at: '2026-08-12T09:15:00.000Z',
      start_percentage: null,
      end_percentage: null,
    });

    expect(mapped.durationMinutes).toBe(0);
    expect(mapped.startPercentage).toBeNull();
    expect(mapped.endPercentage).toBeNull();
  });

  it('fetchReadingSessions returns [] when gated (no request)', async () => {
    mockHasLiveSession.mockReturnValue(false);

    await expect(makeSync().fetchReadingSessions()).resolves.toEqual([]);

    expect(currentClient.client.from).not.toHaveBeenCalled();
  });

  it('fetchReadingSessions selects reading_sessions filtered by user_id and maps rows', async () => {
    mockHasLiveSession.mockReturnValue(true);
    mockAuthUserId.mockReturnValue('u1');
    currentClient.chain.eq.mockResolvedValueOnce({
      data: [
        {
          id: 'sess_x',
          user_id: 'u1',
          book_id: 'b1',
          started_at: '2026-08-13T10:00:00.000Z',
          duration_minutes: 25,
          date: '2026-08-13T00:00:00.000Z',
          updated_at: '2026-08-13T10:30:00.000Z',
          start_percentage: null,
          end_percentage: null,
        },
      ],
      error: null,
    });

    const rows = await makeSync().fetchReadingSessions();

    expect(currentClient.client.from).toHaveBeenCalledWith('reading_sessions');
    expect(currentClient.chain.select).toHaveBeenCalledWith('*');
    expect(currentClient.chain.eq).toHaveBeenCalledWith('user_id', 'u1');
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      id: 'sess_x',
      userId: 'u1',
      bookId: 'b1',
      durationMinutes: 25,
      updatedAtEpochMillis: Date.parse('2026-08-13T10:30:00.000Z'),
    });
  });

  it('fetchReadingSessions throws on error', async () => {
    mockHasLiveSession.mockReturnValue(true);
    currentClient.chain.eq.mockResolvedValueOnce({ data: null, error: { message: 'db down' } });

    await expect(makeSync().fetchReadingSessions()).rejects.toMatchObject({
      message: 'db down',
    });
  });

  it('subscribeToReadingSessions uses sessions:{userId} channel with user filter (Insert/Update only)', () => {
    const sync = makeSync();
    const callback = vi.fn();

    const unsubscribe = sync.subscribeToReadingSessions(callback);

    expect(currentClient.client.channel).toHaveBeenCalledWith('sessions:u1');
    expect(currentClient.channel.on).toHaveBeenCalledTimes(2);

    const insertConfig = currentClient.channel.on.mock.calls[0][1];
    const updateConfig = currentClient.channel.on.mock.calls[1][1];
    expect(insertConfig).toEqual({
      event: 'INSERT',
      schema: 'public',
      table: 'reading_sessions',
      filter: 'user_id=eq.u1',
    });
    expect(updateConfig).toEqual({
      event: 'UPDATE',
      schema: 'public',
      table: 'reading_sessions',
      filter: 'user_id=eq.u1',
    });
    // No DELETE/Select registration — Android parity (local table is the
    // merged source of truth; remote deletes never un-merge local rows).
    expect(currentClient.channel.on.mock.calls.some((c) => c[1].event === 'DELETE')).toBe(false);
    expect(currentClient.channel.subscribe).toHaveBeenCalledTimes(1);

    // Fire the INSERT handler: the callback receives the mapped row.
    const insertHandler = currentClient.channel.on.mock.calls[0][2];
    insertHandler({
      new: {
        id: 'sess_z',
        user_id: 'u1',
        book_id: 'b3',
        started_at: '2026-08-14T08:00:00.000Z',
        duration_minutes: 5,
        date: '2026-08-14T00:00:00.000Z',
        updated_at: '2026-08-14T08:05:00.000Z',
        start_percentage: null,
        end_percentage: null,
      },
    });
    expect(callback).toHaveBeenCalledTimes(1);
    expect(callback.mock.calls[0][0]).toMatchObject({ id: 'sess_z', userId: 'u1', bookId: 'b3' });

    // The UPDATE handler also maps payload.new to the callback.
    const updateHandler = currentClient.channel.on.mock.calls[1][2];
    updateHandler({
      new: {
        id: 'sess_z',
        user_id: 'u1',
        book_id: 'b3',
        started_at: '2026-08-14T08:00:00.000Z',
        duration_minutes: 6,
        date: '2026-08-14T00:00:00.000Z',
        updated_at: '2026-08-14T08:06:00.000Z',
        start_percentage: null,
        end_percentage: null,
      },
    });
    expect(callback).toHaveBeenCalledTimes(2);
    expect(callback.mock.calls[1][0].durationMinutes).toBe(6);

    // Returned unsubscribe tears down the channel.
    unsubscribe();
    expect(currentClient.channel.unsubscribe).toHaveBeenCalledTimes(1);
  });

  it('destroy() unsubscribes the reading-sessions channel too', () => {
    const sync = makeSync();
    sync.subscribeToReadingSessions(vi.fn());
    expect(currentClient.channel.unsubscribe).not.toHaveBeenCalled();

    sync.destroy();

    expect(currentClient.channel.unsubscribe).toHaveBeenCalledTimes(1);
  });
});
