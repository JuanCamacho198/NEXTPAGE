import { describe, expect, it } from 'vitest';
import { clampPdfScale } from '$lib/features/reader/viewer-pdf/pdfNavigation';
import { clampPdfScale as clampRaw } from '$lib/features/reader/viewer-pdf/pdfState.svelte';
import { flattenOutline } from '$lib/features/reader/viewer-pdf/pdfSelection';
import { flattenOutline as outlineFlatten } from '$lib/features/reader/viewer-pdf/usePdfOutline.svelte';
import { clampPdfScale as zoomClamp } from '$lib/features/reader/viewer-pdf/usePdfZoomTheme.svelte';

describe('clampPdfScale (pdfNavigation rounded 0.5-3)', () => {
  it('clamps within bounds with rounding', () => {
    expect(clampPdfScale(1.0)).toBe(1.0);
    expect(clampPdfScale(0.3)).toBe(0.5);
    expect(clampPdfScale(3.5)).toBe(3.0);
    expect(clampPdfScale(1.23)).toBe(1.2);
    expect(clampPdfScale(1.27)).toBe(1.3);
  });
});

describe('clampPdfScale (pdfState raw 0.5-3 no rounding)', () => {
  it('clamps without rounding', () => {
    expect(clampRaw(0.3)).toBe(0.5);
    expect(clampRaw(3.5)).toBe(3.0);
    expect(clampRaw(1.23)).toBe(1.23);
    expect(clampRaw(1.27)).toBe(1.27);
  });
});

describe('clampPdfScale (usePdfZoomTheme re-export)', () => {
  it('matches pdfNavigation clamp', () => {
    expect(zoomClamp(0.2)).toBe(0.5);
    expect(zoomClamp(3.9)).toBe(3.0);
    expect(zoomClamp(1.55)).toBe(1.6);
  });
});

describe('flattenOutline', () => {
  it('flattens nested outline with depth', () => {
    const outline = [
      { id: 'a', title: 'A', dest: null, items: [{ id: 'a-0', title: 'A1', dest: null, items: [] }] },
      { id: 'b', title: 'B', dest: null, items: [] },
    ];
    const flat = flattenOutline(outline as unknown as Parameters<typeof flattenOutline>[0]);
    expect(flat).toHaveLength(3);
    expect((flat[0].item as unknown as { id: string }).id).toBe('a');
    expect(flat[0].depth).toBe(0);
    expect((flat[1].item as unknown as { id: string }).id).toBe('a-0');
    expect(flat[1].depth).toBe(1);
    expect((flat[2].item as unknown as { id: string }).id).toBe('b');
    expect(flat[2].depth).toBe(0);
  });

  it('handles deeply nested outline', () => {
    const outline = [
      {
        id: 'root',
        title: 'Root',
        dest: null,
        items: [
          {
            id: 'child',
            title: 'Child',
            dest: null,
            items: [{ id: 'leaf', title: 'Leaf', dest: null, items: [] }],
          },
        ],
      },
    ];
    const flat = flattenOutline(outline as unknown as Parameters<typeof flattenOutline>[0]);
    expect(flat.map((e) => e.depth)).toEqual([0, 1, 2]);
  });

  it('returns empty for empty outline', () => {
    expect(flattenOutline([])).toEqual([]);
  });

  it('usePdfOutline re-export matches pdfSelection', () => {
    const outline = [{ id: 'x', title: 'X', dest: null, items: [] }];
    const a = flattenOutline(outline as unknown as Parameters<typeof flattenOutline>[0]);
    const b = outlineFlatten(outline as unknown as Parameters<typeof outlineFlatten>[0]);
    expect(a).toEqual(b);
  });
});
