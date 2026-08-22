/**
 * Canonical highlight color palette for both PDF and EPUB highlights.
 *
 * This is the single source of truth for the colors the user can pick when
 * creating or recoloring a highlight. The five hexes mirror Android's
 * `Highlight.fromHex` truth exactly (spec HPU-1), so highlights sync'd from
 * the Android client render with identical colors on desktop. The PDF and
 * EPUB highlight flows both import from this module; the original
 * `pdfSelection.ts` constant has been converted to a re-export for backwards
 * compatibility (see `src/lib/features/reader/viewer-pdf/pdfSelection.ts`).
 *
 * Order is meaningful: `DEFAULT_HIGHLIGHT_COLOR` is the first entry and the
 * strict `<` scan in `nearestHighlightHex` breaks exact equidistance toward
 * whichever entry comes first (yellow). Hex values are the canonical CSS
 * color strings (7 chars, leading `#`, uppercase).
 */
export type HighlightColorId = 'yellow' | 'green' | 'blue' | 'orange' | 'red';

export interface HighlightColor {
  /** Canonical hex value (CSS). Always 7 chars including leading `#`. */
  hex: string;
  /** Short id used as i18n key suffix. */
  label: HighlightColorId;
  /** i18n key, e.g. `highlight.color.yellow`. */
  i18nKey: `highlight.color.${HighlightColorId}`;
}

export const HIGHLIGHT_COLORS: readonly HighlightColor[] = [
  { hex: '#FACC15', label: 'yellow', i18nKey: 'highlight.color.yellow' },
  { hex: '#4ADE80', label: 'green', i18nKey: 'highlight.color.green' },
  { hex: '#3B82F6', label: 'blue', i18nKey: 'highlight.color.blue' },
  { hex: '#F97316', label: 'orange', i18nKey: 'highlight.color.orange' },
  { hex: '#EF4444', label: 'red', i18nKey: 'highlight.color.red' },
] as const;

export const DEFAULT_HIGHLIGHT_COLOR: HighlightColor = HIGHLIGHT_COLORS[0];

/** Lowercase RGB components of a 6-digit hex string, or null if unparseable. */
function parseHexRgb(hex: string): { r: number; g: number; b: number } | null {
  const raw = hex.startsWith('#') ? hex.slice(1) : hex;
  if (!/^[0-9a-fA-F]{6}$/.test(raw)) return null;
  return {
    r: Number.parseInt(raw.slice(0, 2), 16),
    g: Number.parseInt(raw.slice(2, 4), 16),
    b: Number.parseInt(raw.slice(4, 6), 16),
  };
}

/**
 * Resolve any stored highlight color to its canonical palette hex by
 * nearest Euclidean RGB distance (port of the EPUB iframe overlay's
 * `mapColor` / Android `Highlight.fromHex` semantics).
 *
 * - Exact matches (case-insensitive) return their canonical hex.
 * - Legacy stored hexes (e.g. `#60A5FA`, `#C084FC`) resolve to the pinned
 *   canonical targets locked by the unit tests (spec HPU-2).
 * - Unparseable input falls back to `DEFAULT_HIGHLIGHT_COLOR.hex`
 *   (canonical yellow), matching Android's fallback behavior.
 */
export function nearestHighlightHex(color: string): string {
  const rgb = parseHexRgb(color);
  if (!rgb) return DEFAULT_HIGHLIGHT_COLOR.hex;

  let best = HIGHLIGHT_COLORS[0];
  let bestDistance = Number.POSITIVE_INFINITY;
  for (const candidate of HIGHLIGHT_COLORS) {
    const target = parseHexRgb(candidate.hex);
    if (!target) continue;
    const distance = (rgb.r - target.r) ** 2 + (rgb.g - target.g) ** 2 + (rgb.b - target.b) ** 2;
    // Strict '<' keeps the FIRST entry on exact ties (yellow wins).
    if (distance < bestDistance) {
      bestDistance = distance;
      best = candidate;
    }
  }
  return best.hex;
}

/**
 * Convert a hex color (`#FACC15`) to a translucent fill suitable for a
 * highlight background. Kept here so PDF + EPUB stay in lockstep.
 */
export function highlightFillRgba(hex: string, alpha: number = 0.4): string {
  const cleanHex = hex.startsWith('#') ? hex.slice(1) : hex;
  if (cleanHex.length !== 6) return `rgba(250, 204, 21, ${alpha})`;
  const r = parseInt(cleanHex.slice(0, 2), 16);
  const g = parseInt(cleanHex.slice(2, 4), 16);
  const b = parseInt(cleanHex.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
