import { describe, expect, it } from 'vitest';
import {
  HIGHLIGHT_COLORS,
  filterHighlights,
  resolveHighlightHex,
  type HighlightColorKey,
} from '$lib/features/highlights/state.svelte';
import type { HighlightDto, LibraryBookDto } from '$lib/types';

const makeHighlight = (overrides: Partial<HighlightDto> = {}): HighlightDto => ({
  id: 'h1',
  bookId: 'b1',
  text: 'A highlighted passage',
  color: '#FACC15',
  pageNumber: 12,
  note: null,
  createdAt: '2026-01-01T10:00:00.000Z',
  ...overrides,
});

const books = new Map<string, LibraryBookDto>([
  [
    'b1',
    {
      id: 'b1',
      title: 'Sample Book',
      author: 'Author',
      format: 'epub',
      currentPage: 1,
      totalPages: 100,
      progressPercentage: 1,
      coverPath: null,
      minutesRead: 0,
      updatedAt: '2026-01-01T10:00:00.000Z',
      createdAt: '2026-01-01T10:00:00.000Z',
    },
  ],
]);

describe('resolveHighlightHex', () => {
  it('resolves uppercase hex stored by the reader', () => {
    expect(resolveHighlightHex('#FACC15')).toBe('#facc15');
    expect(resolveHighlightHex('#4ADE80')).toBe('#4ade80');
  });

  it('resolves lowercase hex case-insensitively', () => {
    expect(resolveHighlightHex('#facc15')).toBe('#facc15');
    expect(resolveHighlightHex('#ef4444')).toBe('#ef4444');
  });

  it('resolves legacy hexes to their pinned canonical targets', () => {
    expect(resolveHighlightHex('#60A5FA')).toBe('#3b82f6');
    expect(resolveHighlightHex('#c084fc')).toBe('#3b82f6');
    expect(resolveHighlightHex('#FB923C')).toBe('#f97316');
    expect(resolveHighlightHex('#f472b6')).toBe('#ef4444');
  });

  it('resolves legacy key values', () => {
    expect(resolveHighlightHex('yellow')).toBe('#facc15');
    expect(resolveHighlightHex('red')).toBe('#ef4444');
  });

  it('falls back to canonical yellow for removed legacy keys', () => {
    // 'purple'/'pink' are no longer palette keys; they are unparseable as
    // hex, so the resolver falls back to DEFAULT_HIGHLIGHT_COLOR.
    expect(resolveHighlightHex('purple')).toBe('#facc15');
    expect(resolveHighlightHex('pink')).toBe('#facc15');
  });

  it('falls back to canonical yellow for unknown values', () => {
    expect(resolveHighlightHex('')).toBe('#facc15');
    expect(resolveHighlightHex('not-a-color')).toBe('#facc15');
  });

  it('resolves unknown but parseable hexes inside the canonical palette', () => {
    const resolved = resolveHighlightHex('#123456');
    expect(HIGHLIGHT_COLORS.map((c) => c.hex)).toContain(resolved);
  });

  it('covers every palette entry by hex and key', () => {
    for (const color of HIGHLIGHT_COLORS) {
      expect(resolveHighlightHex(color.hex.toUpperCase())).toBe(color.hex);
      expect(resolveHighlightHex(color.key)).toBe(color.hex);
    }
  });
});

describe('filterHighlights color filter', () => {
  it('matches stored hex when a palette key chip is selected', () => {
    const highlights = [
      makeHighlight({ id: 'yellow-hl', color: '#FACC15' }),
      makeHighlight({ id: 'green-hl', color: '#4ADE80' }),
    ];

    const result = filterHighlights(highlights, '', 'yellow', '', '', books);

    expect(result.map((h) => h.id)).toEqual(['yellow-hl']);
  });

  it('matches legacy stored hexes to their pinned canonical chip', () => {
    const highlights = [
      makeHighlight({ id: 'legacy-blue', color: '#60A5FA' }),
      makeHighlight({ id: 'legacy-purple', color: '#c084fc' }),
      makeHighlight({ id: 'canonical-red', color: '#EF4444' }),
    ];

    const result = filterHighlights(highlights, '', 'blue', '', '', books);

    expect(result.map((h) => h.id)).toEqual(['legacy-blue', 'legacy-purple']);
  });

  it('does not match a hex stored under a different palette key', () => {
    const highlights = [makeHighlight({ id: 'yellow-hl', color: '#FACC15' })];

    const result = filterHighlights(highlights, '', 'green', '', '', books);

    expect(result).toEqual([]);
  });

  it('keeps colors named by their own key in the type domain', () => {
    const key: HighlightColorKey = 'orange';
    expect(HIGHLIGHT_COLORS.some((c) => c.key === key)).toBe(true);
  });
});
