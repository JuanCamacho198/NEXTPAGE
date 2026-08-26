import { i18n } from '$lib/shared/i18n';
import type { AppSettingDto, CommandErrorDto, UiLocale } from '$lib/shared/types';
import type { SettingsPort } from '$lib/shared/ports/SettingsPort';
import { TauriSettingsAdapter } from '$lib/shared/ports/adapters/tauri/TauriSettingsAdapter';

export type AppearanceDeps = {
  settingsPort?: SettingsPort;
  getSettings?: () => Promise<AppSettingDto[]>;
  upsertSettings?: (settings: AppSettingDto[]) => Promise<void>;
  getLocaleSetting?: () => Promise<string | null>;
  setLocale?: (locale: UiLocale) => Promise<void>;
  toSupportedLocale?: (value: string | null) => UiLocale | null;
  onLocaleChange?: (locale: UiLocale) => void;
};

const SETTINGS_KEY = {
  THEME: 'ui.theme' as const,
  FONT_SCALE: 'reader.fontScale' as const,
};

const clampInteger = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, Math.round(value)));

const parseSettingValue = (settings: AppSettingDto[], key: string): unknown => {
  const item = settings.find((e) => e.key === key);
  if (!item) return null;
  try {
    return JSON.parse(item.valueJson) as unknown;
  } catch {
    return null;
  }
};

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

const mapCommandErrorMessage = (error: unknown): { message: string; recoverable: boolean } => {
  const err = error as MaybeCommandError;
  const fallback = error instanceof Error ? error.message : 'Settings command failed.';
  if (err.commandError) return { message: err.commandError.message, recoverable: err.commandError.recoverable };
  return { message: fallback, recoverable: false };
};

export function createSettingsAppearance(deps: AppearanceDeps = {}): {
  preferredTheme: string;
  preferredFontScale: number;
  locale: UiLocale;
  settingsError: string | null;
  settingsUnavailable: string | null;
  isSavingSettings: boolean;
  isDirty: boolean;
  isSaving: boolean;
  handlePreferredThemeChange: (value: string) => void;
  handlePreferredFontScaleChange: (value: number) => void;
  handleLocaleSelect: (value: string) => Promise<void>;
  loadAppearance: () => Promise<void>;
  saveAppearance: () => Promise<void>;
  resetToDefaults: () => void;
} {
  const settingsPort: SettingsPort = deps.settingsPort ?? new TauriSettingsAdapter();
  const getSettingsFn = deps.getSettings ?? (() => settingsPort.getAppSettings());
  const upsertSettingsFn = deps.upsertSettings ?? ((s: AppSettingDto[]) => settingsPort.upsertAppSettings(s));
  const getLocaleSettingFn = deps.getLocaleSetting ?? (() => settingsPort.getLocale());
  const setLocaleFn = deps.setLocale ?? ((locale: UiLocale): Promise<void> => i18n.setLocale(locale));
  const toSupportedLocaleFn = deps.toSupportedLocale ?? ((v: string | null): UiLocale | null => i18n.toSupportedLocale(v));

  let preferredTheme = $state('light');
  let preferredFontScale = $state(100);
  let locale = $state<UiLocale>((i18n.FALLBACK_LOCALE ?? 'en') as UiLocale);

  let settingsError = $state<string | null>(null);
  let settingsUnavailable = $state<string | null>(null);
  let isSavingSettings = $state(false);

  let initialTheme = $state('light');
  let initialFontScale = $state(100);
  // svelte-ignore state_referenced_locally
  let initialLocale = $state<UiLocale>(locale);

  const isDirty = $derived(
    preferredTheme !== initialTheme ||
      preferredFontScale !== initialFontScale ||
      locale !== initialLocale,
  );
  const isSaving = $derived(isSavingSettings);

  function handlePreferredThemeChange(value: string): void {
    preferredTheme = value;
  }

  function handlePreferredFontScaleChange(value: number): void {
    preferredFontScale = clampInteger(value, 80, 140);
  }

  async function handleLocaleSelect(value: string): Promise<void> {
    const safeLocale = toSupportedLocaleFn(value) ?? (i18n.FALLBACK_LOCALE as UiLocale);
    locale = safeLocale;
    deps.onLocaleChange?.(safeLocale);
    settingsError = null;
    settingsUnavailable = null;
    try {
      await setLocaleFn(safeLocale);
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) settingsUnavailable = details.message;
      else settingsError = details.message;
    }
  }

  async function loadAppearance(): Promise<void> {
    settingsError = null;
    settingsUnavailable = null;
    try {
      const response = await getSettingsFn();
      const nextTheme = parseSettingValue(response, SETTINGS_KEY.THEME);
      const nextFontScale = parseSettingValue(response, SETTINGS_KEY.FONT_SCALE);

      if (typeof nextTheme === 'string' && nextTheme.length > 0) {
        preferredTheme = nextTheme;
      }
      if (typeof nextFontScale === 'number' && Number.isFinite(nextFontScale)) {
        preferredFontScale = Math.max(80, Math.min(140, Math.round(nextFontScale)));
      }

      const persistedLocale = toSupportedLocaleFn(await getLocaleSettingFn());
      if (persistedLocale) {
        locale = persistedLocale;
        deps.onLocaleChange?.(persistedLocale);
      }

      initialTheme = preferredTheme;
      initialFontScale = preferredFontScale;
      initialLocale = locale;
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) settingsUnavailable = details.message;
      else settingsError = details.message;
    }
  }

  async function saveAppearance(): Promise<void> {
    isSavingSettings = true;
    settingsError = null;
    settingsUnavailable = null;
    try {
      await upsertSettingsFn([
        { key: SETTINGS_KEY.THEME, valueJson: JSON.stringify(preferredTheme), updatedAt: new Date().toISOString() },
        { key: SETTINGS_KEY.FONT_SCALE, valueJson: JSON.stringify(preferredFontScale), updatedAt: new Date().toISOString() },
      ]);
      initialTheme = preferredTheme;
      initialFontScale = preferredFontScale;
      initialLocale = locale;
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) settingsUnavailable = details.message;
      else settingsError = details.message;
    } finally {
      isSavingSettings = false;
    }
  }

  function resetToDefaults(): void {
    preferredTheme = 'light';
    preferredFontScale = 100;
  }

  return {
    get preferredTheme() { return preferredTheme; },
    set preferredTheme(v: string) { preferredTheme = v; },
    get preferredFontScale() { return preferredFontScale; },
    set preferredFontScale(v: number) { preferredFontScale = clampInteger(v, 80, 140); },
    get locale() { return locale; },
    set locale(v: UiLocale) { locale = v; },
    get settingsError() { return settingsError; },
    set settingsError(v: string | null) { settingsError = v; },
    get settingsUnavailable() { return settingsUnavailable; },
    set settingsUnavailable(v: string | null) { settingsUnavailable = v; },
    get isSavingSettings() { return isSavingSettings; },
    set isSavingSettings(v: boolean) { isSavingSettings = v; },
    get isDirty() { return isDirty; },
    get isSaving() { return isSaving; },
    handlePreferredThemeChange,
    handlePreferredFontScaleChange,
    handleLocaleSelect,
    loadAppearance,
    saveAppearance,
    resetToDefaults,
  };
}
