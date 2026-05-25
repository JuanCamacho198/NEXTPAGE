import type { ReaderThemeMode } from '$lib/shared/types';

export const FONT_SIZE_MIN = 50;
export const FONT_SIZE_MAX = 200;

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)));
}

export interface EpubThemeStyles {
  background: string;
  color: string;
}

export function resolveThemeStyles(themeMode: ReaderThemeMode): EpubThemeStyles {
  if (themeMode === 'sepia') {
    return { background: '#f1e7d4', color: '#3a2f1d' };
  }

  if (themeMode === 'night') {
    return { background: '#10141f', color: '#e7ebf1' };
  }

  return { background: '#faf6eb', color: '#2b2116' };
}

type DisplaySettings = {
  fontSize: number;
  fontFamily: string;
  theme: ReaderThemeMode;
};

type RenditionLike = {
  themes: {
    fontSize: (val: string) => void;
    font: (val: string) => void;
    default: (styles: Record<string, Record<string, string>>) => void;
  };
};

export function applyDisplaySettings(
  rendition: RenditionLike,
  displaySettings: DisplaySettings,
): void {
  rendition.themes.fontSize(`${displaySettings.fontSize}%`);
  rendition.themes.font(displaySettings.fontFamily);

  const themeStyles = resolveThemeStyles(displaySettings.theme);
  rendition.themes.default({
    body: {
      'font-size': `${displaySettings.fontSize}%`,
      'font-family': displaySettings.fontFamily,
      'background-color': themeStyles.background,
      color: themeStyles.color,
    },
    p: { color: themeStyles.color },
    h1: { color: themeStyles.color },
    h2: { color: themeStyles.color },
    h3: { color: themeStyles.color },
    h4: { color: themeStyles.color },
    h5: { color: themeStyles.color },
    h6: { color: themeStyles.color },
    a: { color: themeStyles.color },
  });
}
