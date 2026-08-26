import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

vi.mock('$lib/shared/api/tauriClient', () => ({
  listBookmarks: vi.fn().mockResolvedValue([]),
  saveBookmark: vi.fn().mockResolvedValue(undefined),
  deleteBookmark: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('$lib/shared/stores/AuthState.svelte', () => ({
  authState: { userId: 'user-1' },
}));

vi.mock('$lib/shared/outbox/SyncOutboxDao', () => ({
  SyncOutboxDao: class {
    add = vi.fn().mockResolvedValue('id');
  },
}));

import { createBookmarksPanel } from '$lib/features/reader/chrome/useBookmarksPanel.svelte';
import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';

describe('useBookmarksPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('ribbon 2200ms shows then hides, retrigger resets timer', () => {
    const outbox = new SyncOutboxDao();
    const p = createBookmarksPanel({ outboxDao: outbox });
    expect(p.showBookmarkRibbon).toBe(false);
    p.triggerBookmarkRibbon();
    expect(p.showBookmarkRibbon).toBe(true);
    expect(p._ribbonTimer).not.toBeNull();
    vi.advanceTimersByTime(2199);
    expect(p.showBookmarkRibbon).toBe(true);
    // retrigger before hide should reset timer
    p.triggerBookmarkRibbon();
    vi.advanceTimersByTime(2199);
    expect(p.showBookmarkRibbon).toBe(true);
    vi.advanceTimersByTime(1);
    expect(p.showBookmarkRibbon).toBe(false);
    expect(p._ribbonTimer).toBeNull();
    p.cleanup();
  });

  it('toggleBookmarks flips open, cleanup clears ribbon timer', () => {
    const p = createBookmarksPanel({});
    expect(p.showBookmarks).toBe(false);
    p.toggleBookmarks();
    expect(p.showBookmarks).toBe(true);
    p.toggleBookmarks();
    expect(p.showBookmarks).toBe(false);
    p.triggerBookmarkRibbon();
    expect(p._ribbonTimer).not.toBeNull();
    p.cleanup();
    expect(p._ribbonTimer).toBeNull();
    vi.advanceTimersByTime(2200);
    // after cleanup, ribbon stays true (timer cleared) — not auto-hidden
    expect(p.showBookmarkRibbon).toBe(true);
  });

  it('bookmarksState delegates loadBookmarks via outboxDao single instance', async () => {
    const outbox = new SyncOutboxDao();
    const p = createBookmarksPanel({ outboxDao: outbox });
    expect(p.bookmarksState).toBeDefined();
    expect(p.bookmarksState.bookmarksList).toEqual([]);
    // loadBookmarks should be callable (mocked listBookmarks returns [])
    await p.bookmarksState.loadBookmarks('book-1');
    expect(p.bookmarksState.bookmarksLoading).toBe(false);
    p.cleanup();
  });

  it('ribbon not auto-hide before 2200ms', () => {
    const p = createBookmarksPanel({});
    p.triggerBookmarkRibbon();
    vi.advanceTimersByTime(1000);
    expect(p.showBookmarkRibbon).toBe(true);
    vi.advanceTimersByTime(1200);
    expect(p.showBookmarkRibbon).toBe(false);
    p.cleanup();
  });
});
