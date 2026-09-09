import { describe, it, expect, vi } from 'vitest';

import { createSettingsAddons } from '$lib/features/settings/useSettingsAddons.svelte';
import type { InstalledAddonRow } from '$lib/shared/services/addons/AddonRegistry';

const ROW: InstalledAddonRow = {
  id: 'addon-id-1',
  url: 'https://example.com/manifest.json',
  manifest: {
    id: 'my-addon',
    name: 'My Addon',
    version: '1.0.0',
    catalogs: [{ type: 'book', id: 'main', name: 'Main catalog' }],
    resources: ['catalog'],
  },
  enabled: true,
  addedAt: 1,
};

function fakeRegistry() {
  let rows: InstalledAddonRow[] = [];
  return {
    setRows(next: InstalledAddonRow[]) {
      rows = next;
    },
    listInstalled: vi.fn(async () => rows),
    install: vi.fn(async (url: string) => {
      rows = [...rows, { ...ROW, id: `id-${url}`, url }];
      return ROW.manifest;
    }),
    setEnabled: vi.fn(async (id: string, enabled: boolean) => {
      rows = rows.map((r) => (r.id === id ? { ...r, enabled } : r));
    }),
    uninstall: vi.fn(async (id: string) => {
      rows = rows.filter((r) => r.id !== id);
    }),
  };
}

describe('useSettingsAddons', () => {
  it('loads installed addons on refresh', async () => {
    const registry = fakeRegistry();
    registry.setRows([ROW]);
    const d = createSettingsAddons({ registry });
    await d.refresh();
    expect(registry.listInstalled).toHaveBeenCalled();
    expect(d.installed).toHaveLength(1);
    expect(d.installed[0].id).toBe('addon-id-1');
    expect(d.isBusy).toBe(false);
  });

  it('handleInstall installs by URL, clears input, refreshes, toasts success', async () => {
    const registry = fakeRegistry();
    const pushToast = vi.fn();
    const d = createSettingsAddons({ registry, pushToast });
    d.url = 'https://example.com/manifest.json';
    await d.handleInstall();
    expect(registry.install).toHaveBeenCalledWith('https://example.com/manifest.json');
    expect(d.url).toBe('');
    expect(d.installed).toHaveLength(1);
    expect(pushToast).toHaveBeenCalledWith('success', expect.any(String));
    expect(d.isBusy).toBe(false);
  });

  it('handleInstall toasts error and keeps URL on failure', async () => {
    const registry = fakeRegistry();
    registry.install.mockRejectedValue(new Error('ADDON_FETCH_HTTPS_REQUIRED'));
    const pushToast = vi.fn();
    const d = createSettingsAddons({ registry, pushToast });
    d.url = 'http://example.com/manifest.json';
    await d.handleInstall();
    expect(pushToast).toHaveBeenCalledWith('error', expect.any(String));
    expect(d.url).toBe('http://example.com/manifest.json');
    expect(d.isBusy).toBe(false);
  });

  it('handleInstall ignores empty or blank URLs', async () => {
    const registry = fakeRegistry();
    const d = createSettingsAddons({ registry });
    d.url = '   ';
    await d.handleInstall();
    expect(registry.install).not.toHaveBeenCalled();
  });

  it('handleToggle persists enable/disable and refreshes', async () => {
    const registry = fakeRegistry();
    registry.setRows([ROW]);
    const d = createSettingsAddons({ registry });
    await d.refresh();
    await d.handleToggle('addon-id-1', false);
    expect(registry.setEnabled).toHaveBeenCalledWith('addon-id-1', false);
    expect(d.installed[0].enabled).toBe(false);
  });

  it('handleUninstall removes the addon and refreshes', async () => {
    const registry = fakeRegistry();
    registry.setRows([ROW]);
    const d = createSettingsAddons({ registry });
    await d.refresh();
    await d.handleUninstall('addon-id-1');
    expect(registry.uninstall).toHaveBeenCalledWith('addon-id-1');
    expect(d.installed).toHaveLength(0);
  });
});
