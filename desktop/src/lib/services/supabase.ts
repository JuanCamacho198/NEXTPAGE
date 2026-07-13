/**
 * Supabase client factory with:
 * - Anonymous client (legacy, unauthenticated)
 * - Session-aware client using Tauri fs-based storage adapter
 * - AuthStorageAdapter for persisting Supabase session to disk
 */

import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import { BaseDirectory, writeTextFile, readTextFile, exists } from '@tauri-apps/plugin-fs';

const SESSION_FILE = 'supabase-session.json';
const BASE_DIR = BaseDirectory.AppData;

// ─── Session Storage Adapter ──────────────────────────────────────
// supabase-js normally uses localStorage. Tauri webview's localStorage
// is ephemeral (cleared on app restart). This adapter persists the
// session to a JSON file in appDataDir so it survives restarts.
const tauriStorageAdapter = {
  getItem: async (key: string): Promise<string | null> => {
    try {
      const fileExists = await exists(SESSION_FILE, { baseDir: BASE_DIR });
      if (!fileExists) return null;
      const raw = await readTextFile(SESSION_FILE, { baseDir: BASE_DIR });
      const data = JSON.parse(raw) as Record<string, string>;
      return data[key] ?? null;
    } catch {
      return null;
    }
  },
  setItem: async (key: string, value: string): Promise<void> => {
    try {
      let data: Record<string, string> = {};
      const fileExists = await exists(SESSION_FILE, { baseDir: BASE_DIR });
      if (fileExists) {
        try {
          const raw = await readTextFile(SESSION_FILE, { baseDir: BASE_DIR });
          data = JSON.parse(raw) as Record<string, string>;
        } catch {
          // corrupted file, start fresh
        }
      }
      data[key] = value;
      await writeTextFile(SESSION_FILE, JSON.stringify(data, null, 0), { baseDir: BASE_DIR });
    } catch (error) {
      console.warn('Supabase session persist failed:', error);
    }
  },
  removeItem: async (key: string): Promise<void> => {
    try {
      const fileExists = await exists(SESSION_FILE, { baseDir: BASE_DIR });
      if (!fileExists) return;
      const raw = await readTextFile(SESSION_FILE, { baseDir: BASE_DIR });
      const data = JSON.parse(raw) as Record<string, string>;
      delete data[key];
      await writeTextFile(SESSION_FILE, JSON.stringify(data, null, 0), { baseDir: BASE_DIR });
    } catch {
      // best-effort
    }
  },
};

// ─── Client instances ─────────────────────────────────────────────

let anonClient: SupabaseClient | null = null;
let sessionClient: SupabaseClient | null = null;

function getSupabaseUrl(): string {
  const url = import.meta.env.VITE_SUPABASE_URL as string;
  if (!url) throw new Error('VITE_SUPABASE_URL is not set');
  return url;
}

function getSupabaseAnonKey(): string {
  const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string;
  if (!key) throw new Error('VITE_SUPABASE_ANON_KEY is not set');
  return key;
}

/**
 * Get the anonymous Supabase client (legacy, no auth session).
 * Used for unauthenticated reads where RLS permits anon access.
 */
export function getSupabaseClient(): SupabaseClient {
  if (!anonClient) {
    const url = getSupabaseUrl();
    const anonKey = getSupabaseAnonKey();
    anonClient = createClient(url, anonKey, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
      },
    });
  }
  return anonClient;
}

/**
 * Get the session-authenticated Supabase client.
 * Uses Tauri fs-based storage adapter for session persistence.
 * Call this after the user signs in (or when anon session is active).
 */
export function getSessionClient(): SupabaseClient {
  if (!sessionClient) {
    const url = getSupabaseUrl();
    const anonKey = getSupabaseAnonKey();
    sessionClient = createClient(url, anonKey, {
      auth: {
        storage: tauriStorageAdapter,
        autoRefreshToken: true,
        detectSessionInUrl: false,
      },
    });
  }
  return sessionClient;
}

/**
 * Reset the session client (used on sign-out to force fresh state).
 */
export function resetSessionClient(): void {
  sessionClient = null;
}
