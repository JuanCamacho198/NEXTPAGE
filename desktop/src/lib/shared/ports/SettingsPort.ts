import type { AppSettingDto, ReaderSettings, UiLocale } from '$lib/shared/types';

export interface SettingsPort {
  getReaderSettings(): Promise<ReaderSettings>;
  upsertReaderSettings(settings: Partial<ReaderSettings>): Promise<ReaderSettings>;
  resetReaderSettings(): Promise<ReaderSettings>;
  getLocale(): Promise<string | null>;
  upsertLocale(locale: UiLocale): Promise<void>;
  getDailyGoal(userId?: string): Promise<number>;
  saveDailyGoal(minutes: number, userId?: string): Promise<void>;
  getTodayMinutes(userId: string, bookId?: string): Promise<number>;
  getAppSettings(): Promise<AppSettingDto[]>;
  upsertAppSettings(settings: AppSettingDto[]): Promise<void>;
}
