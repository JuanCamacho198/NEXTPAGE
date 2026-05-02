import type { ReaderThemeMode } from "$lib/shared/types";
import { DEFAULT_PDF_SCALE } from "$lib/features/reader/pdf/pdfNavigation";

export const scaleOptions = Array.from({ length: 26 }, (_, index) => (50 + index * 10) / 100);

export const TOOLBAR_OFFSET = 18;
export const TOOLBAR_WIDTH_ESTIMATE = 320;
export const TOOLBAR_EDGE_PADDING = 16;
export const VERTICAL_SCROLL_STEP_PX = 120;
export const ZOOM_COMMIT_DELAY_MS = 120;
export const ZOOM_EPSILON = 0.001;
export const SELECTION_X_PADDING_PX = 3;
export const SELECTION_Y_INSET_PX = 1;
export const SELECTION_LINE_TOLERANCE_PX = 4;

export type ReaderThemePalette = {
  rootBackground: string;
  surfaceBackground: string;
  textColor: string;
};

export type SelectionOverlayRect = {
  left: number;
  top: number;
  width: number;
  height: number;
};

// Pure helper functions (these work in .svelte.ts)
export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)));
}

export function clampSelectionPoint(value: number, min: number, max: number): number {
  if (max < min) {
    return min;
  }
  return Math.min(max, Math.max(min, value));
}

export function clampPdfScale(value: number): number {
  return Math.min(3, Math.max(0.5, value));
}

export function resolveThemePalette(themeMode: ReaderThemeMode): ReaderThemePalette {
  if (themeMode === "sepia") {
    return {
      rootBackground: "#efe2cc",
      surfaceBackground: "#f6ebd8",
      textColor: "#2f2416",
    };
  }
  if (themeMode === "night") {
    return {
      rootBackground: "#0f1320",
      surfaceBackground: "#161c2d",
      textColor: "#e8ecf7",
    };
  }
  return {
    rootBackground: "#f4efe1",
    surfaceBackground: "#fbf7ed",
    textColor: "#221a12",
  };
}

export function calculateScale(delta: number, currentScale: number): number {
  const newScale = currentScale + delta * 0.001;
  return clampPdfScale(newScale);
}

export function formatPageNumber(page: number, total: number): string {
  return `${page} / ${total}`;
}

// NOTE on state extraction:
// Svelte 5 $state runes CANNOT be used in regular .svelte.ts files - they are
// compiler macros that only work in .svelte component files.
//
// The following CAN be extracted to .svelte.ts:
// - Pure functions (done above)
// - Types/interfaces
// - Constants
//
// The following MUST remain in .svelte files:
// - $state variables
// - $derived values
// - $effect blocks
// - Event handlers
// - UI markup
//
// This is a Svelte 5 architectural limitation, not a bug in the implementation.
