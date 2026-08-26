<script lang="ts">
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import { Button } from '$lib/shared/ui';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { ReaderThemeMode } from '$lib/shared/types';
  import type { createSettingsReader } from '../useSettingsReader.svelte';

  type ReaderState = ReturnType<typeof createSettingsReader>;

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    // New composable-driven API (preferred): single reader object
    reader?: ReaderState;
    // Legacy scalar API (fallback) — kept for compatibility until panel migration completes
    readerThemeMode?: ReaderThemeMode;
    readerBrightness?: number;
    readerContrast?: number;
    readerEpubFontSize?: number;
    readerEpubFontFamily?: string;
    isSavingSettings?: boolean;
    onSaveSettings?: () => void;
    onOpenResetModal: () => void;
    onReaderThemeModeChange?: (mode: ReaderThemeMode) => void;
    onReaderBrightnessChange?: (value: number) => void;
    onReaderContrastChange?: (value: number) => void;
    onReaderEpubFontSizeChange?: (value: number) => void;
    onReaderEpubFontFamilyChange?: (value: string) => void;
  };

  let {
    t,
    reader,
    readerThemeMode: legacyThemeMode,
    readerBrightness: legacyBrightness,
    readerContrast: legacyContrast,
    readerEpubFontSize: legacyFontSize,
    readerEpubFontFamily: legacyFontFamily,
    isSavingSettings: legacySaving,
    onSaveSettings: legacySave,
    onOpenResetModal,
    onReaderThemeModeChange: legacyOnTheme,
    onReaderBrightnessChange: legacyOnBrightness,
    onReaderContrastChange: legacyOnContrast,
    onReaderEpubFontSizeChange: legacyOnSize,
    onReaderEpubFontFamilyChange: legacyOnFamily,
  }: Props = $props();

  const fontFamilyOptions = [
    { value: 'serif', label: 'Serif' },
    { value: 'sans-serif', label: 'Sans Serif' },
    { value: 'monospace', label: 'Monospace' },
  ];

  const themeMode = $derived(reader?.readerThemeMode ?? legacyThemeMode ?? 'paper');
  const brightness = $derived(reader?.readerBrightness ?? legacyBrightness ?? 100);
  const contrastVal = $derived(reader?.readerContrast ?? legacyContrast ?? 100);
  const fontSizeVal = $derived(reader?.readerEpubFontSize ?? legacyFontSize ?? 100);
  const fontFamilyVal = $derived(reader?.readerEpubFontFamily ?? legacyFontFamily ?? 'sans');
  const saving = $derived(reader?.isSavingSettings ?? legacySaving ?? false);

  function handleThemeMode(mode: ReaderThemeMode): void {
    if (reader) reader.handleReaderThemeModeChange(mode);
    else legacyOnTheme?.(mode);
  }
  function handleBrightness(v: number): void {
    if (reader) reader.handleReaderBrightnessChange(v);
    else legacyOnBrightness?.(v);
  }
  function handleContrast(v: number): void {
    if (reader) reader.handleReaderContrastChange(v);
    else legacyOnContrast?.(v);
  }
  function handleFontSize(v: number): void {
    if (reader) reader.handleReaderEpubFontSizeChange(v);
    else legacyOnSize?.(v);
  }
  function handleFontFamily(v: string): void {
    if (reader) reader.handleReaderEpubFontFamilyChange(v);
    else legacyOnFamily?.(v);
  }
  function handleSave(): void {
    if (reader) void reader.saveReader();
    else legacySave?.();
  }
</script>

<div role="tabpanel" id="tabpanel-reader" aria-labelledby="tab-reader" class="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
  <section class="rounded-xl border border-(--color-border) bg-(--color-surface) overflow-hidden">
    <div class="p-4 space-y-4">
      <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
        {t('settings.appearance.reader')}
      </h3>
      <div class="flex gap-2 justify-stretch mb-4">
        <button
          type="button"
          class="flex-1 py-3 px-2 rounded-lg border-2 transition-all duration-200 flex items-center justify-center text-2xs font-medium cursor-pointer"
          class:border-(--color-primary)={themeMode === 'paper'}
          class:border-(--color-border)={themeMode !== 'paper'}
          style="--preview-bg: #fafafa; --preview-text: #1a1a1a; --preview-border: #e0e0e0; background: var(--preview-bg); color: var(--preview-text);"
          onclick={() => handleThemeMode('paper')}
        >
          {t('settings.reader.themeMode.paper')}
        </button>
        <button
          type="button"
          class="flex-1 py-3 px-2 rounded-lg border-2 transition-all duration-200 flex items-center justify-center text-2xs font-medium cursor-pointer"
          class:border-(--color-primary)={themeMode === 'sepia'}
          class:border-(--color-border)={themeMode !== 'sepia'}
          style="--preview-bg: #f4ecd8; --preview-text: #5b4636; --preview-border: #d4c4a8; background: var(--preview-bg); color: var(--preview-text);"
          onclick={() => handleThemeMode('sepia')}
        >
          {t('settings.reader.themeMode.sepia')}
        </button>
        <button
          type="button"
          class="flex-1 py-3 px-2 rounded-lg border-2 transition-all duration-200 flex items-center justify-center text-2xs font-medium cursor-pointer"
          class:border-(--color-primary)={themeMode === 'night'}
          class:border-(--color-border)={themeMode !== 'night'}
          style="--preview-bg: #1a1a1a; --preview-text: #e8e8e8; --preview-border: #333333; background: var(--preview-bg); color: var(--preview-text);"
          onclick={() => handleThemeMode('night')}
        >
          {t('settings.reader.themeMode.night')}
        </button>
      </div>

      <div class="space-y-4">
        <div class="mb-2">
          <label class="mb-1 block text-xs text-(--color-text-muted)" for="reader-brightness"
            >{t('settings.reader.brightness')}: {brightness}%</label
          >
          <input
            type="range"
            id="reader-brightness"
            min="50"
            max="150"
            value={brightness}
            oninput={(e) => handleBrightness(Number((e.target as HTMLInputElement).value))}
            class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
          />
        </div>

        <div class="mb-2">
          <label class="mb-1 block text-xs text-(--color-text-muted)" for="reader-contrast"
            >{t('settings.reader.contrast')}: {contrastVal}%</label
          >
          <input
            type="range"
            id="reader-contrast"
            min="50"
            max="150"
            value={contrastVal}
            oninput={(e) => handleContrast(Number((e.target as HTMLInputElement).value))}
            class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
          />
        </div>

        <div class="mb-2">
          <label class="mb-1 block text-xs text-(--color-text-muted)" for="reader-font-size"
            >{t('settings.reader.epub.fontSize')}: {fontSizeVal}%</label
          >
          <input
            type="range"
            id="reader-font-size"
            min="80"
            max="200"
            value={fontSizeVal}
            oninput={(e) => handleFontSize(Number((e.target as HTMLInputElement).value))}
            class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
          />
        </div>

        <div class="mb-2">
          <label class="mb-1 block text-xs text-(--color-text-muted)" for="reader-font-family"
            >{t('settings.reader.epub.fontFamily')}</label
          >
          <Dropdown
            options={fontFamilyOptions}
            value={fontFamilyVal}
            class="w-full"
            onchange={({ value }) => handleFontFamily(value)}
          />
        </div>

        <div class="flex gap-2 mt-4">
          <Button onclick={handleSave} disabled={saving} size="sm">
            {saving ? t('settings.saving') : t('settings.savePreferences')}
          </Button>
          <Button onclick={onOpenResetModal} variant="danger" size="sm">
            {t('settings.resetDefaults')}
          </Button>
        </div>
      </div>
    </div>
  </section>
</div>
