# Manual test — welcome-screen

Validates the welcome-screen change against the spec scenarios. Each step
maps to a `Scenario` in `sdd/welcome-screen/spec` (id 1278).

## Pre-conditions

- Tauri dev build (`bun run tauri:dev`).
- A clean `appDataDir/auth.json` (delete the file before the first scenario).
- A real Google OAuth client (the dev env already has the loopback server
  configured).

## Scenarios

### 1. Fresh install, no cache → welcome

1. Delete `appDataDir/auth.json` (or run with a fresh install).
2. Launch the app.
3. **Expected**: a brief spinner shows for ~100-200ms, then the welcome
   screen renders with both "Continuar con Google" and "Continuar en local"
   buttons. No flash of the home screen.
4. **No welcome flash** (Spec: `App init gate prevents welcome flash`).

### 2. Cached Google session → home, no welcome

1. Sign in with Google once so the cache is populated.
2. Quit the app.
3. Re-launch.
4. **Expected**: spinner for ~100-200ms, then **the home screen** renders
   directly. The welcome screen is never visible.
5. The Google login pill in the app sidebar shows the user's name/photo.

### 3. Cached local profile → home, greeting

1. In dev mode, click "Continuar en local" (skip the form).
2. Verify the home hero greets "Dev" by name.
3. Quit and re-launch.
4. **Expected**: home renders directly; the home hero still greets "Dev".

### 4. Successful Google sign-in from welcome → home + cache

1. Start with a clean cache.
2. From the welcome screen, click "Continuar con Google".
3. Complete OAuth in the browser tab.
4. **Expected**: the app routes to home; the OAuth success page is shown
   in the browser; closing the tab returns focus to the desktop app.
5. Check `appDataDir/auth.json` — it should contain a `kind: "google"`
   record with tokens.

### 5. Cancelled Google sign-in → welcome remains

1. From the welcome screen, click "Continuar con Google".
2. In the browser, click "Cancel" / deny consent.
3. **Expected**: the app remains on the welcome screen; the home route is
   not activated. No cache file is written.

### 6. Dev escape hatch

1. With dev build (`bun run tauri:dev`), reach the welcome screen.
2. Click "Continuar en local".
3. **Expected**: a mock local user is created with `name: "Dev"`, the
   profile is cached to `appDataDir/auth.json` (`kind: "local"`), and the
   user is routed to home.

### 7. Prod form — valid name

1. In a production build, reach the welcome screen.
2. Click "Continuar en local" — the inline form expands.
3. Enter a name, optionally an email, click "Continuar".
4. **Expected**: a local user is created, cached, and the user is routed
   to home.

### 8. Prod form — empty name rejected

1. In a production build, reach the welcome screen and open the inline
   form.
2. Leave the name empty, click "Continuar".
3. **Expected**: an error message is shown ("Please enter a name to
   continue."). The form does not submit; the welcome screen remains.

### 9. Sign-out from Settings clears cache

1. Sign in (Google or local) and reach the home screen.
2. Open Settings → Account tab.
3. Click "Cerrar sesion" / "Sign out".
4. **Expected**: the auth cache file is deleted, in-memory auth state is
   cleared, and the app routes to the welcome screen.

### 10. Sign-out button is hidden when no auth

1. Fresh install (no cache, no auth).
2. Reach the welcome screen and… (no, you can't get to Settings from
   welcome). Instead, set up a profile, then sign out (Scenario 9). The
   button disappears from the Account tab.
3. **Expected**: with no auth present, the "Cerrar sesion" button is NOT
   rendered in the Account tab.

### 11. Returning user, no welcome flash

1. Sign in (Google or local).
2. Quit the app.
3. Re-launch.
4. **Expected**: home screen renders directly. There is NO visible flash
   of the welcome screen during the brief init phase.

### 12. Local user skips Drive sync

1. In dev mode, click "Continuar en local" to become a local user.
2. Open Settings → Sync tab (or trigger any Drive sync path).
3. **Expected**: no Drive sync runs. Local users do not have an
   `accessToken`, so the implicit `isSignedIn` gate skips sync.

## Sign-off

- [ ] Scenarios 1-12 pass on a Tauri dev build.
- [ ] Scenarios 1-12 pass on a Tauri release build.
- [ ] No console errors during any scenario.
- [ ] `bun run check` passes.
- [ ] `bun vitest run src/test/unit/stores/authPersistence.test.ts` passes.
