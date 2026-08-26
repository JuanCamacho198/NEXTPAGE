import { describe, it, expect, vi } from 'vitest';
import { createSettingsReader } from '$lib/features/settings/useSettingsReader.svelte';

describe('useSettingsReader', () => {
  it('defaults and isDirty false', () => {
    const r = createSettingsReader();
    expect(r.readerThemeMode).toBe('paper');
    expect(r.readerBrightness).toBe(100);
    expect(r.readerContrast).toBe(100);
    expect(r.readerEpubFontSize).toBe(100);
    expect(r.readerEpubFontFamily).toBe('sans');
    expect(r.isDirty).toBe(false);
  });

  it('brightness/contrast clamp 50..150', () => {
    const r = createSettingsReader();
    r.handleReaderBrightnessChange(300);
    expect(r.readerBrightness).toBe(150);
    r.handleReaderBrightnessChange(0);
    expect(r.readerBrightness).toBe(50);
    r.handleReaderContrastChange(200);
    expect(r.readerContrast).toBe(150);
  });

  it('fontSize clamp 80..200', () => {
    const r = createSettingsReader();
    r.handleReaderEpubFontSizeChange(500);
    expect(r.readerEpubFontSize).toBe(200);
    r.handleReaderEpubFontSizeChange(10);
    expect(r.readerEpubFontSize).toBe(80);
  });

  it('fontFamily normalizes blank to sans', () => {
    const r = createSettingsReader();
    r.handleReaderEpubFontFamilyChange('  ');
    expect(r.readerEpubFontFamily).toBe('sans');
    r.handleReaderEpubFontFamilyChange('serif');
    expect(r.readerEpubFontFamily).toBe('serif');
  });

  it('isDirty after mutation', () => {
    const r = createSettingsReader();
    r.handleReaderThemeModeChange('night');
    expect(r.isDirty).toBe(true);
  });

  it('loadReader applies settings and clears dirty', async () => {
    const mockSettings = {
      themeMode: 'night' as const,
      brightness: 120,
      contrast: 110,
      selectionColor: '#33bbff',
      epub: { fontSize: 150, fontFamily: 'serif' },
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: 'left' as const,
      direction: 'ltr' as const,
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: 'percentage' as const,
    };
    const getReaderSettings = vi.fn().mockResolvedValue(mockSettings);
    const onReaderSettingsChange = vi.fn();
    const r = createSettingsReader({ getReaderSettings, onReaderSettingsChange });
    await r.loadReader();
    expect(r.readerThemeMode).toBe('night');
    expect(r.readerBrightness).toBe(120);
    expect(r.readerEpubFontSize).toBe(150);
    expect(r.isDirty).toBe(false);
    expect(onReaderSettingsChange).toHaveBeenCalledWith(mockSettings);
  });

  it('saveReader calls upsertReaderSettings with draft', async () => {
    const upsertReaderSettings = vi.fn().mockImplementation(async (draft) => draft as unknown as ReturnType<typeof upsertReaderSettings>);
    const getReaderSettings = vi.fn().mockResolvedValue({
      themeMode: 'paper' as const,
      brightness: 100,
      contrast: 100,
      selectionColor: '#33bbff',
      epub: { fontSize: 100, fontFamily: 'sans' },
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: 'left' as const,
      direction: 'ltr' as const,
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: 'percentage' as const,
    });
    const r = createSettingsReader({ getReaderSettings, upsertReaderSettings });
    r.handleReaderBrightnessChange(130);
    await r.saveReader();
    expect(upsertReaderSettings).toHaveBeenCalledTimes(1);
    const draft = upsertReaderSettings.mock.calls[0][0] as { brightness: number };
    expect(draft.brightness).toBe(130);
    expect(r.isDirty).toBe(false);
  });

  it('buildReaderSettingsDraft clamps values', () => {
    const r = createSettingsReader();
    r.handleReaderBrightnessChange(999);
    r.handleReaderEpubFontSizeChange(999);
    const draft = r.buildReaderSettingsDraft();
    expect(draft.brightness).toBe(150);
    expect(draft.epub.fontSize).toBe(200);
  });

  it('resetToDefaults restores paper/100/sans', () => {
    const r = createSettingsReader();
    r.handleReaderThemeModeChange('night');
    r.handleReaderBrightnessChange(130);
    r.resetToDefaults();
    expect(r.readerThemeMode).toBe('paper');
    expect(r.readerBrightness).toBe(100);
    expect(r.readerEpubFontFamily).toBe('sans');
  });
});
