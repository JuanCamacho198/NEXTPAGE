/**
 * useAddonReadSheet — addon read-sheet state machine (slice 9, Domain C).
 *
 * Exact state union: `Hidden | Resolving | Downloading {downloaded,total} |
 * Loaded {addonName,access} | Empty {addonName} | Error {code} |
 * ConsentRequired {addonId,addonName}`. Every collaborator arrives through
 * injected ports, so the machine is fully testable offline:
 *
 * - `consent`: durable grant/revoke for the `ConsentRequired` allow/deny.
 * - `addonNameOf`: display name for an addon id.
 * - `resolve`: addon access resolution (wired to
 *   `catalogProvider.resolveAddonAccess`; already consent-gated, so
 *   `ConsentRequired` is entered with zero resolve I/O).
 * - `download`: starts the in-app download on the SINGLE existing
 *   `DiscoverDomainState` download machine (no second pipeline) and reports
 *   its backend byte progress through `onProgress`; the returned task's
 *   promise settles with the terminal outcome (`imported` | `cancelled` |
 *   `failed`) and its `cancel` aborts the backend transfer.
 *
 * Transitions: `open` → `Resolving` → (`Loaded` with usable options |
 * `Empty` on zero usable options | `Error` | `ConsentRequired`); a
 * downloadable resolution auto-starts the single-machine download →
 * `Downloading` (backend byte progress) → `imported` maps to
 * `Loaded`/`Empty` by usable options, `cancelled` maps back to `Hidden`,
 * `failed` maps to `Error`. `ConsentRequired` allow/deny grants/revokes then
 * re-resolves. `retry` re-resolves the current book. `close` cancels any
 * in-flight task and returns to `Hidden`. Default `Hidden` composes nothing.
 */
import type { CatalogBook } from '$lib/shared/services/catalog/CatalogProvider';
import { isCatalogError, type CatalogErrorCode } from '$lib/shared/services/catalog/errors';
import type { AddonAccessResolution } from '$lib/shared/services/addons/AddonCatalogProvider';

export type AddonReadState =
  | { kind: 'Hidden' }
  | { kind: 'Resolving' }
  | { kind: 'Downloading'; downloaded: number; total: number | null }
  | { kind: 'Loaded'; addonName: string; access: AddonAccessResolution }
  | { kind: 'Empty'; addonName: string }
  | { kind: 'Error'; code: CatalogErrorCode }
  | { kind: 'ConsentRequired'; addonId: string; addonName: string };

/** Terminal outcome of the single-machine in-app download. */
export type AddonReadDownloadOutcome =
  { kind: 'imported' } | { kind: 'cancelled' } | { kind: 'failed'; code: CatalogErrorCode };

/** In-flight in-app download: the settling promise plus the backend cancel. */
export interface AddonReadDownloadTask {
  promise: Promise<AddonReadDownloadOutcome>;
  cancel: () => void;
}

export interface AddonReadSheetDeps {
  consent: {
    grant: (addonId: string) => Promise<void>;
    revoke: (addonId: string) => Promise<void>;
  };
  addonNameOf: (addonId: string) => string;
  resolve: (book: CatalogBook) => Promise<AddonAccessResolution>;
  download: (
    book: CatalogBook,
    url: string,
    onProgress: (downloaded: number, total: number | null) => void,
  ) => AddonReadDownloadTask;
}

function hasExternalOptions(access: AddonAccessResolution): boolean {
  return access.options.length > 0;
}

export function createAddonReadSheet(deps: AddonReadSheetDeps): {
  readonly state: AddonReadState;
  open: (book: CatalogBook, addonId: string) => Promise<void>;
  close: () => void;
  retry: () => Promise<void>;
  allow: () => Promise<void>;
  deny: () => Promise<void>;
  cancelDownload: () => void;
} {
  let state = $state<AddonReadState>({ kind: 'Hidden' });
  /** Bumped on every open/close so a stale async settle can never land. */
  let generation = 0;
  let current: { book: CatalogBook; addonId: string } | null = null;
  let task: AddonReadDownloadTask | null = null;

  async function startDownload(
    gen: number,
    book: CatalogBook,
    addonName: string,
    access: AddonAccessResolution,
    url: string,
  ): Promise<void> {
    state = { kind: 'Downloading', downloaded: 0, total: null };
    const handle = deps.download(book, url, (downloaded, total) => {
      if (gen !== generation) return;
      state = { kind: 'Downloading', downloaded, total };
    });
    task = handle;
    let outcome: AddonReadDownloadOutcome;
    try {
      outcome = await handle.promise;
    } catch {
      outcome = { kind: 'failed', code: 'UPSTREAM_ERROR' };
    }
    if (gen !== generation) return;
    task = null;
    if (outcome.kind === 'imported') {
      state = hasExternalOptions(access)
        ? { kind: 'Loaded', addonName, access }
        : { kind: 'Empty', addonName };
    } else if (outcome.kind === 'cancelled') {
      state = { kind: 'Hidden' };
    } else {
      state = { kind: 'Error', code: outcome.code };
    }
  }

  async function open(book: CatalogBook, addonId: string): Promise<void> {
    const gen = ++generation;
    current = { book, addonId };
    task?.cancel();
    task = null;
    state = { kind: 'Resolving' };
    let access: AddonAccessResolution;
    try {
      access = await deps.resolve(book);
    } catch (err) {
      if (gen !== generation) return;
      if (isCatalogError(err) && err.code === 'CONSENT_REQUIRED') {
        state = { kind: 'ConsentRequired', addonId, addonName: deps.addonNameOf(addonId) };
      } else {
        state = { kind: 'Error', code: isCatalogError(err) ? err.code : 'UPSTREAM_ERROR' };
      }
      return;
    }
    if (gen !== generation) return;
    const addonName = deps.addonNameOf(addonId);
    if (access.canDownloadInApp && access.downloadUrl !== null) {
      await startDownload(gen, book, addonName, access, access.downloadUrl);
      return;
    }
    state = hasExternalOptions(access)
      ? { kind: 'Loaded', addonName, access }
      : { kind: 'Empty', addonName };
  }

  function close(): void {
    generation += 1;
    task?.cancel();
    task = null;
    current = null;
    state = { kind: 'Hidden' };
  }

  async function retry(): Promise<void> {
    if (!current) return;
    await open(current.book, current.addonId);
  }

  async function allow(): Promise<void> {
    if (!current) return;
    await deps.consent.grant(current.addonId);
    await open(current.book, current.addonId);
  }

  async function deny(): Promise<void> {
    if (!current) return;
    await deps.consent.revoke(current.addonId);
    await open(current.book, current.addonId);
  }

  function cancelDownload(): void {
    task?.cancel();
  }

  return {
    get state() {
      return state;
    },
    open,
    close,
    retry,
    allow,
    deny,
    cancelDownload,
  };
}
