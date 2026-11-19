import { defineConfig, devices } from '@playwright/test'

// Opt-in smoke test against a REAL running stack (no mocks), e.g. `docker compose --profile app up`.
//   LIVE_BASE_URL=http://localhost:8088 pnpm test:live
// Each run registers a brand-new workspace, so it never touches existing data.
export default defineConfig({
  testDir: './e2e-live',
  reporter: [['list']],
  timeout: 90_000,
  use: { baseURL: process.env.LIVE_BASE_URL ?? 'http://localhost:8088', trace: 'retain-on-failure' },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } }],
})
