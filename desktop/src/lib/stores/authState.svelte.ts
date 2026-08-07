/**
 * Reactive auth state using Svelte 5 runes ($state).
 *
 * Wraps a Supabase Auth session internally but exposes the same
 * reactive interface as before for backward compatibility.
 *
 * Consumers read: `isSignedIn`, `email`, `userId`, `displayName`,
 * `photoUrl`, `isLocalUser`, `accessToken`, `refreshToken`, `expiresAt`,
 * and call `startAuth()`, `signOut()`.
 *
 * Local-user support:
 * Local users are first-class profiles that do NOT set `accessToken`.
 * `isSignedIn` therefore evaluates to `false` for local users.
 * Callers that need "has any profile" should check
 * `authState.isLocalUser || authState.isSignedIn`.
 */

import type { LocalUserProfile } from './authPersistence';

export type { LocalUserProfile };

/**
 * Legacy TokenSet type for backward compatibility with GoogleOAuthService.ts.
 * @deprecated Will be removed next release cycle.
 */
export interface TokenSet {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  expiresIn: number;
}

/**
 * Internal Supabase session data shape.
 */
export interface SupabaseSessionData {
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
  userId: string | null;
  email: string | null;
  displayName: string | null;
  photoUrl: string | null;
  providerToken: string | null;
  /**
   * Google OAuth refresh token from the session's `provider_refresh_token`
   * (issued because sign-in requests `access_type=offline`). Distinct from
   * `refreshToken` (the Supabase session refresh token — never reusable as a
   * Google refresh token). Persisted to auth.json so it survives restart.
   */
  driveRefreshToken?: string | null;
}

let accessToken: string | null = $state(null);
let refreshToken: string | null = $state(null);
let expiresAt: number | null = $state(null);
let email: string | null = $state(null);
let displayName: string | null = $state(null);
let photoUrl: string | null = $state(null);
let userId: string | null = $state(null);
let localUser: LocalUserProfile | null = $state(null);
let providerToken: string | null = $state(null);
let driveRefreshToken: string | null = $state(null);

const isSignedIn = $derived(accessToken !== null);
const isLocalUser = $derived(localUser !== null);
const isTokenExpired = $derived(
  expiresAt === null || Date.now() >= expiresAt - 60000, // 1-minute buffer
);

/**
 * Set a Supabase session. Clears any local user profile.
 */
export function setSupabaseSession(data: SupabaseSessionData): void {
  accessToken = data.accessToken;
  refreshToken = data.refreshToken;
  expiresAt = data.expiresAt;
  userId = data.userId;
  email = data.email;
  displayName = data.displayName;
  photoUrl = data.photoUrl;
  providerToken = data.providerToken;
  driveRefreshToken = data.driveRefreshToken ?? null;
  localUser = null; // Clear local user when supabase session is set
}

/**
 * Clear the Supabase session (sign out).
 */
export function clearSupabaseSession(): void {
  accessToken = null;
  refreshToken = null;
  expiresAt = null;
  email = null;
  displayName = null;
  photoUrl = null;
  userId = null;
  providerToken = null;
  driveRefreshToken = null;
}

/**
 * Set the active local-user profile. Local users do NOT have an
 * `accessToken` — see file-level comment for the rationale.
 *
 * Callers are expected to persist the profile via `savePersistedAuth({ kind:
 * 'local', profile })` immediately after calling this.
 */
export function setLocalUser(profile: LocalUserProfile): void {
  localUser = profile;
  // Clear any supabase session when switching to local
  clearSupabaseSession();
}

export function clearLocalUser(): void {
  localUser = null;
}

export function getProviderToken(): string | null {
  return providerToken;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getRefreshToken(): string | null {
  return refreshToken;
}

export function getExpiresAt(): number | null {
  return expiresAt;
}

export function needsRefresh(): boolean {
  return accessToken !== null && expiresAt !== null && Date.now() >= expiresAt - 60000;
}

export const authState = {
  get isSignedIn(): boolean {
    return isSignedIn;
  },
  get isTokenExpired(): boolean {
    return isTokenExpired;
  },
  get isLocalUser(): boolean {
    return isLocalUser;
  },
  get email(): string | null {
    return email;
  },
  get displayName(): string | null {
    return displayName;
  },
  get photoUrl(): string | null {
    return photoUrl;
  },
  get userId(): string | null {
    return userId;
  },
  get accessToken(): string | null {
    return accessToken;
  },
  get refreshToken(): string | null {
    return refreshToken;
  },
  get expiresAt(): number | null {
    return expiresAt;
  },
  get providerToken(): string | null {
    return providerToken;
  },
  get driveRefreshToken(): string | null {
    return driveRefreshToken;
  },
  get localUser(): LocalUserProfile | null {
    return localUser;
  },
  setSupabaseSession,
  clearSupabaseSession,
  setLocalUser,
  clearLocalUser,
  getAccessToken,
  getRefreshToken,
  getExpiresAt,
  getProviderToken,
  needsRefresh,
  // @deprecated Kept for GoogleOAuthService.ts backward compat. Remove next cycle.
  setSession: setSupabaseSession,
  clearSession: clearSupabaseSession,
};
