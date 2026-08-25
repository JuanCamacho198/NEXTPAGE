import {
  getDailyGoal,
  getDefaultReaderSettings,
  getReaderSettings,
  saveDailyGoal,
} from '$lib/shared/api/tauriClient';
import type { ReaderSettings, UiLocale } from '$lib/shared/types';
import { DEFAULT_DAILY_GOAL } from '$lib/shared/types/settings';

class SettingsDomainState {
  // ─── State ───
  locale = $state<UiLocale>('es');
  readerSettings = $state<ReaderSettings>(getDefaultReaderSettings());
  dailyGoalMinutes = $state<number>(DEFAULT_DAILY_GOAL as number);

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

  async loadDailyGoalMinutes(userId?: string): Promise<void> {
    try {
      const minutes = await getDailyGoal(userId);
      this.dailyGoalMinutes = minutes;
    } catch {
      this.dailyGoalMinutes = DEFAULT_DAILY_GOAL as number;
    }
  }

  async saveDailyGoalMinutes(minutes: number, userId?: string): Promise<void> {
    if (!userId || userId.trim().length === 0) return;
    await saveDailyGoal(minutes, userId);
    // reflect sanitized value (normalize 60→45 etc.)
    try {
      const refreshed = await getDailyGoal(userId);
      this.dailyGoalMinutes = refreshed;
    } catch {
      // keep optimistic sanitized via local guard if refresh fails
      const { sanitizeDailyGoal } = await import('$lib/shared/api/tauriClient');
      this.dailyGoalMinutes = sanitizeDailyGoal(minutes) as number;
    }
  }

  clearDailyGoal(): void {
    this.dailyGoalMinutes = DEFAULT_DAILY_GOAL as number;
  }
}

export const settingsState = new SettingsDomainState();
export { SettingsDomainState };
