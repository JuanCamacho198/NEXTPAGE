import type { ReaderSettings, ReaderThemeMode } from '$lib/shared/types';
import type { SettingsPort } from '$lib/shared/ports/SettingsPort';
import { TauriSettingsAdapter } from '$lib/shared/ports/adapters/tauri/TauriSettingsAdapter';

export type ReaderDeps = {
  settingsPort?: SettingsPort;
  getReaderSettings?: () => Promise<ReaderSettings>;
  upsertReaderSettings?: (settings: Partial<ReaderSettings>) => Promise<ReaderSettings>;
  onReaderSettingsChange?: (settings: ReaderSettings) => void;
};

const clampInteger = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, Math.round(value)));

const normalizeFontFamily = (value: string): string => {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : 'sans';
};

export function createSettingsReader(deps: ReaderDeps = {}): {
  readerThemeMode: ReaderThemeMode;
  readerBrightness: number;
  readerContrast: number;
  readerEpubFontSize: number;
  readerEpubFontFamily: string;
  isSavingSettings: boolean;
  settingsError: string | null;
  isDirty: boolean;
  isSaving: boolean;
  handleReaderThemeModeChange: (mode: ReaderThemeMode) => void;
  handleReaderBrightnessChange: (value: number) => void;
  handleReaderContrastChange: (value: number) => void;
  handleReaderEpubFontSizeChange: (value: number) => void;
  handleReaderEpubFontFamilyChange: (value: string) => void;
  buildReaderSettingsDraft: () => ReaderSettings;
  applyReaderSettingsToState: (settings: ReaderSettings) => void;
  loadReader: () => Promise<void>;
  saveReader: () => Promise<void>;
  resetToDefaults: () => void;
} {
  const settingsPort: SettingsPort = deps.settingsPort ?? new TauriSettingsAdapter();
  const getReaderSettingsFn = deps.getReaderSettings ?? (() => settingsPort.getReaderSettings());
  const upsertReaderSettingsFn = deps.upsertReaderSettings ?? ((s: Partial<ReaderSettings>) => settingsPort.upsertReaderSettings(s));

  let readerThemeMode = $state<ReaderThemeMode>('paper');
  let readerBrightness = $state(100);
  let readerContrast = $state(100);
  let readerEpubFontSize = $state(100);
  let readerEpubFontFamily = $state('sans');
  let isSavingSettings = $state(false);
  let settingsError = $state<string | null>(null);

  let initialThemeMode = $state<ReaderThemeMode>('paper');
  let initialBrightness = $state(100);
  let initialContrast = $state(100);
  let initialFontSize = $state(100);
  let initialFontFamily = $state('sans');

  const isDirty = $derived(
    readerThemeMode !== initialThemeMode ||
      readerBrightness !== initialBrightness ||
      readerContrast !== initialContrast ||
      readerEpubFontSize !== initialFontSize ||
      readerEpubFontFamily !== initialFontFamily,
  );
  const isSaving = $derived(isSavingSettings);

  function buildReaderSettingsDraft(): ReaderSettings {
    return {
      themeMode: readerThemeMode,
      brightness: clampInteger(readerBrightness, 50, 150),
      contrast: clampInteger(readerContrast, 50, 150),
      epub: {
        fontSize: clampInteger(readerEpubFontSize, 80, 200),
        fontFamily: normalizeFontFamily(readerEpubFontFamily),
      },
      selectionColor: '#33bbff',
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: 'left',
      direction: 'ltr',
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: 'percentage',
    };
  }

  function applyReaderSettingsToState(settings: ReaderSettings): void {
    readerThemeMode = settings.themeMode;
    readerBrightness = settings.brightness;
    readerContrast = settings.contrast;
    readerEpubFontSize = settings.epub.fontSize;
    readerEpubFontFamily = settings.epub.fontFamily;
  }

  function snapshot(): void {
    initialThemeMode = readerThemeMode;
    initialBrightness = readerBrightness;
    initialContrast = readerContrast;
    initialFontSize = readerEpubFontSize;
    initialFontFamily = readerEpubFontFamily;
  }

  function handleReaderThemeModeChange(mode: ReaderThemeMode): void {
    readerThemeMode = mode;
  }
  function handleReaderBrightnessChange(value: number): void {
    readerBrightness = clampInteger(value, 50, 150);
  }
  function handleReaderContrastChange(value: number): void {
    readerContrast = clampInteger(value, 50, 150);
  }
  function handleReaderEpubFontSizeChange(value: number): void {
    readerEpubFontSize = clampInteger(value, 80, 200);
  }
  function handleReaderEpubFontFamilyChange(value: string): void {
    readerEpubFontFamily = normalizeFontFamily(value);
  }

  async function loadReader(): Promise<void> {
    settingsError = null;
    try {
      const readerSettings = await getReaderSettingsFn();
      applyReaderSettingsToState(readerSettings);
      deps.onReaderSettingsChange?.(readerSettings);
      snapshot();
    } catch (error) {
      settingsError = error instanceof Error ? error.message : String(error);
    }
  }

  async function saveReader(): Promise<void> {
    isSavingSettings = true;
    settingsError = null;
    try {
      const persisted = await upsertReaderSettingsFn(buildReaderSettingsDraft());
      applyReaderSettingsToState(persisted);
      deps.onReaderSettingsChange?.(persisted);
      snapshot();
    } catch (error) {
      settingsError = error instanceof Error ? error.message : String(error);
    } finally {
      isSavingSettings = false;
    }
  }

  function resetToDefaults(): void {
    readerThemeMode = 'paper';
    readerBrightness = 100;
    readerContrast = 100;
    readerEpubFontSize = 100;
    readerEpubFontFamily = 'sans';
  }

  return {
    get readerThemeMode() { return readerThemeMode; },
    set readerThemeMode(v: ReaderThemeMode) { readerThemeMode = v; },
    get readerBrightness() { return readerBrightness; },
    set readerBrightness(v: number) { readerBrightness = clampInteger(v, 50, 150); },
    get readerContrast() { return readerContrast; },
    set readerContrast(v: number) { readerContrast = clampInteger(v, 50, 150); },
    get readerEpubFontSize() { return readerEpubFontSize; },
    set readerEpubFontSize(v: number) { readerEpubFontSize = clampInteger(v, 80, 200); },
    get readerEpubFontFamily() { return readerEpubFontFamily; },
    set readerEpubFontFamily(v: string) { readerEpubFontFamily = normalizeFontFamily(v); },
    get isSavingSettings() { return isSavingSettings; },
    set isSavingSettings(v: boolean) { isSavingSettings = v; },
    get settingsError() { return settingsError; },
    set settingsError(v: string | null) { settingsError = v; },
    get isDirty() { return isDirty; },
    get isSaving() { return isSaving; },
    handleReaderThemeModeChange,
    handleReaderBrightnessChange,
    handleReaderContrastChange,
    handleReaderEpubFontSizeChange,
    handleReaderEpubFontFamilyChange,
    buildReaderSettingsDraft,
    applyReaderSettingsToState,
    loadReader,
    saveReader,
    resetToDefaults,
  };
}
