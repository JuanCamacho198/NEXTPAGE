import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createSettingsAppearance } from '$lib/features/settings/useSettingsAppearance.svelte';

describe('useSettingsAppearance', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('defaults to light/100 and not dirty', () => {
    const a = createSettingsAppearance();
    expect(a.preferredTheme).toBe('light');
    expect(a.preferredFontScale).toBe(100);
    expect(a.isDirty).toBe(false);
    expect(a.isSaving).toBe(false);
  });

  it('handlePreferredThemeChange marks dirty', () => {
    const a = createSettingsAppearance();
    a.handlePreferredThemeChange('dark');
    expect(a.preferredTheme).toBe('dark');
    expect(a.isDirty).toBe(true);
  });

  it('handlePreferredFontScaleChange clamps 80..140', () => {
    const a = createSettingsAppearance();
    a.handlePreferredFontScaleChange(200);
    expect(a.preferredFontScale).toBe(140);
    a.handlePreferredFontScaleChange(10);
    expect(a.preferredFontScale).toBe(80);
    a.handlePreferredFontScaleChange(110);
    expect(a.preferredFontScale).toBe(110);
  });

  it('loadAppearance parses theme and fontScale from getSettings', async () => {
    const getSettings = vi.fn().mockResolvedValue([
      { key: 'ui.theme', valueJson: JSON.stringify('dark'), updatedAt: new Date().toISOString() },
      { key: 'reader.fontScale', valueJson: JSON.stringify(120), updatedAt: new Date().toISOString() },
    ]);
    const getLocaleSetting = vi.fn().mockResolvedValue(null);
    const a = createSettingsAppearance({ getSettings, getLocaleSetting });
    await a.loadAppearance();
    expect(a.preferredTheme).toBe('dark');
    expect(a.preferredFontScale).toBe(120);
    expect(a.isDirty).toBe(false);
  });

  it('loadAppearance clamps fontScale', async () => {
    const getSettings = vi.fn().mockResolvedValue([
      { key: 'reader.fontScale', valueJson: JSON.stringify(999), updatedAt: new Date().toISOString() },
    ]);
    const getLocaleSetting = vi.fn().mockResolvedValue(null);
    const a = createSettingsAppearance({ getSettings, getLocaleSetting });
    await a.loadAppearance();
    expect(a.preferredFontScale).toBe(140);
  });

  it('saveAppearance calls upsertSettings with theme/fontScale', async () => {
    const upsertSettings = vi.fn().mockResolvedValue(undefined);
    const getSettings = vi.fn().mockResolvedValue([]);
    const getLocaleSetting = vi.fn().mockResolvedValue(null);
    const a = createSettingsAppearance({ getSettings, upsertSettings, getLocaleSetting });
    a.handlePreferredThemeChange('sepia');
    a.handlePreferredFontScaleChange(110);
    await a.saveAppearance();
    expect(upsertSettings).toHaveBeenCalledTimes(1);
    const payload = upsertSettings.mock.calls[0][0] as Array<{ key: string; valueJson: string }>;
    expect(payload.find((p) => p.key === 'ui.theme')?.valueJson).toBe(JSON.stringify('sepia'));
    expect(payload.find((p) => p.key === 'reader.fontScale')?.valueJson).toBe(JSON.stringify(110));
    expect(a.isDirty).toBe(false);
  });

  it('handleLocaleSelect calls setLocale and onLocaleChange', async () => {
    const setLocale = vi.fn().mockResolvedValue(undefined);
    const onLocaleChange = vi.fn();
    const toSupportedLocale = (v: string | null): string | null => (v === 'en' ? 'en' : null);
    const mockedLocale = vi.fn(toSupportedLocale) as unknown as (v: string | null) => 'es' | 'en' | null;
    const a = createSettingsAppearance({ setLocale, onLocaleChange, toSupportedLocale: mockedLocale } as unknown as Parameters<typeof createSettingsAppearance>[0]);
    await a.handleLocaleSelect('en');
    expect(setLocale).toHaveBeenCalledWith('en');
    expect(onLocaleChange).toHaveBeenCalledWith('en');
    expect(a.locale).toBe('en');
  });

  it('resetToDefaults restores light/100', () => {
    const a = createSettingsAppearance();
    a.handlePreferredThemeChange('dark');
    a.handlePreferredFontScaleChange(130);
    a.resetToDefaults();
    expect(a.preferredTheme).toBe('light');
    expect(a.preferredFontScale).toBe(100);
  });
});
