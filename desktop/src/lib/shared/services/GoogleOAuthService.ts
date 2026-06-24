/**
 * Google OAuth 2.0 PKCE service — replaces Supabase-proxied auth.
 *
 * Uses a loopback redirect URI (http://127.0.0.1:{port}/) served by
 * `tauri-plugin-oauth` v2.0.0. The plugin starts a tiny HTTP server on a free
 * 127.0.0.1 port when `start()` is called, returns the port, and emits an
 * `oauth://url` event with the full callback URL when Google redirects the
 * browser back. We parse the URL here, validate CSRF `state`, and call
 * `handleCallback(code)` (unchanged) to exchange the code for tokens.
 */

import { start, cancel, onUrl } from '@fabianlars/tauri-plugin-oauth';
import { openUrl } from '@tauri-apps/plugin-opener';

import { authState, type TokenSet } from '$lib/stores/authState.svelte';
import { createErrorEvent } from '$lib/shared/events/ErrorEvent';
import { logger } from '$lib/shared/logger/Logger';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_OAUTH_CLIENT_ID as string;
const AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const SCOPES = [
  'https://www.googleapis.com/auth/drive.appdata',
  'email',
  'profile',
].join(' ');

let currentPort: number | null = null;
let expectedState: string | null = null;
let codeVerifier: string | null = null;
let urlUnlisten: (() => void) | null = null;

export type OAuthErrorCode =
  | 'state_mismatch'
  | 'no_code'
  | 'user_denied'
  | 'server_failed'
  | 'token_exchange_failed'
  | 'plugin_unavailable';

export class OAuthError extends Error {
  public readonly code: OAuthErrorCode;
  constructor(message: string, code: OAuthErrorCode) {
    super(message);
    this.name = 'OAuthError';
    this.code = code;
  }
}

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

function buildState(): string {
  const stateBytes = new Uint8Array(32);
  crypto.getRandomValues(stateBytes);
  return base64UrlEncode(stateBytes.buffer);
}

export async function startAuth(): Promise<void> {
  if (currentPort !== null) {
    try {
      await cancel(currentPort);
    } catch {
      /* best-effort */
    }
    currentPort = null;
  }

  let port: number;
  try {
    port = await start();
  } catch (err) {
    throw new OAuthError(
      `Failed to start loopback server: ${String(err)}`,
      'plugin_unavailable',
    );
  }
  currentPort = port;

  const verifier = generateCodeVerifier();
  codeVerifier = verifier;
  const challenge = await generateCodeChallenge(verifier);

  const state = buildState();
  expectedState = state;

  const redirectUri = `http://127.0.0.1:${port}/`;

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: redirectUri,
    response_type: 'code',
    code_challenge: challenge,
    code_challenge_method: 'S256',
    scope: SCOPES,
    state,
    access_type: 'offline',
    prompt: 'consent',
  });

  await openUrl(`${AUTH_ENDPOINT}?${params.toString()}`);
}

export async function handleOAuthCallback(url: string): Promise<void> {
  const parsed = new URL(url);
  const code = parsed.searchParams.get('code');
  const state = parsed.searchParams.get('state');
  const error = parsed.searchParams.get('error');

  if (error === 'access_denied') {
    expectedState = null;
    currentPort = null;
    throw new OAuthError('User denied OAuth consent', 'user_denied');
  }
  if (error) {
    expectedState = null;
    currentPort = null;
    throw new OAuthError(
      `OAuth provider returned error: ${error}`,
      'server_failed',
    );
  }
  if (!code) {
    throw new OAuthError('No authorization code in callback URL', 'no_code');
  }
  if (state !== expectedState) {
    throw new OAuthError(
      'State parameter mismatch — possible CSRF',
      'state_mismatch',
    );
  }

  await handleCallback(code);

  expectedState = null;
  currentPort = null;
}

export async function registerOAuthCallbackHandler(): Promise<void> {
  if (urlUnlisten) {
    return;
  }
  urlUnlisten = await onUrl((url) => {
    handleOAuthCallback(url).catch((err: unknown) => {
      if (err instanceof OAuthError) {
        logger.error(
          createErrorEvent({
            severity: 'medium',
            category: 'validation',
            code: `OAUTH_${err.code.toUpperCase()}`,
            message: err.message,
            context: { code: err.code },
            source: 'sync',
            recoverable: true,
          }),
        );
      } else {
        logger.error(
          createErrorEvent({
            severity: 'high',
            category: 'runtime',
            code: 'OAUTH_CALLBACK_FAILED',
            message: err instanceof Error ? err.message : String(err),
            context: {},
            source: 'sync',
            recoverable: true,
          }),
        );
      }
    });
  });
}

export async function handleCallback(code: string): Promise<void> {
  if (!codeVerifier) {
    throw new Error('No code verifier found — startAuth() must be called before handleCallback()');
  }

  const body = new URLSearchParams({
    code,
    client_id: CLIENT_ID,
    redirect_uri:
      currentPort !== null ? `http://127.0.0.1:${currentPort}/` : 'http://127.0.0.1:0/',
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
    throw new OAuthError(
      `Token exchange failed: ${response.status} ${errorText}`,
      'token_exchange_failed',
    );
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

export async function signOut(): Promise<void> {
  codeVerifier = null;
  expectedState = null;
  if (currentPort !== null) {
    try {
      await cancel(currentPort);
    } catch {
      /* best-effort */
    }
    currentPort = null;
  }
  authState.clearSession();
}
