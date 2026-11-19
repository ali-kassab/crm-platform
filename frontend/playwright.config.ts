import { defineConfig, devices } from '@playwright/test'

// UI tests against the production build with the API mocked (e2e/fixtures.ts): fast, deterministic,
// no backend needed. Screenshot baselines live next to the specs (RTL and LTR).
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  reporter: [['list']],
  use: { baseURL: 'http://localhost:4173', trace: 'retain-on-failure' },
  expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.01, animations: 'disabled' } },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: { command: 'pnpm preview --port 4173 --strictPort', url: 'http://localhost:4173', reuseExistingServer: true },
})
