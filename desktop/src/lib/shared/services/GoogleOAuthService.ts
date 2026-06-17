/**
 * Google OAuth 2.0 PKCE service — replaces Supabase-proxied auth.
 * Uses Web Crypto API for SHA256 + crypto.getRandomValues for code_verifier.
 * No external OAuth library dependency.
 */

import { authState, type TokenSet } from '$lib/stores/authState.svelte';

const CLIENT_ID = import.meta.env.GOOGLE_OAUTH_CLIENT_ID as string;
const REDIRECT_URI = 'nextpage-desktop://auth-callback';
const AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const SCOPES = [
  'https://www.googleapis.com/auth/drive.appdata',
  'email',
  'profile',
].join(' ');

let codeVerifier: string | null = null;

function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function generateCodeVerifier(): string {
  const bytes = new Uint8Array(128);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes.buffer);
}

export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(hash);
}

export async function startAuth(): Promise<void> {
  const verifier = generateCodeVerifier();
  codeVerifier = verifier;
  const challenge = await generateCodeChallenge(verifier);

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    code_challenge: challenge,
    code_challenge_method: 'S256',
    scope: SCOPES,
    access_type: 'offline',
    prompt: 'consent',
  });

  const authUrl = `${AUTH_ENDPOINT}?${params.toString()}`;
  window.open(authUrl, '_blank');
}

export async function handleCallback(code: string): Promise<void> {
  if (!codeVerifier) {
    throw new Error('No code verifier found — startAuth() must be called before handleCallback()');
  }

  const body = new URLSearchParams({
    code,
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code_verifier: codeVerifier,
    grant_type: 'authorization_code',
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  if (!response.ok) {
    const errorText = await response.text();
    codeVerifier = null;
    throw new Error(`Token exchange failed: ${response.status} ${errorText}`);
  }

  const data = await response.json();
  codeVerifier = null;

  const tokenSet: TokenSet = {
    accessToken: data.access_token,
    refreshToken: data.refresh_token,
    idToken: data.id_token,
    expiresIn: data.expires_in,
  };

  authState.setSession(tokenSet);
}

export async function getValidAccessToken(): Promise<string> {
  const token = authState.accessToken;
  if (!token) {
    throw new Error('No access token available — user must sign in');
  }

  if (!authState.needsRefresh()) {
    return token;
  }

  return refreshAccessToken();
}

export async function refreshAccessToken(): Promise<string> {
  const storedRefreshToken = authState.refreshToken;
  if (!storedRefreshToken) {
    throw new Error('No refresh token available — user must re-authenticate');
  }

  const body = new URLSearchParams({
    client_id: CLIENT_ID,
    refresh_token: storedRefreshToken,
    grant_type: 'refresh_token',
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  if (!response.ok) {
    const errorText = await response.text();
    authState.clearSession();
    throw new Error(`Token refresh failed: ${response.status} ${errorText}`);
  }

  const data = await response.json();
  const tokenSet: TokenSet = {
    accessToken: data.access_token,
    refreshToken: data.refresh_token || storedRefreshToken,
    idToken: data.id_token,
    expiresIn: data.expires_in,
  };

  authState.setSession(tokenSet);
  return data.access_token;
}

export function signOut(): void {
  codeVerifier = null;
  authState.clearSession();
}
