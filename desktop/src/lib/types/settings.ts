export type AppSettingDto = {
  key: string;
  valueJson: string;
  updatedAt: string;
};

export const SUPPORTED_UI_LOCALES = ["es", "en"] as const;

export type UiLocale = (typeof SUPPORTED_UI_LOCALES)[number];

export const UI_LOCALE_SETTING_KEY = "ui.locale" as const;

export const READER_THEME_MODE_SETTING_KEY = "reader.themeMode" as const;
export const READER_BRIGHTNESS_SETTING_KEY = "reader.brightness" as const;
export const READER_CONTRAST_SETTING_KEY = "reader.contrast" as const;
export const READER_EPUB_FONT_SIZE_SETTING_KEY = "reader.epub.fontSize" as const;
export const READER_EPUB_FONT_FAMILY_SETTING_KEY = "reader.epub.fontFamily" as const;

export type ReaderThemeMode = "paper" | "sepia" | "night";

export type ReaderSettings = {
  themeMode: ReaderThemeMode;
  brightness: number;
  contrast: number;
  epub: {
    fontSize: number;
    fontFamily: string;
  };
};

export type TranslationKey = string;

export interface SentrySettings {
  dsn: string;
  tracesSampleRate: number;
  enabled: boolean;
}