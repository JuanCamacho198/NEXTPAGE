import { describe, it, expect, vi } from 'vitest';
import { createSettingsData } from '$lib/features/settings/useSettingsData.svelte';

describe('useSettingsData', () => {
  it('defaults and isDirty false', () => {
    const d = createSettingsData();
    expect(d.selectedExportBook).toBe('all');
    expect(d.selectedExportFormat).toBe('json');
    expect(d.isDirty).toBe(false);
    expect(d.isSaving).toBe(false);
  });

  it('export book/format change marks dirty', () => {
    const d = createSettingsData();
    d.handleSelectedExportBookChange('book-1');
    expect(d.isDirty).toBe(true);
    d.handleSelectedExportBookChange('all');
    d.handleSelectedExportFormatChange('markdown');
    expect(d.isDirty).toBe(true);
  });

  it('handleClearCache calls storageState.clearCache and toasts success', async () => {
    const clearCache = vi.fn().mockResolvedValue({ freedBytes: 123 });
    const pushToast = vi.fn();
    const t = vi.fn((k: string) => k);
    const storageState = { clearCache } as unknown as Parameters<typeof createSettingsData>[0] extends { storageState?: infer S } ? S : never;
    const d = createSettingsData({ storageState: storageState as never, pushToast: pushToast as never, t: t as never });
    await d.handleClearCache();
    expect(clearCache).toHaveBeenCalledWith('temp', false);
    expect(d.cacheCleared).toBe(true);
    expect(pushToast).toHaveBeenCalledWith('success', expect.any(String));
  });

  it('handleClearCache toasts permission_denied on that error', async () => {
    const clearCache = vi.fn().mockRejectedValue(new Error('storage.permission_denied'));
    const pushToast = vi.fn();
    const d = createSettingsData({ storageState: { clearCache } as never, pushToast: pushToast as never });
    await d.handleClearCache();
    expect(pushToast).toHaveBeenCalledWith('error', 'storage.permission_denied');
  });

  it('handleExportColdBackup calls Drive service when userId present', async () => {
    const exportColdBackup = vi.fn().mockResolvedValue(undefined);
    const pushToast = vi.fn();
    const t = vi.fn((k: string) => k);
    const authState = { userId: 'user-1' } as never;
    const DriveColdBackupService = { exportColdBackup } as never;
    const d = createSettingsData({ authState, DriveColdBackupService, pushToast: pushToast as never, t: t as never });
    await d.handleExportColdBackup();
    expect(exportColdBackup).toHaveBeenCalledWith('user-1');
    expect(pushToast).toHaveBeenCalledWith('success', expect.any(String));
  });

  it('handleImportColdBackup calls import when userId present', async () => {
    const importColdBackup = vi.fn().mockResolvedValue(undefined);
    const pushToast = vi.fn();
    const t = vi.fn((k: string) => k);
    const authState = { userId: 'u1' } as never;
    const DriveColdBackupService = { importColdBackup } as never;
    const d = createSettingsData({ authState, DriveColdBackupService, pushToast: pushToast as never, t: t as never });
    await d.handleImportColdBackup();
    expect(importColdBackup).toHaveBeenCalledWith('u1');
  });

  it('handleExportColdBackup errors when no userId', async () => {
    const pushToast = vi.fn();
    const t = vi.fn((k: string) => k);
    const authState = { userId: null } as never;
    const d = createSettingsData({ authState, pushToast: pushToast as never, t: t as never });
    await d.handleExportColdBackup();
    expect(pushToast).toHaveBeenCalledWith('error', expect.any(String));
  });
});
