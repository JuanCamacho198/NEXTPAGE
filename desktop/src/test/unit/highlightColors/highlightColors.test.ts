import { describe, expect, it } from 'vitest';
import {
  DEFAULT_HIGHLIGHT_COLOR,
  HIGHLIGHT_COLORS,
  nearestHighlightHex,
} from '$lib/features/reader/highlight/highlightColors';

// The canonical palette must mirror Android's Highlight.fromHex truth
// exactly (spec HPU-1): five entries, this order, these hexes.
const CANONICAL_HEXES = ['#FACC15', '#4ADE80', '#3B82F6', '#F97316', '#EF4444'];
const CANONICAL_IDS = ['yellow', 'green', 'blue', 'orange', 'red'] as const;

function rgbOf(hex: string): { r: number; g: number; b: number } {
  const raw = hex.startsWith('#') ? hex.slice(1) : hex;
  return {
    r: Number.parseInt(raw.slice(0, 2), 16),
    g: Number.parseInt(raw.slice(2, 4), 16),
    b: Number.parseInt(raw.slice(4, 6), 16),
  };
}

/** Squared Euclidean RGB distance from `hex` to every palette entry, in order. */
function squaredDistances(hex: string): Array<{ id: string; distance: number }> {
  const source = rgbOf(hex);
  return HIGHLIGHT_COLORS.map((color) => {
    const target = rgbOf(color.hex);
    return {
      id: color.label,
      distance:
        (source.r - target.r) ** 2 + (source.g - target.g) ** 2 + (source.b - target.b) ** 2,
    };
  });
}

describe('HIGHLIGHT_COLORS canonical palette (HPU-1)', () => {
  it('offers exactly the five Android-canonical hexes in stable order', () => {
    expect(HIGHLIGHT_COLORS.map((c) => c.hex)).toEqual(CANONICAL_HEXES);
  });

  it('uses the canonical color ids in the same order', () => {
    expect(HIGHLIGHT_COLORS.map((c) => c.label)).toEqual([...CANONICAL_IDS]);
  });

  it('defaults to canonical yellow as the first entry', () => {
    expect(DEFAULT_HIGHLIGHT_COLOR).toBe(HIGHLIGHT_COLORS[0]);
    expect(DEFAULT_HIGHLIGHT_COLOR.hex).toBe('#FACC15');
  });

  it('exposes an i18n key per entry with no purple/pink leftovers', () => {
    expect(HIGHLIGHT_COLORS.map((c) => c.i18nKey)).toEqual([
      'highlight.color.yellow',
      'highlight.color.green',
      'highlight.color.blue',
      'highlight.color.orange',
      'highlight.color.red',
    ]);
  });
});

describe('nearestHighlightHex pinned legacy mappings (HPU-2)', () => {
  // Test-locked targets from the spec: legacy stored hexes resolve to
  // exactly these canonical colors (each verified to have a unique
  // nearest entry — see no-tie guard below).
  it.each([
    ['#60A5FA', '#3B82F6'], // legacy light blue -> canonical blue
    ['#C084FC', '#3B82F6'], // legacy purple -> canonical blue
    ['#FB923C', '#F97316'], // legacy light orange -> canonical orange
    ['#f472b6', '#EF4444'], // legacy pink -> canonical red (case-insensitive)
  ])('maps legacy %s to pinned %s', (legacy, expected) => {
    expect(nearestHighlightHex(legacy)).toBe(expected);
  });

  it('resolves every canonical hex to itself regardless of input case', () => {
    for (const color of HIGHLIGHT_COLORS) {
      expect(nearestHighlightHex(color.hex)).toBe(color.hex);
      expect(nearestHighlightHex(color.hex.toLowerCase())).toBe(color.hex);
    }
  });
});

describe('nearestHighlightHex resolver contract', () => {
  it('never ties: the minimum squared distance is unique for palette and legacy inputs', () => {
    // NOTE: #95639D is deliberately absent here — it is an exact blue/red
    // tie covered by the equidistance test below.
    const inputs = [...CANONICAL_HEXES, '#60A5FA', '#C084FC', '#FB923C', '#f472b6'];
    for (const hex of inputs) {
      const distances = squaredDistances(hex);
      const min = Math.min(...distances.map((d) => d.distance));
      const winners = distances.filter((d) => d.distance === min);
      expect(winners, `expected a unique nearest color for ${hex}`).toHaveLength(1);
    }
  });

  it('breaks exact equidistance toward the first palette entry (yellow)', () => {
    // #95639D is exactly equidistant (d² = 16982) between blue and red;
    // the strict '<' scan resolves to blue because blue precedes red.
    const distances = squaredDistances('#95639D');
    const blue = distances.find((d) => d.id === 'blue');
    const red = distances.find((d) => d.id === 'red');
    expect(blue?.distance).toBe(red?.distance);
    expect(blue?.distance).toBeLessThan(distances.find((d) => d.id === 'yellow')!.distance);
    expect(nearestHighlightHex('#95639D')).toBe('#3B82F6');
  });

  it('falls back to DEFAULT_HIGHLIGHT_COLOR for unparseable input', () => {
    expect(nearestHighlightHex('')).toBe('#FACC15');
    expect(nearestHighlightHex('not-a-color')).toBe('#FACC15');
    expect(nearestHighlightHex('#12345')).toBe('#FACC15');
  });
});
