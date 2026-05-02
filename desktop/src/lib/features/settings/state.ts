import { writable, get } from "svelte/store";
import type { ReaderSettings, UiLocale } from "$lib/shared/types";
import { getReaderSettings, getDefaultReaderSettings } from "$lib/shared/api/tauriClient";

export const locale = writable<UiLocale>("es");
export const readerSettings = writable<ReaderSettings>(getDefaultReaderSettings());

export const SettingsState = {
  async loadSettings() {
    try {
      readerSettings.set(await getReaderSettings());
    } catch {
      readerSettings.set(getDefaultReaderSettings());
    }
  },

  setLocale(newLocale: UiLocale) {
    locale.set(newLocale);
  },

  setReaderSettings(settings: ReaderSettings) {
    readerSettings.set(settings);
  }
};