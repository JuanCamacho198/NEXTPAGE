/**
 * Unit tests for `drivePersistence` (login-drive-separation, PR1 foundation).
 *
 * Covers: round-trip save/load, missing file → null, corrupt file → null,
 * atomic write (tmp + rename, no tmp leftover), and the one-shot
 * `migrateLoginGrantOnce()` seeder (copies the auth.json login grant once,
 * then skips; never overwrites; skips when there is no login grant).
 */

import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  clearDriveGrant,
  loadDriveGrant,
  migrateLoginGrantOnce,
  saveDriveGrant,
  type DriveGrant,
} from '$lib/shared/stores/drivePersistence';

const files = vi.hoisted(() => new Map<string, string>());

vi.mock('@tauri-apps/plugin-fs', () => ({
  BaseDirectory: { AppData: 0 },
  exists: vi.fn(async (path: unknown) => files.has(path as string)),
  readTextFile: vi.fn(async (path: unknown) => {
    const content = files.get(path as string);
    if (content === undefined) throw new Error('not found');
    return content;
  }),
  writeTextFile: vi.fn(async (path: unknown, data: unknown) => {
    files.set(path as string, data as string);
  }),
  remove: vi.fn(async (path: unknown) => {
    files.delete(path as string);
  }),
  rename: vi.fn(async (oldPath: unknown, newPath: unknown) => {
    const content = files.get(oldPath as string);
    if (content === undefined) throw new Error('rename source missing');
    files.delete(oldPath as string);
    files.set(newPath as string, content);
  }),
}));

const grant: DriveGrant = {
  refreshToken: 'drive-refresh-1',
  scope: 'https://www.googleapis.com/auth/drive.file',
  obtainedAt: 1700000000000,
};

// Seeds the fake fs `auth.json` with a pre-separation login-coupled grant.
// The seeder owns migration (work unit 4): it reads auth.json directly,
// `authPersistence.ts` no longer exposes Drive-token helpers.
function seedLoginGrant(token: string): void {
  files.set(
    'auth.json',
    JSON.stringify({
      kind: 'supabase',
      session: { access_token: 'x', provider_refresh_token: token },
    }),
  );
}

beforeEach(() => {
  files.clear();
});

describe('loadDriveGrant', () => {
  it('returns null when drive.json does not exist', async () => {
    await expect(loadDriveGrant()).resolves.toBeNull();
  });

  it('returns null for malformed JSON (no crash)', async () => {
    files.set('drive.json', '{not valid json');
    await expect(loadDriveGrant()).resolves.toBeNull();
  });

  it('returns null for structurally valid but invalid grants', async () => {
    files.set('drive.json', JSON.stringify({ kind: 'supabase', session: {} }));
    await expect(loadDriveGrant()).resolves.toBeNull();

    files.set('drive.json', JSON.stringify({ refreshToken: '', scope: 's', obtainedAt: 1 }));
    await expect(loadDriveGrant()).resolves.toBeNull();

    files.set('drive.json', JSON.stringify({ refreshToken: 'r', scope: 's' }));
    await expect(loadDriveGrant()).resolves.toBeNull();
  });
});

describe('saveDriveGrant / loadDriveGrant round-trip', () => {
  it('returns the same grant that was saved', async () => {
    await saveDriveGrant(grant);
    await expect(loadDriveGrant()).resolves.toEqual(grant);
  });

  it('writes atomically via the tmp file (no tmp leftover)', async () => {
    await saveDriveGrant(grant);
    expect(files.has('drive.json.tmp')).toBe(false);
    expect(files.has('drive.json')).toBe(true);
  });
});

describe('clearDriveGrant', () => {
  it('removes the stored grant; load returns null afterwards', async () => {
    await saveDriveGrant(grant);
    await clearDriveGrant();
    await expect(loadDriveGrant()).resolves.toBeNull();
  });

  it('never throws when nothing is stored', async () => {
    await expect(clearDriveGrant()).resolves.toBeUndefined();
  });
});

describe('migrateLoginGrantOnce', () => {
  it('seeds drive.json from the auth.json login grant, then skips', async () => {
    seedLoginGrant('login-coupled-refresh');

    await expect(migrateLoginGrantOnce()).resolves.toBe('seeded');

    const seeded = await loadDriveGrant();
    expect(seeded?.refreshToken).toBe('login-coupled-refresh');
    const firstObtainedAt = seeded?.obtainedAt;

    await expect(migrateLoginGrantOnce()).resolves.toBe('skipped');
    await expect(loadDriveGrant()).resolves.toEqual(seeded);
    expect((await loadDriveGrant())?.obtainedAt).toBe(firstObtainedAt);
  });

  it('never overwrites an existing drive.json grant', async () => {
    await saveDriveGrant(grant);
    seedLoginGrant('login-coupled-refresh');

    await expect(migrateLoginGrantOnce()).resolves.toBe('skipped');
    await expect(loadDriveGrant()).resolves.toEqual(grant);
  });

  it('skips when there is no login grant and stores nothing', async () => {
    await expect(migrateLoginGrantOnce()).resolves.toBe('skipped');
    expect(files.has('drive.json')).toBe(false);
  });

  it('skips corrupt auth.json without crashing (degrades to one re-connect)', async () => {
    files.set('auth.json', '{not valid json');

    await expect(migrateLoginGrantOnce()).resolves.toBe('skipped');
    expect(files.has('drive.json')).toBe(false);
  });
});
