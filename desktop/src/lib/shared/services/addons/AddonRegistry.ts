/**
 * AddonRegistry — install-by-URL service over the desktop registry store.
 * install(url): HTTPS check → platform fetch → validateManifest → addonId →
 * UPSERT preserving enabled. enable/disable/uninstall mutate rows; the
 * composite's existence-check cache guard (design A1) keeps uninstalled or
 * disabled addon sources from ever serving cached entries.
 */
import { invoke } from '@tauri-apps/api/core';

import { addonIdFromUrl } from './addonId';
import {
  AddonFetchError,
  AddonFetchErrorCode,
  assertHttpsInstallUrl,
  validateManifest,
  type AddonManifest,
} from './validateManifest';

/** Closed set of built-in source names addon manifests must not claim. */
const BUILTIN_SOURCE_NAMES = new Set(['gutendex', 'openlibrary']);

export { AddonFetchErrorCode, AddonFetchError };
export type { AddonManifest };

export interface InstalledAddonRow {
  id: string;
  url: string;
  manifest: AddonManifest;
  enabled: boolean;
  addedAt: number;
}

/** Raw persistence row; mirrors Rust `InstalledAddonDto` / Room `AddonEntity`. */
interface RegistryStoreRow {
  id: string;
  url: string;
  manifestJson: string;
  enabled: boolean;
  addedAt: number;
}

export interface AddonRegistryStore {
  listInstalled(): Promise<RegistryStoreRow[]>;
  upsert(row: RegistryStoreRow): Promise<void>;
  setEnabled(id: string, enabled: boolean): Promise<void>;
  uninstall(id: string): Promise<void>;
}

/** Default store: the thin Rust CRUD commands (desktop only). */
export class TauriAddonRegistryStore implements AddonRegistryStore {
  async listInstalled(): Promise<RegistryStoreRow[]> {
    return invoke<RegistryStoreRow[]>('listInstalledAddons');
  }

  async upsert(row: RegistryStoreRow): Promise<void> {
    await invoke('upsertInstalledAddon', { addon: row });
  }

  async setEnabled(id: string, enabled: boolean): Promise<void> {
    await invoke('setAddonEnabled', { id, enabled });
  }

  async uninstall(id: string): Promise<void> {
    await invoke('deleteInstalledAddon', { id });
  }
}

export interface AddonFetchResult {
  status: number;
  contentType: string | null;
  body: Uint8Array;
}

/** Platform network layer seam (desktop: the Rust fetchAddonResource command). */
export type AddonTransport = (url: string) => Promise<AddonFetchResult>;

export class AddonRegistry {
  private readonly store: AddonRegistryStore;
  private readonly transport: AddonTransport;
  private readonly now: () => number;

  constructor(options: {
    store?: AddonRegistryStore;
    transport?: AddonTransport;
    now?: () => number;
  } = {}) {
    this.store = options.store ?? new TauriAddonRegistryStore();
    this.transport =
      options.transport ??
      (async (url) => {
        const res = await invoke<{ status: number; contentType: string | null; body: number[] }>(
          'fetchAddonResource',
          { url },
        );
        return { status: res.status, contentType: res.contentType, body: new Uint8Array(res.body) };
      });
    this.now = options.now ?? (() => Date.now());
  }

  async install(url: string): Promise<AddonManifest> {
    assertHttpsInstallUrl(url);
    const fetched = await this.transport(url);
    if (fetched.status < 200 || fetched.status >= 300) {
      throw new AddonFetchError(AddonFetchErrorCode.NETWORK, `addon fetch status ${fetched.status}`);
    }
    const manifest = validateManifest(fetched.body, fetched.contentType);
    if (
      BUILTIN_SOURCE_NAMES.has(manifest.id) ||
      manifest.id.includes(':') ||
      manifest.id.startsWith('builtin')
    ) {
      throw new AddonFetchError(
        AddonFetchErrorCode.INVALID_MANIFEST,
        `addon id must not collide with built-in source ids: ${manifest.id}`,
      );
    }
    const addonId = await addonIdFromUrl(url);
    const existing = (await this.store.listInstalled()).find((row) => row.id === addonId);
    await this.store.upsert({
      id: addonId,
      url,
      manifestJson: JSON.stringify(manifest),
      enabled: existing ? existing.enabled : true,
      addedAt: existing ? existing.addedAt : this.now(),
    });
    return manifest;
  }

  async listInstalled(): Promise<InstalledAddonRow[]> {
    const rows = await this.store.listInstalled();
    return rows
      .map((row) => {
        try {
          return { row, manifest: JSON.parse(row.manifestJson) as AddonManifest };
        } catch {
          return { row, manifest: null };
        }
      })
      .filter((entry): entry is { row: RegistryStoreRow; manifest: AddonManifest } => entry.manifest !== null)
      .map(({ row, manifest }) => ({
        id: row.id,
        url: row.url,
        manifest,
        enabled: row.enabled,
        addedAt: row.addedAt,
      }))
      .sort((a, b) => a.addedAt - b.addedAt || a.id.localeCompare(b.id));
  }

  async setEnabled(id: string, enabled: boolean): Promise<void> {
    await this.store.setEnabled(id, enabled);
  }

  async uninstall(id: string): Promise<void> {
    await this.store.uninstall(id);
  }
}
