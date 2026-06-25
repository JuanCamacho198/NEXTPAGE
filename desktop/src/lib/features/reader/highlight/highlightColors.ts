/**
 * Canonical highlight color palette for both PDF and EPUB highlights.
 *
 * This is the single source of truth for the colors the user can pick when
 * creating or recoloring a highlight. The PDF and EPUB highlight flows both
 * import from this module; the original `pdfSelection.ts` constant has been
 * converted to a re-export for backwards compatibility (see
 * `src/lib/features/reader/pdf/pdfSelection.ts`).
 *
 * Order is meaningful: `DEFAULT_HIGHLIGHT_COLOR` is the first entry.
 * Hex values are the canonical CSS color strings (7 chars, leading `#`).
 */
export type HighlightColorId = 'yellow' | 'green' | 'blue' | 'purple' | 'orange';

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
  { hex: '#60A5FA', label: 'blue', i18nKey: 'highlight.color.blue' },
  { hex: '#C084FC', label: 'purple', i18nKey: 'highlight.color.purple' },
  { hex: '#FB923C', label: 'orange', i18nKey: 'highlight.color.orange' },
] as const;

export const DEFAULT_HIGHLIGHT_COLOR: HighlightColor = HIGHLIGHT_COLORS[0];

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
