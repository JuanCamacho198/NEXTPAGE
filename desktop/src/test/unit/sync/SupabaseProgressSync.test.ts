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
  return {
    client: {
      from: vi.fn().mockReturnValue(chain),
      channel: vi.fn(),
    },
    chain,
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
