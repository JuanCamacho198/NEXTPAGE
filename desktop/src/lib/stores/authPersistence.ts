/**
 * Persisted authentication cache.
 *
 * Stores auth state to `appDataDir/auth.json` so returning users can skip the
 * welcome screen. Writes are atomic (tmp file + rename).
 *
 * Discriminated union on disk:
 *   { kind: "supabase", session: Record<string, unknown> }
 *   { kind: "local",    profile: LocalUserProfile }
 *   { kind: "google",   tokens: TokenSet }  // legacy, discarded on read
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
  | { kind: 'supabase'; session: Record<string, unknown> }
  | { kind: 'local'; profile: LocalUserProfile };

/**
 * Load the persisted auth cache. Returns `null` when:
 *  - the file does not exist
 *  - the file contains malformed JSON
 *  - the file's `kind` is unknown or legacy ('google' — discarded per MG-01)
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
 */
export async function savePersistedAuth(auth: PersistedAuth): Promise<void> {
  const payload = JSON.stringify(auth);
  await writeTextFile(TMP_FILE, payload, { baseDir: BASE_DIR });
  await rename(TMP_FILE, CACHE_FILE, {
    oldPathBaseDir: BASE_DIR,
    newPathBaseDir: BASE_DIR,
  });
}

/**
 * Remove the persisted auth cache. Best-effort.
 */
export async function clearPersistedAuth(): Promise<void> {
  try {
    const fileExists = await exists(CACHE_FILE, { baseDir: BASE_DIR });
    if (!fileExists) {
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

  // Legacy 'google' kind — discard per MG-01
  if (value.kind === 'google') {
    return null;
  }

  if (value.kind === 'supabase') {
    const session = value.session;
    if (!isObject(session)) {
      return null;
    }
    // We don't validate session fields deeply — supabase-js handles that.
    return { kind: 'supabase', session: session as Record<string, unknown> };
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
