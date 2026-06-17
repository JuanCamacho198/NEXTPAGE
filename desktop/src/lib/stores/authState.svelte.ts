/**
 * Reactive Google OAuth state using Svelte 5 runes ($state).
 * Replaces the Supabase auth session with direct Google OAuth PKCE tokens.
 *
 * Usage:
 *   import { authState } from "$lib/stores/authState.svelte";
 *   // authState.isSignedIn, authState.email, etc. are reactive
 */

let accessToken: string | null = $state(null);
let refreshToken: string | null = $state(null);
let expiresAt: number | null = $state(null);
let email: string | null = $state(null);
let displayName: string | null = $state(null);
let photoUrl: string | null = $state(null);
let userId: string | null = $state(null);

const isSignedIn = $derived(accessToken !== null);
const isTokenExpired = $derived(
  expiresAt === null || Date.now() >= expiresAt - 60000, // 1-minute buffer
);

export interface IdTokenPayload {
  sub: string;
  email: string;
  name: string;
  picture: string;
}

export interface TokenSet {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  expiresIn: number; // seconds
}

function parseIdTokenPayload(idToken: string): IdTokenPayload {
  const parts = idToken.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid id_token format');
  }
  const payloadBase64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  const json = atob(payloadBase64);
  const payload = JSON.parse(json);
  if (!payload.sub || !payload.email) {
    throw new Error('id_token missing required claims (sub, email)');
  }
  return {
    sub: payload.sub,
    email: payload.email,
    name: payload.name || '',
    picture: payload.picture || '',
  };
}

export function setSession(tokens: TokenSet): void {
  accessToken = tokens.accessToken;
  refreshToken = tokens.refreshToken;
  expiresAt = Date.now() + tokens.expiresIn * 1000;

  const payload = parseIdTokenPayload(tokens.idToken);
  userId = payload.sub;
  email = payload.email;
  displayName = payload.name;
  photoUrl = payload.picture;
}

export function clearSession(): void {
  accessToken = null;
  refreshToken = null;
  expiresAt = null;
  email = null;
  displayName = null;
  photoUrl = null;
  userId = null;
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
  setSession,
  clearSession,
  getAccessToken,
  getRefreshToken,
  getExpiresAt,
  needsRefresh,
};
