import type { SettingsPort } from '$lib/shared/ports/SettingsPort';
import type { ReaderSettings, UiLocale } from '$lib/shared/types';
import * as tauriClient from '$lib/shared/api/tauriClient';

export class TauriSettingsAdapter implements SettingsPort {
  getReaderSettings(): Promise<ReaderSettings> {
    return tauriClient.getReaderSettings();
  }

  upsertReaderSettings(settings: Partial<ReaderSettings>): Promise<ReaderSettings> {
    return tauriClient.upsertReaderSettings(settings);
  }

  resetReaderSettings(): Promise<ReaderSettings> {
    return tauriClient.resetReaderSettingsToDefaults();
  }

  getLocale(): Promise<string | null> {
    return tauriClient.getLocaleSetting();
  }

  upsertLocale(locale: UiLocale): Promise<void> {
    return tauriClient.upsertLocaleSetting(locale);
  }

  getDailyGoal(userId?: string): Promise<number> {
    return tauriClient.getDailyGoal(userId);
  }

  saveDailyGoal(minutes: number, userId?: string): Promise<void> {
    return tauriClient.saveDailyGoal(minutes, userId);
  }

  getTodayMinutes(userId: string, bookId?: string): Promise<number> {
    return tauriClient.getTodayMinutes(userId, bookId);
  }
}
