import { expect, test } from '@playwright/test';
import { DEFAULT_SEEDED_TITLE, installTauriInvokeStub } from './harness/tauriStub';

// Single Chromium smoke: the library screen renders a seeded book with the
// Tauri IPC layer stubbed. No real Tauri, Supabase or reader call is made.
//
// NOTE: the routed library screen is `LibraryShelfScreen` (AppRouter renders
// it for route === 'library'); `LibraryView.svelte` is not mounted by any
// route, so the assertion targets the library route that actually renders the
// import list. Assert by role/text only — no production component changes.
//
// The invoke double now lives in `e2e/harness/tauriStub.ts` and is shared with
// the visual gate, so the smoke and the gate boot the app the same way. Both
// assertions below are unchanged.

const SEEDED_TITLE = DEFAULT_SEEDED_TITLE;

test('library import list renders the seeded book with invoke stubbed', async ({ page }) => {
  const remoteDataRequests: string[] = [];
  page.on('request', (request) => {
    if (/\/(rest|auth)\/v1\//.test(request.url())) remoteDataRequests.push(request.url());
  });

  await installTauriInvokeStub(page, { title: SEEDED_TITLE });

  await page.goto('/');

  const libraryNav = page.locator('aside nav').getByRole('button', { name: 'Library' });
  await expect(libraryNav).toBeVisible({ timeout: 30_000 });
  await libraryNav.click();

  await expect(page.getByText(SEEDED_TITLE).first()).toBeVisible({ timeout: 15_000 });

  // Frontend-only boundary: no PostgREST/Auth traffic may leave the browser.
  expect(remoteDataRequests).toEqual([]);
});
