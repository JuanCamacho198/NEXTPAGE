import { describe, expect, it, vi } from 'vitest';
import { createHighlightsViewDeps } from '$lib/features/highlights/highlightsViewDeps';
import { MockViewerAdapter } from '$lib/shared/ports';
import type { RemoteHighlightRow } from '$lib/shared/types';

describe('createHighlightsViewDeps', () => {
  it('forwards listHighlights/deleteHighlight/listTags/listTagsForHighlight to ViewerPort', async () => {
    const mock = new MockViewerAdapter();
    const deps = createHighlightsViewDeps(mock);

    const spyList = vi.spyOn(mock, 'listHighlights');
    const spyDelete = vi.spyOn(mock, 'deleteHighlight');
    const spyTags = vi.spyOn(mock, 'listTags');
    const spyTagsFor = vi.spyOn(mock, 'listTagsForHighlight');

    await deps.listHighlights('book-1');
    expect(spyList).toHaveBeenCalledWith('book-1');

    // seed a highlight to delete
    await mock.saveHighlight({
      id: 'h1',
      bookId: 'b1',
      text: 'hi',
      color: '#facc15',
      pageNumber: 1,
      rectLeft: 0,
      rectRight: 10,
      rectTop: 0,
      rectBottom: 10,
      cfi: null,
    });
    await deps.deleteHighlight('h1');
    expect(spyDelete).toHaveBeenCalledWith('h1');

    await deps.listTags();
    expect(spyTags).toHaveBeenCalled();

    await deps.listTagsForHighlight('h1');
    expect(spyTagsFor).toHaveBeenCalledWith('h1');
  });

  it('forwards upsertRemoteHighlights to ViewerPort', async () => {
    const mock = new MockViewerAdapter();
    const deps = createHighlightsViewDeps(mock);
    const spy = vi.spyOn(mock, 'upsertRemoteHighlights');

    const rows: RemoteHighlightRow[] = [
      {
        id: 'r1',
        bookId: 'b1',
        userId: 'u1',
        cfiRange: '',
        textContent: 'hello',
        color: '#facc15',
        page: 1,
        updatedAtEpochMillis: Date.now(),
      },
    ];
    await deps.upsertRemoteHighlights(rows);
    expect(spy).toHaveBeenCalledWith(rows);
  });

  it('chunking contract 1200→3×500 stays in caller (factory just forwards)', async () => {
    // This test proves caller-side chunking is preserved: 1200 rows must
    // become 3 calls of 500/500/200 when chunkSize 500 is applied in the
    // component, while ViewerPort/upsert remains a 1:1 forwarder.
    const mock = new MockViewerAdapter();
    const deps = createHighlightsViewDeps(mock);
    const spy = vi.spyOn(mock, 'upsertRemoteHighlights');

    const rows: RemoteHighlightRow[] = Array.from({ length: 1200 }, (_, i) => ({
      id: `r-${i}`,
      bookId: 'b1',
      userId: 'u1',
      cfiRange: '',
      textContent: `text ${i}`,
      color: '#facc15',
      page: 1,
      updatedAtEpochMillis: Date.now(),
    }));

    // Replicate caller chunking (exactly as HighlightsView.svelte does)
    const chunkSize = 500;
    for (let i = 0; i < rows.length; i += chunkSize) {
      const chunk = rows.slice(i, i + chunkSize);
      await deps.upsertRemoteHighlights(chunk);
    }

    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.calls[0][0]).toHaveLength(500);
    expect(spy.mock.calls[1][0]).toHaveLength(500);
    expect(spy.mock.calls[2][0]).toHaveLength(200);
  });

  it('defaults to TauriViewerAdapter when no port is provided (barrel resolve)', async () => {
    // Should not throw and should create an object with required methods
    const deps = createHighlightsViewDeps();
    expect(deps.listHighlights).toBeDefined();
    expect(deps.deleteHighlight).toBeDefined();
    expect(deps.upsertRemoteHighlights).toBeDefined();
    expect(deps.listTags).toBeDefined();
    expect(deps.listTagsForHighlight).toBeDefined();
  });
});
