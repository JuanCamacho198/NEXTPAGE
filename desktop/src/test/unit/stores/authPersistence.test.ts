/**
 * Unit tests for `authPersistence`.
 *
 * Mocks `@tauri-apps/plugin-fs` with controllable mocks so we can simulate:
 *  - cache hits (round-trip Google + local)
 *  - missing files
 *  - malformed JSON
 *  - atomic write (write to .tmp, then rename)
 *  - clear behavior (and the stale .tmp cleanup)
 *
 * The platform call wrappers (`load/save/clearPersistedAuth`) must never
 * throw — they swallow IO errors and return `null`/`void` so the calling
 * UI can degrade gracefully.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { TokenSet } from '$lib/stores/authState.svelte';
import {
  clearPersistedAuth,
  loadPersistedAuth,
  savePersistedAuth,
  type LocalUserProfile,
  type PersistedAuth,
} from '$lib/stores/authPersistence';

const mockReadTextFile = vi.hoisted(() => vi.fn<(...args: unknown[]) => Promise<string>>());
const mockWriteTextFile = vi.hoisted(() => vi.fn<(...args: unknown[]) => Promise<void>>());
const mockRemove = vi.hoisted(() => vi.fn<(...args: unknown[]) => Promise<void>>());
const mockRename = vi.hoisted(() => vi.fn<(...args: unknown[]) => Promise<void>>());
const mockExists = vi.hoisted(() => vi.fn<(...args: unknown[]) => Promise<boolean>>());

vi.mock('@tauri-apps/plugin-fs', () => ({
  BaseDirectory: { AppData: 0 },
  exists: mockExists,
  readTextFile: mockReadTextFile,
  writeTextFile: mockWriteTextFile,
  remove: mockRemove,
  rename: mockRename,
}));

const googleTokens: TokenSet = {
  accessToken: 'access-123',
  refreshToken: 'refresh-123',
  idToken: 'id-123',
  expiresIn: 3600,
};

const localProfile: LocalUserProfile = {
  name: 'Dev',
  email: 'dev@local',
  avatarUrl: null,
  localOnly: true,
};

const googleAuth = { kind: 'google', tokens: googleTokens } as unknown as PersistedAuth;
const localAuth: PersistedAuth = { kind: 'local', profile: localProfile };

beforeEach(() => {
  mockReadTextFile.mockReset();
  mockWriteTextFile.mockReset();
  mockRemove.mockReset();
  mockRename.mockReset();
  mockExists.mockReset();
});

describe('loadPersistedAuth', () => {
  it('returns null when the cache file does not exist', async () => {
    mockExists.mockResolvedValue(false);
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
    expect(mockReadTextFile).not.toHaveBeenCalled();
  });

  it('returns null when the cache contains a legacy Google record (discarded per MG-01)', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue(JSON.stringify(googleAuth));
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });

  it('returns a parsed local auth when the cache contains a valid local record', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue(JSON.stringify(localAuth));
    const result = await loadPersistedAuth();
    expect(result).toEqual(localAuth);
  });

  it('returns null when the JSON is malformed (and does not throw)', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue('{not valid json');
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });

  it('returns null when the JSON is structurally valid but the discriminator is unknown', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue(JSON.stringify({ kind: 'magic-link', payload: {} }));
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });

  it('returns null when the Supabase record has a non-object session field', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue(
      JSON.stringify({ kind: 'supabase', session: 'not-an-object' }),
    );
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });

  it('returns null when the local profile has an empty name', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue(
      JSON.stringify({
        kind: 'local',
        profile: { name: '', email: null, avatarUrl: null, localOnly: true },
      }),
    );
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });

  it('returns null when the local profile is missing the localOnly literal', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockResolvedValue(
      JSON.stringify({
        kind: 'local',
        profile: { name: 'Dev', email: null, avatarUrl: null },
      }),
    );
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });

  it('returns null when the platform read throws (no crash propagates)', async () => {
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockRejectedValue(new Error('disk gone'));
    const result = await loadPersistedAuth();
    expect(result).toBeNull();
  });
});

describe('savePersistedAuth', () => {
  it('writes Supabase auth to the tmp file, then renames over the real file (atomic write)', async () => {
    mockWriteTextFile.mockResolvedValue();
    mockRename.mockResolvedValue();
    const supabaseAuth: PersistedAuth = {
      kind: 'supabase',
      session: { access_token: 'test' },
    };

    await savePersistedAuth(supabaseAuth);

    expect(mockWriteTextFile).toHaveBeenCalledTimes(1);
    expect(mockWriteTextFile).toHaveBeenCalledWith('auth.json.tmp', JSON.stringify(supabaseAuth), {
      baseDir: 0,
    });
    expect(mockRename).toHaveBeenCalledTimes(1);
    expect(mockRename).toHaveBeenCalledWith('auth.json.tmp', 'auth.json', {
      oldPathBaseDir: 0,
      newPathBaseDir: 0,
    });
  });

  it('writes local auth to the tmp file, then renames over the real file', async () => {
    mockWriteTextFile.mockResolvedValue();
    mockRename.mockResolvedValue();

    await savePersistedAuth(localAuth);

    expect(mockWriteTextFile).toHaveBeenCalledWith('auth.json.tmp', JSON.stringify(localAuth), {
      baseDir: 0,
    });
    expect(mockRename).toHaveBeenCalledWith('auth.json.tmp', 'auth.json', {
      oldPathBaseDir: 0,
      newPathBaseDir: 0,
    });
  });

  it('does not rename if the write fails (no half-written real file)', async () => {
    mockWriteTextFile.mockRejectedValue(new Error('disk full'));
    const supabaseAuth: PersistedAuth = {
      kind: 'supabase',
      session: { access_token: 'test' },
    };

    await expect(savePersistedAuth(supabaseAuth)).rejects.toThrow('disk full');
    expect(mockRename).not.toHaveBeenCalled();
  });
});

describe('clearPersistedAuth', () => {
  it('removes the cache file when it exists', async () => {
    mockExists.mockImplementation(((path: unknown) => Promise.resolve(path === 'auth.json')) as (
      ...args: unknown[]
    ) => Promise<boolean>);
    mockRemove.mockResolvedValue();

    await clearPersistedAuth();

    expect(mockRemove).toHaveBeenCalledWith('auth.json', { baseDir: 0 });
  });

  it('does not call remove when the cache file is missing', async () => {
    mockExists.mockResolvedValue(false);

    await clearPersistedAuth();

    expect(mockRemove).not.toHaveBeenCalled();
  });

  it('removes a stale .tmp file even when the main file is missing', async () => {
    mockExists.mockImplementation(((path: unknown) =>
      Promise.resolve(path === 'auth.json.tmp')) as (...args: unknown[]) => Promise<boolean>);
    mockRemove.mockResolvedValue();

    await clearPersistedAuth();

    expect(mockRemove).toHaveBeenCalledWith('auth.json.tmp', { baseDir: 0 });
  });

  it('swallows platform errors and does not throw', async () => {
    mockExists.mockResolvedValue(true);
    mockRemove.mockRejectedValue(new Error('permission denied'));

    await expect(clearPersistedAuth()).resolves.toBeUndefined();
  });
});

describe('round-trip persistence', () => {
  it('returns the same Supabase auth that was saved', async () => {
    let storedPayload: string | null = null;
    const supabaseAuth: PersistedAuth = {
      kind: 'supabase',
      session: { access_token: 'test', refresh_token: 'test-refresh' },
    };

    mockWriteTextFile.mockImplementation(((_path: unknown, data: unknown) => {
      storedPayload = data as string;
      return Promise.resolve();
    }) as (...args: unknown[]) => Promise<void>);
    mockRename.mockResolvedValue();
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockImplementation((() => {
      if (storedPayload === null) {
        return Promise.reject(new Error('not yet written'));
      }
      return Promise.resolve(storedPayload);
    }) as (...args: unknown[]) => Promise<string>);

    await savePersistedAuth(supabaseAuth);
    const loaded = await loadPersistedAuth();
    expect(loaded).toEqual(supabaseAuth);
  });

  it('returns the same local auth that was saved', async () => {
    let storedPayload: string | null = null;

    mockWriteTextFile.mockImplementation(((_path: unknown, data: unknown) => {
      storedPayload = data as string;
      return Promise.resolve();
    }) as (...args: unknown[]) => Promise<void>);
    mockRename.mockResolvedValue();
    mockExists.mockResolvedValue(true);
    mockReadTextFile.mockImplementation((() => {
      if (storedPayload === null) {
        return Promise.reject(new Error('not yet written'));
      }
      return Promise.resolve(storedPayload);
    }) as (...args: unknown[]) => Promise<string>);

    await savePersistedAuth(localAuth);
    const loaded = await loadPersistedAuth();
    expect(loaded).toEqual(localAuth);
  });
});
