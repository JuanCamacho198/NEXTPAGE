import { pushToast as defaultPushToast } from '$lib/shared/stores/ToastQueue.svelte';
import {
  AddonRegistry as DefaultAddonRegistry,
  type InstalledAddonRow,
} from '$lib/shared/services/addons/AddonRegistry';

export type AddonsDeps = {
  registry?: {
    listInstalled(): Promise<InstalledAddonRow[]>;
    install(url: string): Promise<unknown>;
    setEnabled(id: string, enabled: boolean): Promise<void>;
    uninstall(id: string): Promise<void>;
    /** Registry-mutation hook: fires after install/enable/disable/uninstall. */
    onChanged?: (listener: (version: number) => void) => () => void;
  };
  /** Called when the registry mutates so the live Discover composite rebuilds. */
  onAddonsChanged?: () => void;
  pushToast?: typeof defaultPushToast;
  t?: (key: string, params?: Record<string, string | number>) => string;
};

export function createSettingsAddons(deps: AddonsDeps = {}): {
  url: string;
  installed: InstalledAddonRow[];
  isBusy: boolean;
  refresh: () => Promise<void>;
  handleInstall: () => Promise<void>;
  handleToggle: (id: string, enabled: boolean) => Promise<void>;
  handleUninstall: (id: string) => Promise<void>;
} {
  const registry = deps.registry ?? new DefaultAddonRegistry();
  const pushToast = deps.pushToast ?? defaultPushToast;
  const t = deps.t ?? ((k: string) => k);
  registry.onChanged?.(() => deps.onAddonsChanged?.());

  let url = $state('');
  let installed = $state<InstalledAddonRow[]>([]);
  let isBusy = $state(false);

  async function refresh(): Promise<void> {
    installed = await registry.listInstalled();
  }

  async function handleInstall(): Promise<void> {
    const target = url.trim();
    if (target === '') return;
    isBusy = true;
    try {
      await registry.install(target);
      url = '';
      await refresh();
      pushToast('success', t('settings.addons.installedToast'));
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : t('settings.addons.installFailed'));
    } finally {
      isBusy = false;
    }
  }

  async function handleToggle(id: string, enabled: boolean): Promise<void> {
    isBusy = true;
    try {
      await registry.setEnabled(id, enabled);
      await refresh();
    } finally {
      isBusy = false;
    }
  }

  async function handleUninstall(id: string): Promise<void> {
    isBusy = true;
    try {
      await registry.uninstall(id);
      await refresh();
    } finally {
      isBusy = false;
    }
  }

  return {
    get url() { return url; },
    set url(v: string) { url = v; },
    get installed() { return installed; },
    get isBusy() { return isBusy; },
    refresh,
    handleInstall,
    handleToggle,
    handleUninstall,
  };
}
