import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

vi.mock('$lib/shared/api/tauriClient', () => ({
  saveHighlight: vi.fn().mockResolvedValue(undefined),
  deleteHighlight: vi.fn().mockResolvedValue(undefined),
  updateHighlight: vi.fn().mockResolvedValue({}),
  listHighlights: vi.fn().mockResolvedValue([]),
}));

vi.mock('$lib/stores/authState.svelte', () => ({
  authState: { userId: 'user-1' },
}));

vi.mock('$lib/shared/stores/ReaderDomainState.svelte', () => ({
  readerState: { locatorJson: '{"href":"a.xhtml"}', highlightsVersion: 1, cfiLocation: '' },
}));

vi.mock('$lib/shared/debug/debugState.svelte', () => ({
  debugState: {
    epub: {
      colorPickCount: 0,
      lastPickedColor: '',
      saveHighlightCallCount: 0,
      saveHighlightLastError: '',
      failedHighlightIds: [] as string[],
      persistedHighlightsCount: 0,
    },
  },
}));

vi.mock('$lib/shared/outbox/SyncOutboxDao', () => ({
  SyncOutboxDao: class {
    add = vi.fn().mockResolvedValue('outbox-id');
  },
}));

import { createSpineResolver } from '$lib/features/reader/chrome/useSpineResolver.svelte';
import { createHighlights } from '$lib/features/reader/chrome/useHighlights.svelte';
import { listHighlights, saveHighlight } from '$lib/shared/api/tauriClient';
import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';

describe('useHighlights', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('single-flight 32ms: duplicate reloadHighlights collapse to 1 DB read, queued follow-up makes 2', async () => {
    const listMock = vi.mocked(listHighlights);
    // delayed resolve to keep in-flight
    let resolveFirst: (v: unknown) => void = () => {};
    listMock.mockImplementationOnce(
      () =>
        new Promise((res) => {
          resolveFirst = res as unknown as (v: unknown) => void;
        }),
    );
    listMock.mockResolvedValueOnce([]);

    const spine = createSpineResolver({ parseEpub: vi.fn().mockResolvedValue({ spineHrefs: [] }) });
    const outbox = new SyncOutboxDao();
    const book = { id: 'book-1', filePath: 'C:/book.epub', format: 'epub' } as unknown as ReturnType<typeof createHighlights> extends never ? never : any;
    const getBook = vi.fn().mockReturnValue(book);
    const h = createHighlights({ getBook: () => book, spine, outbox });

    // trigger 3 times within 32ms window
    h.reloadHighlights();
    h.reloadHighlights();
    h.reloadHighlights();
    // advance 32ms -> one run starts (in-flight)
    vi.advanceTimersByTime(32);
    await Promise.resolve();
    expect(listMock).toHaveBeenCalledTimes(1);

    // while in-flight, queue another reload
    h.reloadHighlights();
    vi.advanceTimersByTime(32);
    await Promise.resolve();
    // should be queued, not immediate second call until first resolves
    expect(listMock).toHaveBeenCalledTimes(1);

    resolveFirst([]);
    await Promise.resolve();
    // allow queued run to start (next tick)
    await vi.advanceTimersByTimeAsync(32);
    await Promise.resolve();
    // queued follow-up should have triggered second DB read
    expect(listMock).toHaveBeenCalledTimes(2);

    h.cleanup();
  });

  it('race: handleColorSelect uses capturedData after lastSelectionData = null (220ms)', async () => {
    const saveMock = vi.mocked(saveHighlight);
    saveMock.mockResolvedValue(undefined);
    const spine = createSpineResolver({ parseEpub: vi.fn().mockResolvedValue({ spineHrefs: [] }) });
    const outbox = new SyncOutboxDao();
    const book = { id: 'book-1', filePath: 'C:/book.pdf', format: 'pdf' } as unknown as never;
    const h = createHighlights({ getBook: () => book as any, spine, outbox });

    const captured = {
      text: 'selected text',
      bounds: { left: 0, top: 0, right: 100, bottom: 20 },
      rects: [{ left: 0, top: 0, width: 100, height: 20 }],
      pageNumber: 3,
      cfi: null,
    };

    // simulate global lastSelectionData already nulled by selectionchange
    // but captured data still passed
    await h.handleColorSelect('#facc15', captured as never);
    // optimistic push happens immediately
    expect(h.persistedHighlights).toHaveLength(1);
    expect(h.persistedHighlights[0].text).toBe('selected text');
    expect(h.persistedHighlights[0].pageNumber).toBe(3);
    expect(saveMock).toHaveBeenCalledWith(
      expect.objectContaining({ text: 'selected text', pageNumber: 3 }),
    );

    // 220ms timeout clears selection range without needing lastSelectionData
    const removeAllRanges = vi.fn();
    Object.defineProperty(window, 'getSelection', {
      configurable: true,
      value: () => ({ removeAllRanges }),
    });
    vi.advanceTimersByTime(220);
    expect(removeAllRanges).toHaveBeenCalled();

    h.cleanup();
  });

  it('ensureSpineHrefs called before reload maps readium href -> spine idx', async () => {
    const listMock = vi.mocked(listHighlights);
    listMock.mockResolvedValue([
      {
        id: 'h1',
        bookId: 'book-1',
        text: 'hi',
        color: '#facc15',
        pageNumber: 99,
        cfi: 'readium:OEBPS/Text/cap1.xhtml',
        note: null,
        createdAt: '',
        updatedAt: '',
      } as never,
    ]);
    const parseMock = vi.fn().mockResolvedValue({ spineHrefs: ['OEBPS/Text/cap1.xhtml', 'OEBPS/Text/cap2.xhtml'] });
    const spine = createSpineResolver({ parseEpub: parseMock });
    const outbox = new SyncOutboxDao();
    const book = { id: 'book-1', filePath: 'C:/book.epub', format: 'epub' } as unknown as never;
    const h = createHighlights({ getBook: () => book as any, spine, outbox });

    h.reloadHighlights();
    vi.advanceTimersByTime(32);
    // need to flush async ensureSpine + list
    await Promise.resolve();
    await Promise.resolve();
    // wait for runReloadHighlights to complete (uses saga)
    await vi.advanceTimersByTimeAsync(10);
    await Promise.resolve();

    expect(parseMock).toHaveBeenCalled();
    // pageNumber 99 should be fixed to 0 via spine mapping
    expect(h.persistedHighlights[0].pageNumber).toBe(0);

    h.cleanup();
  });

  it('optimistic merge preserves not-yet-in-DB highlights', async () => {
    const listMock = vi.mocked(listHighlights);
    listMock.mockResolvedValue([
      { id: 'h-db', bookId: 'b1', text: 'db', color: '#facc15', pageNumber: 1, note: null, createdAt: '', updatedAt: '', cfi: null } as never,
    ]);
    const spine = createSpineResolver({ parseEpub: vi.fn().mockResolvedValue({ spineHrefs: [] }) });
    const outbox = new SyncOutboxDao();
    const book = { id: 'b1', filePath: 'C:/book.pdf', format: 'pdf' } as unknown as never;
    const h = createHighlights({ getBook: () => book as any, spine, outbox });
    // optimistic highlight already in memory not yet in DB
    h.persistedHighlights = [
      { id: 'h-opt', color: '#facc15', pageNumber: 2, rects: [], cfi: null, text: 'opt', note: null },
    ];
    h.reloadHighlights();
    vi.advanceTimersByTime(32);
    await Promise.resolve();
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(10);
    expect(h.persistedHighlights.map((x) => x.id).sort()).toEqual(['h-db', 'h-opt'].sort());
    h.cleanup();
  });
});
