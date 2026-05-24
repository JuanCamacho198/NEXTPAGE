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
};

class SettingsStateManager {
  locale = $state<UiLocale>("es");
  readerSettings = $state<ReaderSettings>(DEFAULT_READER_SETTINGS);
}

export const settingsState = new SettingsStateManager();
