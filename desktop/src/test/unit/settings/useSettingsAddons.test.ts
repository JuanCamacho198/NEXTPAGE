import { describe, it, expect, vi } from 'vitest';

import { createSettingsAddons } from '$lib/features/settings/useSettingsAddons.svelte';
import {
  AddonFetchError,
  AddonFetchErrorCode,
  type InstalledAddonRow,
} from '$lib/shared/services/addons/AddonRegistry';

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

  it('tracks installOutcome idle → installing → idle on success', async () => {
    const registry = fakeRegistry();
    let release!: (value: typeof ROW.manifest) => void;
    const gate = new Promise<typeof ROW.manifest>((resolve) => {
      release = resolve;
    });
    registry.install.mockReturnValueOnce(gate);
    const d = createSettingsAddons({ registry });
    expect(d.installOutcome).toEqual({ kind: 'idle' });
    d.url = 'https://example.com/manifest.json';
    const pending = d.handleInstall();
    expect(d.installOutcome).toEqual({ kind: 'installing' });
    release(ROW.manifest);
    await pending;
    expect(d.installOutcome).toEqual({ kind: 'idle' });
    expect(d.isBusy).toBe(false);
  });

  it('records an inline error outcome with the fetch code on install failure', async () => {
    const registry = fakeRegistry();
    registry.install.mockRejectedValue(
      new AddonFetchError(AddonFetchErrorCode.INVALID_MANIFEST, 'bad manifest'),
    );
    const pushToast = vi.fn();
    const d = createSettingsAddons({ registry, pushToast });
    d.url = 'https://example.com/manifest.json';
    await d.handleInstall();
    expect(d.installOutcome).toEqual({ kind: 'error', code: 'ADDON_FETCH_INVALID_MANIFEST' });
    expect(pushToast).toHaveBeenCalledWith('error', expect.any(String));
    expect(d.url).toBe('https://example.com/manifest.json');
    expect(d.isBusy).toBe(false);
  });

  it('maps a transport NETWORK failure to the offline outcome', async () => {
    const registry = fakeRegistry();
    registry.install.mockRejectedValue(
      new AddonFetchError(AddonFetchErrorCode.NETWORK, 'connection down'),
    );
    const pushToast = vi.fn();
    const d = createSettingsAddons({ registry, pushToast });
    d.url = 'https://example.com/manifest.json';
    await d.handleInstall();
    expect(d.installOutcome).toEqual({ kind: 'offline' });
    expect(pushToast).toHaveBeenCalledWith('error', expect.any(String));
    expect(d.isBusy).toBe(false);
  });

  it('fails fast with offline and no registry I/O when navigator is offline', async () => {
    const restore = stubOnline(false);
    try {
      const registry = fakeRegistry();
      const pushToast = vi.fn();
      const d = createSettingsAddons({ registry, pushToast });
      d.url = 'https://example.com/manifest.json';
      await d.handleInstall();
      expect(registry.install).not.toHaveBeenCalled();
      expect(d.installOutcome).toEqual({ kind: 'offline' });
      expect(pushToast).toHaveBeenCalledWith('error', expect.any(String));
      expect(d.url).toBe('https://example.com/manifest.json');
      expect(d.isBusy).toBe(false);
    } finally {
      restore();
    }
  });

  it('resolves pushToast per call so a late-bound notifier is honored', async () => {
    const registry = fakeRegistry();
    registry.install.mockRejectedValue(new Error('boom'));
    const deps = { registry, pushToast: vi.fn() };
    const d = createSettingsAddons(deps);
    const late = vi.fn();
    deps.pushToast = late;
    d.url = 'https://example.com/manifest.json';
    await d.handleInstall();
    expect(late).toHaveBeenCalledWith('error', expect.any(String));
    expect(deps.pushToast).toBe(late);
  });

  it('shares one registry across surfaces: a mutation through one instance is observed by the other', async () => {
    const registry = fakeRegistry();
    const settingsSurface = createSettingsAddons({ registry });
    const screenSurface = createSettingsAddons({ registry });
    settingsSurface.url = 'https://example.com/manifest.json';
    await settingsSurface.handleInstall();
    await screenSurface.refresh();
    expect(screenSurface.installed).toHaveLength(1);
    expect(screenSurface.installed[0].url).toBe('https://example.com/manifest.json');
  });
});

/** Stub navigator.onLine; returns a restore function. */
function stubOnline(value: boolean): () => void {
  const original = Object.getOwnPropertyDescriptor(window.navigator, 'onLine');
  Object.defineProperty(window.navigator, 'onLine', { value, configurable: true });
  return () => {
    if (original) Object.defineProperty(window.navigator, 'onLine', original);
    else Reflect.deleteProperty(window.navigator, 'onLine');
  };
}
