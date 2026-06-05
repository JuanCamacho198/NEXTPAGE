import {
  getDefaultReaderSettings,
  getReaderSettings,
} from "$lib/shared/api/tauriClient";
import type { ReaderSettings, UiLocale } from "$lib/shared/types";

class SettingsDomainState {
  // ─── State ───
  locale = $state<UiLocale>("es");
  readerSettings = $state<ReaderSettings>(getDefaultReaderSettings());

  // ─── Methods ───

  async loadReaderSettings(): Promise<void> {
    try {
      this.readerSettings = await getReaderSettings();
    } catch {
      this.readerSettings = getDefaultReaderSettings();
    }
  }

  handleReaderSettingsChange(nextSettings: ReaderSettings): void {
    this.readerSettings = nextSettings;
  }

  handleLocaleChange(nextLocale: UiLocale): void {
    this.locale = nextLocale;
  }
}

export const settingsState = new SettingsDomainState();
export { SettingsDomainState };
