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
export const READER_SELECTION_COLOR_SETTING_KEY = "reader.selectionColor" as const;
export const READER_EPUB_FONT_SIZE_SETTING_KEY = "reader.epub.fontSize" as const;
export const READER_EPUB_FONT_FAMILY_SETTING_KEY = "reader.epub.fontFamily" as const;

// New layout setting keys
export const READER_LINE_HEIGHT_SETTING_KEY = "reader.lineHeight" as const;
export const READER_LETTER_SPACING_SETTING_KEY = "reader.letterSpacing" as const;
export const READER_PARAGRAPH_SPACING_SETTING_KEY = "reader.paragraphSpacing" as const;
export const READER_TEXT_ALIGN_SETTING_KEY = "reader.textAlign" as const;
export const READER_DIRECTION_SETTING_KEY = "reader.direction" as const;
export const READER_HYPHENATION_SETTING_KEY = "reader.hyphenation" as const;
export const READER_VERTICAL_SCROLLING_SETTING_KEY = "reader.verticalScrolling" as const;
export const READER_MARGINS_SETTING_KEY = "reader.margins" as const;
export const READER_SHOW_HEADER_SETTING_KEY = "reader.showHeader" as const;
export const READER_SHOW_FOOTER_SETTING_KEY = "reader.showFooter" as const;
export const READER_SHOW_PAGE_NUMBERS_SETTING_KEY = "reader.showPageNumbers" as const;
export const READER_PROGRESS_INDICATOR_SETTING_KEY = "reader.progressIndicator" as const;

export type ReaderThemeMode = "paper" | "sepia" | "night" | "dark" | "blue";

export type ReaderTextAlign = "left" | "center" | "right" | "justify";
export type ReaderDirection = "ltr" | "rtl";
export type ReaderProgressIndicator = "percentage" | "chapter" | "time";

export type ReaderSettings = {
  // Existing fields
  themeMode: ReaderThemeMode;
  brightness: number;
  contrast: number;
  selectionColor: string;
  epub: {
    fontSize: number;
    fontFamily: string;
  };

  // New layout fields (merged from ReaderLayoutSettings)
  lineHeight: number;          // 1.0–3.0, step 0.1, default 1.8
  letterSpacing: number;       // -2 to 10 px, default 0
  paragraphSpacing: number;    // 0 to 4 em, default 1
  textAlign: ReaderTextAlign;
  direction: ReaderDirection;
  hyphenation: boolean;
  verticalScrolling: boolean;
  margins: {
    top: number;    // 0.5–4 rem
    bottom: number;
    left: number;
    right: number;
  };
  showHeader: boolean;
  showFooter: boolean;
  showPageNumbers: boolean;
  progressIndicator: ReaderProgressIndicator;
};

export type TranslationKey = string;

export interface SentrySettings {
  dsn: string;
  tracesSampleRate: number;
  enabled: boolean;
}

// Reader Layout Settings (margins, spacing, header/footer)
// Deprecated: These fields are now merged directly into ReaderSettings.
// Kept for backward compat with AppConfigExport.
export interface ReaderLayoutSettings {
  margins: {
    top: number;
    bottom: number;
    left: number;
    right: number;
  };
  lineHeight: number;
  paragraphSpacing: number;
  showHeader: boolean;
  showFooter: boolean;
  showPageNumbers: boolean;
  progressIndicator: "percentage" | "chapter" | "time";
}

// Notification Settings
export interface NotificationSettings {
  readingReminders: {
    enabled: boolean;
    frequency: "daily" | "weekly";
    time?: string;
  };
  progressAlerts: {
    enabled: boolean;
    milestones: number[];
  };
}

// Sync Settings
export interface SyncSettings {
  autoSync: boolean;
  frequency: "manual" | "hourly" | "daily";
  conflictResolution: "local" | "remote" | "ask";
  lastSyncTime?: string;
}

// Storage Information
export interface StorageInfo {
  cacheSize: number;
  downloadedBooks: number;
  tempFiles: number;
}

// Export/Import Config
export interface AppConfigExport {
  version: string;
  exportedAt: string;
  locale: UiLocale;
  theme: string;
  fontScale: number;
  readerSettings: ReaderSettings;
  readerLayoutSettings: ReaderLayoutSettings;
  notificationSettings: NotificationSettings;
  syncSettings: SyncSettings;
}