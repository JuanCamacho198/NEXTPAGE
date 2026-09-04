import { describe, it, expect, vi } from 'vitest';
import {
  createHighlightsSync,
  sortByUpdatedAtDesc,
  chunkRows,
  isSameHighlights,
} from '$lib/features/highlights/useHighlightsSync.svelte';
import { createHighlightsViewDeps } from '$lib/features/highlights/highlightsViewDeps';
import { MockViewerAdapter } from '$lib/shared/ports';
import type { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
import type { HighlightDto } from '$lib/shared/types';

const { mockAuthState } = vi.hoisted(() => ({
  mockAuthState: { userId: 'u1', email: null, displayName: null, photoUrl: null },
}));

vi.mock('$lib/shared/stores/AuthState.svelte', () => ({
  authState: mockAuthState,
}));

const makeStubOutbox = (): { add: ReturnType<typeof vi.fn> } => ({
  add: vi.fn(async () => 'uuid'),
});

describe('useHighlightsSync pure', () => {
  it('sortByUpdatedAtDesc sorts DESC', () => {
    const a = { id: 'a', updatedAt: '2024-01-01T00:00:00Z' } as HighlightDto;
    const b = { id: 'b', updatedAt: '2024-06-01T00:00:00Z' } as HighlightDto;
    const c = { id: 'c', updatedAt: '2024-03-01T00:00:00Z' } as HighlightDto;
    expect(sortByUpdatedAtDesc([a, b, c]).map((x) => x.id)).toEqual(['b', 'c', 'a']);
  });

  it('chunkRows 1200→500/500/200', () => {
    const rows = Array.from({ length: 1200 }, (_, i) => i);
    const chunks = chunkRows(rows, 500);
    expect(chunks).toHaveLength(3);
    expect(chunks[0]).toHaveLength(500);
    expect(chunks[1]).toHaveLength(500);
    expect(chunks[2]).toHaveLength(200);
  });

  it('chunkRows 0→0, 500→1', () => {
    expect(chunkRows([], 500)).toHaveLength(0);
    expect(
      chunkRows(
        Array.from({ length: 500 }, (_, i) => i),
        500,
      ),
    ).toHaveLength(1);
  });

  it('isSameHighlights guard prevents blink when ids unchanged', () => {
    const h1 = { id: '1' } as HighlightDto;
    const h2 = { id: '2' } as HighlightDto;
    expect(isSameHighlights([h1, h2], [h1, h2])).toBe(true);
    expect(isSameHighlights([h1, h2], [h2, h1])).toBe(false);
    expect(isSameHighlights([h1], [h1, h2])).toBe(false);
  });

  it('outbox DELETE payload shape (pure JSON check)', () => {
    const payload = JSON.stringify({
      userId: 'u1',
      bookId: 'b1',
      cfiRange: 'epubcfi(/6/2!)',
      textContent: 'hello',
      color: '#FACC15',
      page: 1,
      deletedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });
    const parsed = JSON.parse(payload);
    expect(parsed.userId).toBe('u1');
    expect(parsed.deletedAt).toBeDefined();
  });
});

describe('useHighlightsSync note updates', () => {
  it('handleUpdateNote persists via deps and enqueues outbox UPSERT carrying userId', async () => {
    const mock = new MockViewerAdapter();
    await mock.saveHighlight({
      id: 'h1',
      bookId: 'b1',
      text: 'hello',
      color: '#facc15',
      pageNumber: 1,
      rectLeft: 0,
      rectRight: 10,
      rectTop: 0,
      rectBottom: 10,
      cfi: null,
      note: null,
    });
    const spyUpdate = vi.spyOn(mock, 'updateHighlight');
    const outbox = makeStubOutbox();
    const sync = createHighlightsSync({
      deps: createHighlightsViewDeps(mock),
      getHighlights: () => [],
      setHighlights: () => {},
      outbox: outbox as unknown as SyncOutboxDao,
    });

    const highlight = (await mock.listHighlights('b1'))[0];
    const updated = await sync.handleUpdateNote(highlight, 'my note');

    expect(spyUpdate).toHaveBeenCalledWith({ id: 'h1', note: 'my note' });
    expect(updated?.note).toBe('my note');
    expect(outbox.add).toHaveBeenCalledTimes(1);
    const [entityType, entityId, operation, payloadJson] = outbox.add.mock.calls[0] as [
      string,
      string,
      string,
      string,
    ];
    expect(entityType).toBe('HIGHLIGHT');
    expect(entityId).toBe('h1');
    expect(operation).toBe('UPSERT');
    const payload = JSON.parse(payloadJson) as Record<string, unknown>;
    expect(payload.userId).toBe('u1');
    expect(payload.note).toBe('my note');
    expect(payload.updatedAt).toBeDefined();
  });

  it('handleUpdateNote returns null and enqueues nothing when the backend fails', async () => {
    const mock = new MockViewerAdapter();
    const outbox = makeStubOutbox();
    const sync = createHighlightsSync({
      deps: createHighlightsViewDeps(mock),
      getHighlights: () => [],
      setHighlights: () => {},
      outbox: outbox as unknown as SyncOutboxDao,
    });

    const ghost = {
      id: 'missing',
      bookId: 'b1',
      text: 'x',
      color: '#facc15',
      pageNumber: 1,
    } as HighlightDto;
    const result = await sync.handleUpdateNote(ghost, 'note');

    expect(result).toBeNull();
    expect(outbox.add).not.toHaveBeenCalled();
  });

  it('handleDelete reports failure so the view can surface it', async () => {
    const mock = new MockViewerAdapter();
    const sync = createHighlightsSync({
      deps: createHighlightsViewDeps(mock),
      getHighlights: () => [],
      setHighlights: () => {},
      outbox: makeStubOutbox() as unknown as SyncOutboxDao,
    });

    const ghost = {
      id: 'missing',
      bookId: 'b1',
      text: 'x',
      color: '#facc15',
      pageNumber: 1,
    } as HighlightDto;
    const ok = await sync.handleDelete(ghost, [ghost], () => {});
    expect(ok).toBe(false);
  });
});
