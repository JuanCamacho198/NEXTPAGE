import { defineConfig, devices } from '@playwright/test';

// Chromium-only on purpose: one repeatable browser and one visual gate, no
// browser matrix. `toHaveScreenshot` is the gate; the `screenshot` use-option
// only decides whether a *failing* check keeps an artifact, so a green run
// still compares pixels against the committed baselines.
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
    screenshot: 'only-on-failure',
    video: 'off',
  },
  // The visual gate. `maxDiffPixelRatio` is deliberately tight enough that a
  // shifted overlay or an off-token colour fails (see the deliberate-regression
  // proof in slice 4); `animations: 'disabled'` removes the fly/fade timing as a
  // flake source. Loosening either value is a visible change in review.
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.002,
      animations: 'disabled',
    },
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
