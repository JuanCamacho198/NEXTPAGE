// ─── Feature state: settings — migrated to $state runes ───
// Canonical state is AppState ($lib/stores/AppState.svelte).

import type { ReaderSettings, UiLocale } from "$lib/shared/types";

const DEFAULT_READER_SETTINGS: ReaderSettings = {
  themeMode: "paper",
  brightness: 100,
  contrast: 100,
  selectionColor: "#3b82f6",
  epub: {
    fontSize: 16,
    fontFamily: "serif",
  },
  lineHeight: 1.8,
  letterSpacing: 0,
  paragraphSpacing: 1,
  textAlign: "left",
  direction: "ltr",
  hyphenation: false,
  verticalScrolling: false,
  margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
  showHeader: true,
  showFooter: true,
  showPageNumbers: true,
  progressIndicator: "percentage",
};

class SettingsStateManager {
  locale = $state<UiLocale>("es");
  readerSettings = $state<ReaderSettings>(DEFAULT_READER_SETTINGS);
}

export const settingsState = new SettingsStateManager();
