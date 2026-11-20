import { defineConfig, devices } from '@playwright/test'

// Opt-in smoke test against a REAL running stack (no mocks), e.g. `docker compose --profile app up`.
//   LIVE_BASE_URL=http://localhost:8088 pnpm test:live
// Each run registers a brand-new workspace, so it never touches existing data.
// LIVE_SLOW=1 for small free-tier hosts (0.1 CPU): longer waits, same assertions.
const slow = process.env.LIVE_SLOW === '1'
export default defineConfig({
  testDir: './e2e-live',
  reporter: [['list']],
  timeout: slow ? 900_000 : 90_000,
  expect: { timeout: slow ? 90_000 : 5_000 },
  use: {
    baseURL: process.env.LIVE_BASE_URL ?? 'http://localhost:8088',
    trace: 'retain-on-failure',
    ...(slow ? { actionTimeout: 90_000, navigationTimeout: 120_000 } : {}),
  },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } }],
})
