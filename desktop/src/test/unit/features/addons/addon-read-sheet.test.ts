/**
 * Addon read sheet (slice 9, tasks 9.7–9.9, 9.12): the exact 7-state machine
 * over injected ports plus the presentational component.
 *
 * `Hidden` by default (composes nothing); `Resolving` then `Loaded`;
 * `Empty` on zero usable options; `Error` with retry; `ConsentRequired`
 * with no network I/O (allow grants + re-resolves, deny revokes +
 * re-resolves); `Downloading` reflects the backend transfer's byte progress
 * (the fake IS the single-machine stand-in: progress arrives only through
 * `onProgress`, the terminal outcome only through the task promise) and maps
 * `imported` → `Loaded`/`Empty`, `cancelled` → `Hidden`, `failed` → `Error`.
 * The REAL `AddonReadSheet` renders every state; the screen wiring is pinned
 * by source-read assertions (the screen itself is never rendered: its
 * `$effect` calls the Tauri-backed singleton refresh, which needs invoke).
 */
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { readFileSync } from 'node:fs';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';

import {
  createAddonReadSheet,
  type AddonReadDownloadOutcome,
  type AddonReadDownloadTask,
  type AddonReadState,
} from '$lib/features/addons/useAddonReadSheet.svelte';
import AddonReadSheet from '$lib/features/addons/components/AddonReadSheet.svelte';
import { CatalogError } from '$lib/shared/services/catalog/errors';
import type { CatalogBook } from '$lib/shared/services/catalog/CatalogProvider';
import type { AddonAccessResolution } from '$lib/shared/services/addons/AddonCatalogProvider';
import type { MessageKey } from '$lib/shared/i18n';

const here = dirname(fileURLToPath(import.meta.url));
const readSource = (rel: string): string => readFileSync(resolve(here, rel), 'utf8');

const t = (key: MessageKey): string => key;

const ADDON_ID = 'a1b2c3d4e5f60718';

const BOOK: CatalogBook = {
  id: `addon:${ADDON_ID}:book-1`,
  provider: `addon:${ADDON_ID}`,
  title: 'Dune',
  authors: ['Herbert'],
  coverUrl: null,
  languages: ['en'],
  subjects: ['sci-fi'],
  downloadUrl: null,
};

const OPTIONS_ACCESS: AddonAccessResolution = {
  canDownloadInApp: false,
  downloadUrl: null,
  options: [
    {
      group: 'BUY',
      titleKey: 'discover.accessOpen',
      url: 'https://shop.example/buy/1',
      opensInApp: false,
    },
  ],
};

const EMPTY_ACCESS: AddonAccessResolution = {
  canDownloadInApp: false,
  downloadUrl: null,
  options: [],
};

const DOWNLOAD_ACCESS: AddonAccessResolution = {
  canDownloadInApp: true,
  downloadUrl: 'https://space.example/dl/1.epub',
  options: OPTIONS_ACCESS.options,
};

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

function sheetHarness(
  overrides: {
    resolve?: (book: CatalogBook) => Promise<AddonAccessResolution>;
    onGrant?: (id: string) => void;
    onRevoke?: (id: string) => void;
  } = {},
): {
  sheet: ReturnType<typeof createAddonReadSheet>;
  calls: { resolve: number; download: number; cancel: number; grant: string[]; revoke: string[] };
  progress: { fn: ((downloaded: number, total: number | null) => void) | null };
  finishDownload: (outcome: AddonReadDownloadOutcome) => void;
} {
  const calls = {
    resolve: 0,
    download: 0,
    cancel: 0,
    grant: [] as string[],
    revoke: [] as string[],
  };
  const progress: {
    fn: ((downloaded: number, total: number | null) => void) | null;
  } = { fn: null };
  let finish: ((outcome: AddonReadDownloadOutcome) => void) | null = null;
  const sheet = createAddonReadSheet({
    consent: {
      grant: (id: string) => {
        calls.grant.push(id);
        overrides.onGrant?.(id);
        return Promise.resolve();
      },
      revoke: (id: string) => {
        calls.revoke.push(id);
        overrides.onRevoke?.(id);
        return Promise.resolve();
      },
    },
    addonNameOf: (id: string) => `Addon ${id}`,
    resolve: (book) => {
      calls.resolve += 1;
      return overrides.resolve ? overrides.resolve(book) : Promise.resolve(OPTIONS_ACCESS);
    },
    download: (_book, _url, onProgress): AddonReadDownloadTask => {
      calls.download += 1;
      progress.fn = onProgress;
      const gate = deferred<AddonReadDownloadOutcome>();
      finish = gate.resolve;
      return {
        promise: gate.promise,
        cancel: () => {
          calls.cancel += 1;
        },
      };
    },
  });
  return {
    sheet,
    calls,
    progress,
    finishDownload: (outcome) => finish?.(outcome),
  };
}

const noop = (): void => undefined;

function renderSheet(state: AddonReadState): ReturnType<typeof render> {
  return render(AddonReadSheet, {
    t,
    state,
    onClose: noop,
    onRetry: noop,
    onAllow: noop,
    onDeny: noop,
    onCancel: noop,
  } as Record<string, unknown>);
}

describe('read-sheet state machine (real hook, injected ports)', () => {
  it('Hidden by default and composes nothing', () => {
    const { sheet } = sheetHarness();
    expect(sheet.state).toEqual({ kind: 'Hidden' });

    const { container } = renderSheet(sheet.state);
    expect(container.textContent).toBe('');
  });

  it('Resolving then Loaded when the resolution has usable options', async () => {
    const { sheet, calls } = sheetHarness();

    const pending = sheet.open(BOOK, ADDON_ID);
    expect(sheet.state).toEqual({ kind: 'Resolving' });
    await pending;

    expect(sheet.state.kind).toBe('Loaded');
    expect(sheet.state).toMatchObject({ addonName: `Addon ${ADDON_ID}` });
    expect(calls).toMatchObject({ resolve: 1, download: 0 });
  });

  it('Empty on zero usable options', async () => {
    const { sheet } = sheetHarness({ resolve: () => Promise.resolve(EMPTY_ACCESS) });

    await sheet.open(BOOK, ADDON_ID);

    expect(sheet.state).toEqual({ kind: 'Empty', addonName: `Addon ${ADDON_ID}` });
  });

  it('Error with retry: a failed resolve retries into Loaded', async () => {
    let attempts = 0;
    const { sheet, calls } = sheetHarness({
      resolve: () => {
        attempts += 1;
        return attempts === 1
          ? Promise.reject(new CatalogError('UPSTREAM_ERROR', 'boom'))
          : Promise.resolve(OPTIONS_ACCESS);
      },
    });

    await sheet.open(BOOK, ADDON_ID);
    expect(sheet.state).toEqual({ kind: 'Error', code: 'UPSTREAM_ERROR' });

    await sheet.retry();
    expect(sheet.state.kind).toBe('Loaded');
    expect(calls.resolve).toBe(2);
  });

  it('a non-catalog resolve failure maps to UPSTREAM_ERROR', async () => {
    const { sheet } = sheetHarness({ resolve: () => Promise.reject(new Error('boom')) });

    await sheet.open(BOOK, ADDON_ID);
    expect(sheet.state).toEqual({ kind: 'Error', code: 'UPSTREAM_ERROR' });
  });

  it('ConsentRequired performs no network I/O; allow grants + re-resolves', async () => {
    let consented = false;
    const { sheet, calls } = sheetHarness({
      resolve: () =>
        consented
          ? Promise.resolve(OPTIONS_ACCESS)
          : Promise.reject(new CatalogError('CONSENT_REQUIRED', 'denied')),
      onGrant: () => {
        consented = true;
      },
    });

    await sheet.open(BOOK, ADDON_ID);
    expect(sheet.state).toEqual({
      kind: 'ConsentRequired',
      addonId: ADDON_ID,
      addonName: `Addon ${ADDON_ID}`,
    });
    // The gate fired before any fetch: resolve ran once, the download never started.
    expect(calls).toMatchObject({ resolve: 1, download: 0 });

    // Allowing grants durably, then re-resolves into Loaded.
    await sheet.allow();
    expect(calls.grant).toEqual([ADDON_ID]);
    expect(sheet.state.kind).toBe('Loaded');
  });

  it('deny revokes then re-resolves (still denied ⇒ ConsentRequired again)', async () => {
    const { sheet, calls } = sheetHarness({
      resolve: () => Promise.reject(new CatalogError('CONSENT_REQUIRED', 'denied')),
    });

    await sheet.open(BOOK, ADDON_ID);
    await sheet.deny();

    expect(calls.revoke).toEqual([ADDON_ID]);
    expect(calls.resolve).toBe(2);
    expect(sheet.state.kind).toBe('ConsentRequired');
    expect(calls.download).toBe(0);
  });

  it('Downloading reflects the backend transfer byte progress, then Loaded', async () => {
    const { sheet, calls, progress, finishDownload } = sheetHarness({
      resolve: () => Promise.resolve(DOWNLOAD_ACCESS),
    });

    const pending = sheet.open(BOOK, ADDON_ID);
    await vi.waitFor(() => expect(sheet.state.kind).toBe('Downloading'));
    expect(sheet.state).toEqual({ kind: 'Downloading', downloaded: 0, total: null });

    progress.fn?.(512, 1024);
    expect(sheet.state).toEqual({ kind: 'Downloading', downloaded: 512, total: 1024 });

    progress.fn?.(1024, 1024);
    finishDownload({ kind: 'imported' });
    await pending;

    expect(sheet.state.kind).toBe('Loaded');
    expect(calls.download).toBe(1);
  });

  it('imported with zero usable options lands on Empty', async () => {
    const { sheet, progress, finishDownload } = sheetHarness({
      resolve: () =>
        Promise.resolve({
          canDownloadInApp: true,
          downloadUrl: DOWNLOAD_ACCESS.downloadUrl,
          options: [],
        }),
    });

    const pending = sheet.open(BOOK, ADDON_ID);
    await vi.waitFor(() => expect(sheet.state.kind).toBe('Downloading'));
    expect(sheet.state.kind).toBe('Downloading');

    progress.fn?.(64, null);
    finishDownload({ kind: 'imported' });
    await pending;

    expect(sheet.state).toEqual({ kind: 'Empty', addonName: `Addon ${ADDON_ID}` });
  });

  it('cancelled maps back to Hidden; failed maps to Error', async () => {
    const first = sheetHarness({ resolve: () => Promise.resolve(DOWNLOAD_ACCESS) });
    const pendingCancel = first.sheet.open(BOOK, ADDON_ID);
    await vi.waitFor(() => expect(first.sheet.state.kind).toBe('Downloading'));
    expect(first.sheet.state.kind).toBe('Downloading');
    first.finishDownload({ kind: 'cancelled' });
    await pendingCancel;
    expect(first.sheet.state).toEqual({ kind: 'Hidden' });

    const second = sheetHarness({ resolve: () => Promise.resolve(DOWNLOAD_ACCESS) });
    const pendingFail = second.sheet.open(BOOK, ADDON_ID);
    await vi.waitFor(() => expect(second.sheet.state.kind).toBe('Downloading'));
    second.finishDownload({ kind: 'failed', code: 'UPSTREAM_ERROR' });
    await pendingFail;
    expect(second.sheet.state).toEqual({ kind: 'Error', code: 'UPSTREAM_ERROR' });
  });

  it('close cancels the in-flight task, returns Hidden, and drops the late settle', async () => {
    const { sheet, calls, finishDownload } = sheetHarness({
      resolve: () => Promise.resolve(DOWNLOAD_ACCESS),
    });

    const pending = sheet.open(BOOK, ADDON_ID);
    await vi.waitFor(() => expect(sheet.state.kind).toBe('Downloading'));
    expect(sheet.state.kind).toBe('Downloading');

    sheet.close();
    expect(sheet.state).toEqual({ kind: 'Hidden' });
    expect(calls.cancel).toBe(1);

    // The backend settles after the close: the generation guard drops it.
    finishDownload({ kind: 'imported' });
    await pending;
    expect(sheet.state).toEqual({ kind: 'Hidden' });
  });

  it('cancelDownload aborts the backend transfer without closing first', async () => {
    const { sheet, calls, finishDownload } = sheetHarness({
      resolve: () => Promise.resolve(DOWNLOAD_ACCESS),
    });

    const pending = sheet.open(BOOK, ADDON_ID);
    await vi.waitFor(() => expect(sheet.state.kind).toBe('Downloading'));
    sheet.cancelDownload();
    expect(calls.cancel).toBe(1);

    finishDownload({ kind: 'cancelled' });
    await pending;
    expect(sheet.state).toEqual({ kind: 'Hidden' });
  });

  it('a stale resolve settle never lands (rapid re-open keeps the latest)', async () => {
    const first = deferred<AddonAccessResolution>();
    const { sheet } = sheetHarness({ resolve: () => first.promise });

    const pendingFirst = sheet.open(BOOK, ADDON_ID);
    const pendingSecond = sheet.open(BOOK, ADDON_ID);
    first.resolve(OPTIONS_ACCESS);
    await Promise.all([pendingFirst, pendingSecond]);

    expect(sheet.state.kind).toBe('Loaded');
  });

  it('retry/allow/deny with nothing open are harmless no-ops', async () => {
    const { sheet, calls } = sheetHarness();
    await sheet.retry();
    await sheet.allow();
    await sheet.deny();
    expect(sheet.state).toEqual({ kind: 'Hidden' });
    expect(calls).toMatchObject({ resolve: 0, grant: [], revoke: [] });
  });
});

describe('read-sheet component (real component, every state)', () => {
  it('Hidden renders nothing', () => {
    const { container } = renderSheet({ kind: 'Hidden' });
    expect(container.textContent).toBe('');
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });

  it('Resolving shows the resolving copy', () => {
    const { container } = renderSheet({ kind: 'Resolving' });
    expect(container.textContent).toContain('addons.readSheet.resolving');
  });

  it('Downloading shows progress bytes and a cancel action', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(AddonReadSheet, {
      t,
      state: { kind: 'Downloading', downloaded: 512, total: 1024 },
      onClose: noop,
      onRetry: noop,
      onAllow: noop,
      onDeny: noop,
      onCancel,
    } as Record<string, unknown>);

    expect(screen.getByText('addons.readSheet.downloading')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'discover.downloadCancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('Loaded shows the addon name, the legal notice, and the grouped options', () => {
    const { container } = renderSheet({
      kind: 'Loaded',
      addonName: 'Space Books',
      access: OPTIONS_ACCESS,
    });
    expect(container.textContent).toContain('Space Books');
    expect(container.textContent).toContain('addons.readSheet.legalNotice');
    expect(container.textContent).toContain('discover.accessGroupBuy');
    const link = container.querySelector('a[href="https://shop.example/buy/1"]');
    expect(link?.textContent).toContain('discover.accessOpen');
  });

  it('Empty shows the empty title and body', () => {
    const { container } = renderSheet({ kind: 'Empty', addonName: 'Space Books' });
    expect(container.textContent).toContain('addons.readSheet.empty.title');
    expect(container.textContent).toContain('addons.readSheet.empty.body');
  });

  it('Error shows the error copy and retry calls onRetry', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(AddonReadSheet, {
      t,
      state: { kind: 'Error', code: 'UPSTREAM_ERROR' },
      onClose: noop,
      onRetry,
      onAllow: noop,
      onDeny: noop,
      onCancel: noop,
    } as Record<string, unknown>);

    expect(screen.getByText('addons.readSheet.error')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'discover.retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('ConsentRequired reuses the informed-consent copy with working allow/deny', async () => {
    const user = userEvent.setup();
    const onAllow = vi.fn();
    const onDeny = vi.fn();
    render(AddonReadSheet, {
      t,
      state: { kind: 'ConsentRequired', addonId: ADDON_ID, addonName: 'Space Books' },
      onClose: noop,
      onRetry: noop,
      onAllow,
      onDeny,
      onCancel: noop,
    } as Record<string, unknown>);

    expect(screen.getByText('addons.consent.title')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'addons.consent.allow' }));
    expect(onAllow).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('button', { name: 'addons.consent.deny' }));
    expect(onDeny).toHaveBeenCalledTimes(1);
  });

  it('Loaded and Empty dismiss calls onClose', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const { unmount } = render(AddonReadSheet, {
      t,
      state: { kind: 'Empty', addonName: 'Space Books' },
      onClose,
      onRetry: noop,
      onAllow: noop,
      onDeny: noop,
      onCancel: noop,
    } as Record<string, unknown>);

    await user.click(screen.getByRole('button', { name: 'discover.dismiss' }));
    expect(onClose).toHaveBeenCalledTimes(1);
    unmount();
  });
});

describe('addons screen hosts the sheet + badges (source-read pin)', () => {
  it('the screen creates the sheet with production ports and fills the badges slot', () => {
    const source = readSource('../../../../lib/features/addons/AddonsScreen.svelte');
    expect(source).toContain('createAddonReadSheet');
    expect(source).toContain('addonSourceIdOf(book.provider)');
    expect(source).toContain('liveCatalogProvider.resolveAddonAccess');
    expect(source).toContain('discoverState.startDownloadUrl(book, url)');
    expect(source).toContain('discoverState.cancelDownload()');
    expect(source).toContain('<AddonReadSheet');
    expect(source).toContain('state={readSheet.state}');
    expect(source).toContain('{#snippet capabilityBadges(addon)}');
    expect(source).toContain('declaredCapabilities(addon.manifest)');
    expect(source).toContain('<AddonCapabilityBadges');
  });
});
