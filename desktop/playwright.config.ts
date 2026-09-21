import { defineConfig, devices } from '@playwright/test';

// Chromium-only on purpose: one repeatable smoke, no browser matrix, no
// snapshots. Wave-1 explicitly keeps the E2E footprint to one spec + config.
const PORT = 1420;
const BASE_URL = `http://127.0.0.1:${PORT}`;

// webServer uses the plain Vite dev server (frontend only). CI only needs the
// bun toolchain and Chromium installed — no Rust/Tauri build and no backend.
const WEB_SERVER_COMMAND = 'bun run dev';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: BASE_URL,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: WEB_SERVER_COMMAND,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
