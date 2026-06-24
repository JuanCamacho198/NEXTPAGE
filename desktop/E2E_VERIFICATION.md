# E2E Verification — OAuth Loopback Migration

> **Status**: BLOCKED in headless environments. The previous `migrate-supabase-to-gdrive`
> cycle marked "✅ Complete" without running this procedure. **This change does not
> mark itself complete until all 13 steps below pass on all three target platforms
> (Windows, macOS, Linux).**

## Why this matters

The new OAuth flow replaces a **hardcoded custom-URI-scheme redirect** (`nextpage-desktop://auth-callback`) that Google Cloud Console **rejects for "Desktop app" OAuth clients**. The replacement is a **loopback redirect** (`http://127.0.0.1:{port}/`) served by `tauri-plugin-oauth` v2.0.0.

Google accepts the new flow only if:

1. The OAuth client in Google Cloud Console is type **"Desktop app"** (not "Web application").
2. The authorized redirect URI list contains **`http://127.0.0.1`** (no port — Google permits any port for Desktop-app clients).
3. The Google Drive API is enabled on the same project.
4. The OAuth consent screen is configured (User type, App name, support email, Test users if "Testing" status).

If any of these is wrong, the user sees Google's `redirect_uri_mismatch` error and **cannot sign in at all**. There is no offline fallback. The unit tests pass, the build succeeds, the `cargo deny` check is green, but the app is broken in production.

## One-time Google Cloud Console setup

1. Open https://console.cloud.google.com.
2. Navigate to **APIs & Services → Credentials**.
3. Create a new OAuth client (or update the existing one):
   - Application type: **Desktop app** (NOT "Web application").
   - Name: `NextPage Desktop`.
   - Authorized redirect URIs: add **`http://127.0.0.1`** (no port).
4. Enable the Google Drive API: **APIs & Services → Library → Google Drive API → Enable**.
5. Configure the OAuth consent screen (**APIs & Services → OAuth consent screen**):
   - User type: **External**.
   - App name: `NextPage`.
   - User support email: `<your email>`.
   - Developer contact: `<your email>`.
   - If status is "Testing", add your email as a **Test user** under **Audience**.
6. Copy the new Client ID into `desktop/.env` as:
   ```
   GOOGLE_OAUTH_CLIENT_ID=<the-client-id>.apps.googleusercontent.com
   ```

## Test procedure (Windows + macOS + Linux)

Run all 13 steps on each of the three target platforms. **All 13 must pass on all three
platforms before the change is marked complete.**

| # | Step | Expected result |
|---|------|-----------------|
| 1 | `bun install` | Picks up new JS dep `@fabianlars/tauri-plugin-oauth`. Exit code 0. |
| 2 | `cd src-tauri && cargo build` | Picks up new Rust dep `tauri-plugin-oauth`. Exit code 0. |
| 3 | `bun run tauri:dev` | App launches. No panic. No "plugin not found" error. |
| 4 | Click **"Sign in with Google"** in the UI | System browser opens (not in-webview). |
| 5 | Verify the browser URL | Begins with `https://accounts.google.com/o/oauth2/v2/auth?...` and contains `redirect_uri=http%3A%2F%2F127.0.0.1%3A<PORT>%2F` and `state=...`. |
| 6 | Complete Google sign-in | User grants the requested scopes. |
| 7 | Verify the browser redirect | The browser navigates to `http://127.0.0.1:<PORT>/?code=...&state=...`. The browser tab may show a "connection refused" or be quickly redirected — that is OK, the URL was already captured. |
| 8 | Verify the app state | `authState.isSignedIn === true`. Profile panel shows the user's email, display name, and photo. |
| 9 | Click **"Sign out"** | The app shows the signed-out state. No "stuck loading" UI. |
| 10 | Verify no loopback server is bound | Run `netstat -an \| grep <PORT>` (or `lsof -i :<PORT>` on macOS/Linux). Nothing bound to the port. Alternatively, close the app and confirm the port is free for other processes. |
| 11 | Click **"Sign in"** again | Flow completes successfully. `authState.isSignedIn === true` again. **This proves REQ-9 multi-instance/cancel logic.** |
| 12 | Close the app, reopen within 1 hour | The user stays signed in. `authState.isSignedIn === true` immediately on app load. **This proves token refresh via the `refresh_token` works.** |
| 13 | Check the app's log output (terminal where `bun run tauri:dev` is running, or DevTools in the webview) | No `OAUTH_STATE_MISMATCH`, `OAUTH_USER_DENIED`, `OAUTH_NO_CODE`, `OAUTH_SERVER_FAILED`, `OAUTH_PLUGIN_UNAVAILABLE`, or `OAUTH_TOKEN_EXCHANGE_FAILED` events. |

## Pass criteria

- All 13 steps pass on Windows.
- All 13 steps pass on macOS.
- All 13 steps pass on Linux.
- No `OAUTH_*` errors in the logs.
- The user-visible email matches the Google account on every platform.
- The loopback server port is freed after each sign-in (the plugin's documented behavior — it exits after the first request).

## Failure mode reporting

If any step fails, capture:

1. **Step number** that failed.
2. **Platform** (Windows / macOS / Linux — include build/version).
3. **URL the browser was redirected to** (copy the full address bar).
4. **App log output** — the terminal where `bun run tauri:dev` is running, OR the webview DevTools console (right-click → Inspect, or via `tauri.conf.json` `devtools: true`).
5. **Port number** the loopback server tried to bind to (visible in logs as `oauth://server started on 127.0.0.1:PORT` or similar).
6. **Google Cloud Console screenshot** of the OAuth client's authorized redirect URIs.

File a follow-up task and re-run the procedure from the failing step after the fix. **Do NOT mark the change as complete until all 13 steps pass on all three platforms.**

## Platform notes

### Windows
- Default browser: Edge or Chrome. Both work.
- If Edge blocks the redirect, click "Always allow" on the protocol prompt.
- `netstat` syntax: `netstat -an | findstr :<PORT>` (PowerShell) or `netstat -an | grep <PORT>` (bash via Git Bash / WSL).

### macOS
- Default browser: Safari. The first launch may prompt to allow `oauth://url` events; click "Allow".
- `lsof` syntax: `lsof -i :<PORT>`.
- Gatekeeper may require running the un-signed dev binary once via "Open Anyway" in System Settings.

### Linux
- Default browser: depends on the desktop environment (Chromium, Firefox, etc.). The plugin emits the URL via the system browser opener regardless.
- `ss` syntax: `ss -tlnp | grep :<PORT>` (requires `sudo` for the process column).
- **Headless / CI note**: a Linux environment without a GUI (e.g. CI containers) cannot complete this procedure. In that case, document "E2E performed on Windows + macOS only" and file a follow-up issue for Linux GUI verification. This is acceptable for CI but must be flagged explicitly.

## Why the unit tests are not enough

`src/test/unit/services/GoogleOAuthService.test.ts` mocks the `@fabianlars/tauri-plugin-oauth` plugin entirely. It proves:

- `start()` is called and the port is captured.
- `state` is generated and validated.
- The OAuth error codes map correctly.
- Multi-instance cancel works at the JS layer.

It does **NOT** prove:

- The Rust plugin's TcpListener actually binds to 127.0.0.1 on the user's machine.
- Google's auth server accepts the request with the configured OAuth client.
- The browser actually navigates to the loopback server.
- The plugin's `oauth://url` event actually reaches the frontend.
- The token exchange succeeds end-to-end (mocked in tests).

Only the manual procedure above can prove these. Skipping it is the gap the previous `migrate-supabase-to-gdrive` cycle left open.
