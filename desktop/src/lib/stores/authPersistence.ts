/**
 * Persisted authentication cache.
 *
 * Stores the most recent Google OAuth `TokenSet` or a local-only user profile
 * to `appDataDir/auth.json` so returning users can skip the welcome screen.
 * Writes are atomic (tmp file + rename) to prevent partial-write corruption
 * if the process is interrupted.
 *
 * The on-disk format is a discriminated union:
 *
 *   { kind: "google", tokens: TokenSet }
 *   { kind: "local",  profile: LocalUserProfile }
 *
 * No tokens, secrets, or PII are ever logged. Malformed JSON is treated as
 * "no cache" and a warning is emitted; the app falls back to the welcome
 * screen in that case.
 */

import {
  BaseDirectory,
  exists,
  readTextFile,
  remove,
  rename,
  writeTextFile,
} from '@tauri-apps/plugin-fs';
import { logger } from '$lib/shared/logger/Logger';
import { createErrorEvent } from '$lib/shared/events/ErrorEvent';
import type { TokenSet } from './authState.svelte';

const CACHE_FILE = 'auth.json';
const TMP_FILE = 'auth.json.tmp';
const BASE_DIR = BaseDirectory.AppData;

export type LocalUserProfile = {
  name: string;
  email: string | null;
  avatarUrl: string | null;
  localOnly: true;
};

export type PersistedAuth =
  | { kind: 'google'; tokens: TokenSet }
  | { kind: 'local'; profile: LocalUserProfile };

/**
 * Load the persisted auth cache. Returns `null` when:
 *  - the file does not exist (first launch, never signed in)
 *  - the file contains malformed JSON
 *  - the file's `kind` discriminator is unknown
 *  - the platform layer (Tauri) is unavailable (e.g. during unit tests)
 *
 * Any read or parse error is logged at `low` severity and treated as a cache
 * miss; the caller does not need to handle exceptions.
 */
export async function loadPersistedAuth(): Promise<PersistedAuth | null> {
  try {
    const fileExists = await exists(CACHE_FILE, { baseDir: BASE_DIR });
    if (!fileExists) {
      return null;
    }

    const raw = await readTextFile(CACHE_FILE, { baseDir: BASE_DIR });
    const parsed = JSON.parse(raw) as unknown;
    return validatePersistedAuth(parsed);
  } catch (error) {
    logger.warn(
      createErrorEvent({
        severity: 'low',
        category: 'runtime',
        code: 'AUTH_CACHE_READ_FAILED',
        message: 'Failed to read persisted auth cache; treating as no cache.',
        context: { reason: error instanceof Error ? error.message : String(error) },
        source: 'app_shell',
        recoverable: true,
      }),
    );
    return null;
  }
}

/**
 * Persist the given auth record atomically.
 *
 * Implementation: write to `auth.json.tmp` first, then `rename` over the
 * real file. The `rename` is atomic on the same filesystem, so a crash
 * mid-write cannot leave a partially-written `auth.json` — the previous
 * valid cache (or no cache) survives.
 */
export async function savePersistedAuth(auth: PersistedAuth): Promise<void> {
  const payload = JSON.stringify(auth);
  await writeTextFile(TMP_FILE, payload, { baseDir: BASE_DIR });
  // `rename` in @tauri-apps/plugin-fs overwrites the destination if it exists.
  await rename(TMP_FILE, CACHE_FILE, {
    oldPathBaseDir: BASE_DIR,
    newPathBaseDir: BASE_DIR,
  });
}

/**
 * Remove the persisted auth cache. Best-effort: a missing file is not an
 * error. Failures are logged but never thrown.
 */
export async function clearPersistedAuth(): Promise<void> {
  try {
    const fileExists = await exists(CACHE_FILE, { baseDir: BASE_DIR });
    if (!fileExists) {
      // Also try to clean up a stale .tmp file
      const tmpExists = await exists(TMP_FILE, { baseDir: BASE_DIR });
      if (tmpExists) {
        await remove(TMP_FILE, { baseDir: BASE_DIR });
      }
      return;
    }
    await remove(CACHE_FILE, { baseDir: BASE_DIR });
  } catch (error) {
    logger.warn(
      createErrorEvent({
        severity: 'low',
        category: 'runtime',
        code: 'AUTH_CACHE_CLEAR_FAILED',
        message: 'Failed to clear persisted auth cache.',
        context: { reason: error instanceof Error ? error.message : String(error) },
        source: 'app_shell',
        recoverable: true,
      }),
    );
  }
}

// ─── Internal validation ───

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStringOrNull(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function validatePersistedAuth(value: unknown): PersistedAuth | null {
  if (!isObject(value)) {
    return null;
  }

  if (value.kind === 'google') {
    const tokens = value.tokens;
    if (!isObject(tokens)) {
      return null;
    }
    if (
      typeof tokens.accessToken !== 'string' ||
      typeof tokens.refreshToken !== 'string' ||
      typeof tokens.idToken !== 'string' ||
      typeof tokens.expiresIn !== 'number' ||
      !Number.isFinite(tokens.expiresIn)
    ) {
      return null;
    }
    return { kind: 'google', tokens: tokens as unknown as TokenSet };
  }

  if (value.kind === 'local') {
    const profile = value.profile;
    if (!isObject(profile)) {
      return null;
    }
    if (
      typeof profile.name !== 'string' ||
      profile.name.trim().length === 0 ||
      !isStringOrNull(profile.email) ||
      !isStringOrNull(profile.avatarUrl) ||
      profile.localOnly !== true
    ) {
      return null;
    }
    return { kind: 'local', profile: profile as unknown as LocalUserProfile };
  }

  return null;
}
