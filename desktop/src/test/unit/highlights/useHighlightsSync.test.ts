import { describe, it, expect } from 'vitest';
import {
  sortByUpdatedAtDesc,
  chunkRows,
  isSameHighlights,
} from '$lib/features/highlights/useHighlightsSync.svelte';
import type { HighlightDto } from '$lib/shared/types';

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
