/**
 * SupabaseAuthService — wraps supabase-js auth for desktop.
 *
 * Replaces the custom PKCE OAuth flow (GoogleOAuthService.ts) with
 * Supabase Auth's built-in OAuth + session management.
 *
 * Flow:
 * 1. signInWithGoogle() opens Supabase OAuth URL in system browser
 * 2. User authenticates with Google (hosted by Supabase)
 * 3. Supabase redirects to loopback URL (http://127.0.0.1:{port}/callback)
 * 4. @tauri-apps/plugin-oauth captures the callback URL
 * 5. We extract the PKCE code and call exchangeCodeForSession()
 * 6. supabase-js stores the session internally (via TauriStorage adapter)
 *
 * Google Drive token (provider_token) is stored alongside session.
 *
 * @deprecated GoogleOAuthService.ts — kept for 1 release cycle, then removed.
 */

import { start, cancel, onUrl } from '@fabianlars/tauri-plugin-oauth';
import { openUrl } from '@tauri-apps/plugin-opener';
import type { Session } from '@supabase/supabase-js';

import { getSessionClient, getLiveSession } from '$lib/services/supabase';
import { savePersistedAuth, loadDriveRefreshToken, saveDriveRefreshToken } from '$lib/stores/authPersistence';
import { authState } from '$lib/stores/authState.svelte';
import { createErrorEvent } from '$lib/shared/events/ErrorEvent';
import { logger } from '$lib/shared/logger/Logger';
import { DRIVE_SCOPE, redactLogLine, syncError } from '$lib/shared/protocol/DriveCatalogContract';

let currentPort: number | null = null;
let urlUnlisten: (() => void) | null = null;

/**
 * Custom HTML shown in the browser tab after Supabase redirects back.
 * The user sees "You're signed in" and can close the tab.
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
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: flex; align-items: center; justify-content: center;
      min-height: 100vh; margin: 0; padding: 24px;
      background: #f0fdf4; color: #14532d;
    }
    .card {
      max-width: 420px; width: 100%;
      background: #fff; border: 1px solid #bbf7d0; border-radius: 16px;
      padding: 32px 24px; text-align: center;
    }
    .check {
      width: 64px; height: 64px; margin: 0 auto 16px;
      background: #22c55e; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
    }
    h1 { margin: 0 0 8px; font-size: 22px; }
    p  { margin: 0; font-size: 14px; color: #4b5563; }
    @media (prefers-color-scheme: dark) {
      body { background: #052e16; color: #dcfce7; }
      .card { background: #0f172a; border-color: #14532d; }
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
    <p>You can close this tab and return to NextPage.</p>
  </div>
</body>
</html>`;

/**
 * Listen for OAuth callbacks on the loopback URL (http://127.0.0.1:{port}/callback).
 * Must be called once at app init (in main.ts).
 */
export async function registerSupabaseCallbackHandler(): Promise<void> {
  if (urlUnlisten) return;

  urlUnlisten = await onUrl(async (url) => {
    try {
      const parsed = new URL(url);

      // Try PKCE flow first: ?code=... in query params
      const code = parsed.searchParams.get('code');

      if (code) {
        const supabase = getSessionClient();
        const { data, error } = await supabase.auth.exchangeCodeForSession(code);

        if (error) {
          logger.error(
            createErrorEvent({
              severity: 'medium',
              category: 'runtime',
              code: 'SUPABASE_CODE_EXCHANGE_FAILED',
              message: error.message,
              context: { error: error.message },
              source: 'sync',
              recoverable: true,
            }),
          );
          return;
        }

        if (data.session) {
          await handleSession(data.session);
        }
        return;
      }

      // Try implicit/hybrid flow: #access_token=... in hash fragment
      const hashParams = new URLSearchParams(parsed.hash.slice(1));
      const accessToken = hashParams.get('access_token');
      const refreshToken = hashParams.get('refresh_token');

      if (accessToken && refreshToken) {
        const supabase = getSessionClient();
        const { data, error } = await supabase.auth.setSession({
          access_token: accessToken,
          refresh_token: refreshToken,
        });

        if (error) {
          logger.error(
            createErrorEvent({
              severity: 'medium',
              category: 'runtime',
              code: 'SUPABASE_SESSION_SET_FAILED',
              message: error.message,
              context: { error: error.message },
              source: 'sync',
              recoverable: true,
            }),
          );
          return;
        }

        if (data.session) {
          await handleSession(data.session);
        }
        return;
      }

      // No recognizable auth payload in the URL
      logger.warn(
        createErrorEvent({
          severity: 'low',
          category: 'validation',
          code: 'SUPABASE_OAUTH_NO_PAYLOAD',
          message: 'OAuth callback missing both authorization code and session tokens',
          context: { url },
          source: 'sync',
          recoverable: true,
        }),
      );
    } catch (err) {
      logger.error(
        createErrorEvent({
          severity: 'high',
          category: 'runtime',
          code: 'SUPABASE_OAUTH_CALLBACK_FAILED',
          message: err instanceof Error ? err.message : String(err),
          context: {},
          source: 'sync',
          recoverable: true,
        }),
      );
    }
  });
}

/**
 * Start Google sign-in via Supabase Auth.
 * Opens Supabase's hosted Google OAuth page in the system browser.
 * The redirect goes to a loopback URL on 127.0.0.1 where plugin-oauth captures it.
 */
export async function signInWithGoogle(): Promise<void> {
  // Cancel any previous loopback
  if (currentPort !== null) {
    try {
      await cancel(currentPort);
    } catch {
      /* best-effort */
    }
    currentPort = null;
  }

  // Start loopback server
  let port: number;
  try {
    port = await start({ response: LOOPBACK_SUCCESS_HTML });
  } catch (err) {
    throw new Error(`Failed to start loopback server: ${String(err)}`);
  }
  currentPort = port;

  const supabase = getSessionClient();
  const redirectTo = `http://127.0.0.1:${port}/callback`;

  const { data, error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: {
      redirectTo,
      skipBrowserRedirect: true,
      // Additional provider scopes MUST go in `options.scopes`, not
      // `queryParams.scope`. supabase-js merges these into Google's
      // authorization URL; putting drive.file in queryParams meant Google
      // never issued a token with the Drive scope, so GoTrue returned no
      // provider_token/provider_refresh_token (DRIVE_TOKEN_MISSING).
      scopes: DRIVE_SCOPE,
      queryParams: {
        access_type: 'offline',
        prompt: 'consent',
      },
    },
  });

  if (error) {
    throw new Error(`Supabase OAuth error: ${error.message}`);
  }

  if (data.url) {
    await openUrl(data.url);
  }
}

/**
 * Handle a Supabase session — store it in authState and persist it.
 * Persists `provider_refresh_token` (issued because sign-in requests
 * `access_type=offline`) so Drive tokens can be refreshed after supabase-js
 * auto-refresh drops `provider_token` (DTL-1).
 */
async function handleSession(session: Session): Promise<void> {
  const providerToken = session.provider_token ?? null;
  // GoTrue does not always re-issue the Google provider refresh token on every
  // sign-in (project-dependent, DTL-1). Preserve a previously persisted token
  // so Drive sync survives re-login instead of losing it (DRIVE_TOKEN_LOST).
  const previous = await loadDriveRefreshToken();
  const providerRefreshToken = session.provider_refresh_token ?? previous;

  authState.setSupabaseSession({
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    expiresAt: session.expires_at ? session.expires_at * 1000 : null,
    userId: session.user.id,
    email: session.user.email ?? null,
    displayName: session.user.user_metadata?.full_name ?? session.user.user_metadata?.name ?? null,
    photoUrl: session.user.user_metadata?.avatar_url ?? session.user.user_metadata?.picture ?? null,
    providerToken,
    driveRefreshToken: providerRefreshToken,
  });

  await savePersistedAuth({
    kind: 'supabase',
    session: session as unknown as Record<string, unknown>,
  });

  // Ensure the (possibly preserved) Drive refresh token survives in auth.json
  // even when GoTrue omits provider_refresh_token on this login (DRIVE_TOKEN_LOST).
  if (providerRefreshToken && !session.provider_refresh_token) {
    await saveDriveRefreshToken(providerRefreshToken);
  }
}

/**
 * Get the Google Drive access token from the Supabase session's provider_token.
 * Returns null if the token is not available (user not signed in or not via Google).
 */
export async function getDriveToken(): Promise<string | null> {
  const supabase = getSessionClient();
  const { data } = await supabase.auth.getSession();
  return data.session?.provider_token ?? null;
}

/**
 * Layered Google Drive token refresh (DTL-1/DTL-2):
 * 1. `supabase.auth.refreshSession()` → use the re-issued `provider_token`
 *    when GoTrue provides one (project-dependent; see apply-progress 4.1).
 * 2. Direct Google token endpoint (`oauth2.googleapis.com/token`,
 *    `grant_type=refresh_token`) with `VITE_GOOGLE_OAUTH_CLIENT_ID` +
 *    `VITE_GOOGLE_OAUTH_CLIENT_SECRET` and the persisted
 *    `provider_refresh_token`. Only valid when the env Google client is the
 *    one configured in the Supabase dashboard (apply-progress 4.2).
 * 3. Typed `AUTH_REQUIRED` (retryable=false) — single attempt, no hot loop.
 *
 * Never logs tokens; every message passes through `redactLogLine` (DTL-3).
 */
export async function refreshDriveToken(): Promise<string> {
  const supabase = getSessionClient();

  // Path 1: GoTrue session refresh may re-issue provider_token.
  try {
    const { data, error } = await supabase.auth.refreshSession();
    if (!error && data.session?.provider_token) {
      return data.session.provider_token;
    }
  } catch (e) {
    logger.warn(
      createErrorEvent({
        severity: 'low',
        category: 'runtime',
        code: 'DRIVE_TOKEN_REFRESH_GOTRUE_FAILED',
        message: redactLogLine('Drive token refresh via GoTrue failed; falling back'),
        context: { reason: e instanceof Error ? redactLogLine(e.message) : 'unknown' },
        source: 'sync',
        recoverable: true,
      }),
    );
  }

  // Path 2: direct Google token exchange with the persisted refresh token.
  const refreshToken = authState.driveRefreshToken;
  const clientId = import.meta.env.VITE_GOOGLE_OAUTH_CLIENT_ID as string | undefined;
  const clientSecret = import.meta.env.VITE_GOOGLE_OAUTH_CLIENT_SECRET as string | undefined;
  if (refreshToken && clientId && clientSecret) {
    try {
      const response = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          client_id: clientId,
          client_secret: clientSecret,
          refresh_token: refreshToken,
        }),
      });
      if (response.ok) {
        const data = await response.json();
        if (typeof data.access_token === 'string' && data.access_token) {
          return data.access_token;
        }
      }
    } catch (e) {
      logger.warn(
        createErrorEvent({
          severity: 'low',
          category: 'runtime',
          code: 'DRIVE_TOKEN_REFRESH_GOOGLE_FAILED',
          message: redactLogLine('Direct Google token refresh failed'),
          context: { reason: e instanceof Error ? redactLogLine(e.message) : 'unknown' },
          source: 'sync',
          recoverable: true,
        }),
      );
    }
  }

  // Path 3: typed re-auth fallback — never a silent no-op, never a hot loop.
  throw syncError(
    'AUTH_REQUIRED',
    'Google Drive access expired. Please sign in with Google again.',
    false,
  );
}

/**
 * Sign the user out: clear Supabase session, reset state, clear persistence.
 */
export async function signOut(): Promise<void> {
  if (currentPort !== null) {
    try {
      await cancel(currentPort);
    } catch {
      /* best-effort */
    }
    currentPort = null;
  }

  const supabase = getSessionClient();
  const { error } = await supabase.auth.signOut();
  if (error) {
    console.warn('Supabase signOut error:', error.message);
  }

  authState.clearSupabaseSession();
  // Persistence is cleared via AppState signOutAndReturnToWelcome
}

/**
 * Sign in anonymously to get an auth context for RLS.
 * Used on app start when no session exists.
 *
 * DA-3: anonymous fallback applies ONLY when no real session can be restored.
 * If any live session exists (real or anon), this no-ops — an anonymous
 * sign-in must never clobber a real persisted session.
 */
export async function signInAnonymously(): Promise<void> {
  if (getLiveSession() !== null) return;

  const supabase = getSessionClient();
  const { data, error } = await supabase.auth.signInAnonymously();

  if (error) {
    console.warn('Anon sign-in failed:', error.message);
    return;
  }

  // Re-check after the await: a real session may have landed while the
  // anonymous sign-in was in flight (e.g. OAuth callback completing) — in
  // that case discard the anon result and keep the real session (DA-3.1).
  if (data.session && getLiveSession() === null) {
    authState.setSupabaseSession({
      accessToken: data.session.access_token,
      refreshToken: data.session.refresh_token,
      expiresAt: data.session.expires_at ? data.session.expires_at * 1000 : null,
      userId: data.session.user.id,
      email: null,
      displayName: null,
      photoUrl: null,
      providerToken: null,
    });
  }
}

/**
 * Restore a persisted Supabase session on app start.
 * Returns the session if restored, or null.
 */
export async function restoreSession(): Promise<Session | null> {
  const supabase = getSessionClient();
  const { data } = await supabase.auth.getSession();
  return data.session;
}

/**
 * Remove OAuth callback handler (for cleanup).
 */
export function unregisterCallbackHandler(): void {
  if (urlUnlisten) {
    urlUnlisten();
    urlUnlisten = null;
  }
}
