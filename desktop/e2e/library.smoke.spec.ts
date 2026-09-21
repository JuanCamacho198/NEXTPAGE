import { expect, test, type Page } from '@playwright/test';

// Single Chromium smoke: the library screen renders a seeded book with the
// Tauri IPC layer stubbed. No real Tauri, Supabase or reader call is made.
//
// NOTE: the routed library screen is `LibraryShelfScreen` (AppRouter renders
// it for route === 'library'); `LibraryView.svelte` is not mounted by any
// route, so the assertion targets the library route that actually renders the
// import list. Assert by role/text only — no production component changes.

const SEEDED_TITLE = 'Seeded Smoke Title';

type StubSeed = { title: string };

async function installTauriInvokeStub(page: Page, seed: StubSeed): Promise<void> {
  await page.addInitScript(({ title }: StubSeed) => {
    const TIMESTAMP = '2026-01-01T00:00:00.000Z';

    const libraryRow = {
      id: 'smoke-book-1',
      title,
      author: 'Smoke Author',
      format: 'epub',
      currentPage: 4,
      totalPages: 10,
      progressPercentage: 40,
      coverPath: null,
      minutesRead: 12,
      updatedAt: TIMESTAMP,
      createdAt: TIMESTAMP,
      collectionIds: [],
      readingStatus: 'reading',
    };

    const sourceRow = {
      id: 'smoke-book-1',
      title,
      author: 'Smoke Author',
      filePath: 'C:/smoke/seeded-smoke-title.epub',
      format: 'epub',
      syncStatus: 'local',
      currentPage: 4,
      totalPages: 10,
      createdAt: TIMESTAMP,
      updatedAt: TIMESTAMP,
    };

    const stats = {
      totalMinutesRead: 12,
      totalSessions: 1,
      booksStarted: 1,
      booksCompleted: 0,
      avgProgressPercentage: 40,
    };

    // Tiny in-memory FS backing @tauri-apps/plugin-fs. `auth.json` holds a
    // local profile so AppState boots straight to the authenticated shell
    // (route 'home') instead of the welcome screen, with no Supabase session.
    const files = new Map<string, string>();
    files.set(
      'auth.json',
      JSON.stringify({
        kind: 'local',
        profile: { name: 'Smoke User', email: null, avatarUrl: null, localOnly: true },
      }),
    );

    const channelResults: Record<string, unknown> = {
      listBooks: [sourceRow],
      listLibraryBooks: [libraryRow],
      listCollections: [],
      getSettings: [],
      getReadingStats: stats,
      getReadingStatsForRange: stats,
      getReadingActivity: [],
      getReadingStreak: 0,
      listSyncOutboxReady: [],
      listBookmarks: [],
      listHighlights: [],
      listTags: [],
      listDictionaryWords: [],
      listInstalledAddons: [],
      listAddonConsents: [],
    };

    // Force the English UI so the sidebar label is stable regardless of the
    // developer machine's persisted locale.
    window.localStorage.setItem('nextpage.ui.locale', 'en');

    const internals = {
      transformCallback: (callback: unknown) => {
        const id = Math.floor(Math.random() * 1_000_000_000);
        (window as unknown as Record<string, unknown>)[`_${id}`] = callback;
        return id;
      },
      unregisterCallback: () => undefined,
      convertFileSrc: (filePath: string) => filePath,
      invoke: async (cmd: string, args?: { path?: unknown; data?: unknown }) => {
        if (cmd.startsWith('plugin:fs|')) {
          const path = typeof args?.path === 'string' ? args.path : '';
          if (cmd === 'plugin:fs|exists') return files.has(path);
          if (cmd === 'plugin:fs|read_text_file') {
            // plugin-fs decodes a byte array, not a string.
            return Array.from(new TextEncoder().encode(files.get(path) ?? ''));
          }
          if (cmd === 'plugin:fs|write_text_file') {
            const bytes =
              args?.data instanceof Uint8Array
                ? args.data
                : Uint8Array.from((args?.data as ArrayLike<number>) ?? []);
            files.set(path, new TextDecoder().decode(bytes));
            return null;
          }
          if (cmd === 'plugin:fs|remove') {
            files.delete(path);
            return null;
          }
          return null;
        }
        if (Object.prototype.hasOwnProperty.call(channelResults, cmd)) {
          return channelResults[cmd];
        }
        return null;
      },
    };

    (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = internals;
  }, seed);
}

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
