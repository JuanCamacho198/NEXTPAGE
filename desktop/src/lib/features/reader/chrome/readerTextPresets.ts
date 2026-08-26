import type { ReaderSettings, ReaderTextAlign, ReaderThemeMode } from '$lib/shared/types';

export const LINE_HEIGHT_PRESETS: readonly number[] = [1.4, 1.6, 1.8, 2.0, 2.2, 2.4] as const;

export const PARAGRAPH_SPACING_PRESETS: readonly number[] = [0, 0.5, 1, 1.5, 2, 3] as const;

export const MARGIN_PRESETS: readonly ReaderSettings['margins'][] = [
  { top: 0.5, bottom: 0.5, left: 0.75, right: 0.75 },
  { top: 1, bottom: 1, left: 1.5, right: 1.5 },
  { top: 1.5, bottom: 1.5, left: 2, right: 2 },
  { top: 2, bottom: 2, left: 2.5, right: 2.5 },
  { top: 2.5, bottom: 2.5, left: 3, right: 3 },
] as const;

export const ALIGN_CYCLE: readonly ReaderTextAlign[] = [
  'left',
  'center',
  'right',
  'justify',
] as const;

export const THEME_PRESETS: readonly { name: ReaderThemeMode; bg: string }[] = [
  { name: 'paper', bg: '#ffffff' },
  { name: 'sepia', bg: '#f4ecd8' },
  { name: 'night', bg: '#000000' },
  { name: 'dark', bg: '#444444' },
  { name: 'blue', bg: '#5b7fa3' },
] as const;

export const FONT_FAMILY_PRESETS: readonly string[] = [
  'serif',
  'sans-serif',
  'monospace',
  'Georgia',
  'Palatino',
] as const;

export function cyclePreset<T>(current: T, presets: readonly T[]): T {
  const idx = presets.indexOf(current);
  if (idx >= 0 && idx < presets.length - 1) return presets[idx + 1] as T;
  return presets[0] as T;
}

export function cyclePresetBy<T>(
  current: T,
  presets: readonly T[],
  equal: (a: T, b: T) => boolean,
): T {
  const idx = presets.findIndex((p) => equal(p, current));
  if (idx >= 0 && idx < presets.length - 1) return presets[idx + 1] as T;
  return presets[0] as T;
}
