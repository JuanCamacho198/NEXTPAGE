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
// Google requires the client_secret on the token endpoint whenever the OAuth
// client was issued one, even for Desktop-app + PKCE flows. Read from env
// (also VITE_-prefixed so Vite exposes it to the client bundle).
const CLIENT_SECRET = (import.meta.env.VITE_GOOGLE_OAUTH_CLIENT_SECRET ?? '') as string;
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
  // PKCE spec (RFC 7636 §4.1): code_verifier must be 43-128 chars when
  // base64url-encoded, i.e. 32-96 random bytes. 32 bytes gives 256 bits of
  // entropy and produces a 43-char verifier. Using 128 bytes overflows.
  const bytes = new Uint8Array(32);
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

/**
 * Custom HTML shown to the user in the system browser after Google redirects
 * them back to the loopback server. Replaces `tauri-plugin-oauth`'s default
 * "Please return to the app." plain-text response with a styled success page.
 * Tauri itself does not render this — it lives in the system browser tab.
 */
const LOOPBACK_SUCCESS_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Signed in — NextPage</title>
  <style>
    :root { color-scheme: light dark; }
    * { box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      min-height: 100vh; margin: 0; padding: 24px;
      background: #f0fdf4; color: #14532d;
    }
    .card {
      max-width: 420px; width: 100%;
      background: #fff; border: 1px solid #bbf7d0; border-radius: 16px;
      padding: 32px 24px; text-align: center;
      box-shadow: 0 10px 25px -10px rgba(22, 101, 52, 0.25);
    }
    .check {
      width: 64px; height: 64px; margin: 0 auto 16px;
      background: #22c55e; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
    }
    h1 { margin: 0 0 8px; font-size: 22px; line-height: 1.2; }
    p  { margin: 0; font-size: 14px; line-height: 1.5; color: #4b5563; }
    @media (prefers-color-scheme: dark) {
      body { background: #052e16; color: #dcfce7; }
      .card { background: #0f172a; border-color: #14532d; box-shadow: none; }
      p { color: #94a3b8; }
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="check">
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg>
    </div>
    <h1>You're signed in</h1>
    <p>You can close this tab and return to NextPage. The app will pick up where you left off.</p>
  </div>
</body>
</html>`;

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
    port = await start({ response: LOOPBACK_SUCCESS_HTML });
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
    client_secret: CLIENT_SECRET,
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
