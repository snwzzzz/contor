import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against the real backend: e2e/support/start-backend.sh runs contor-api on a fresh Testcontainers
 * Postgres (Docker required), the Next.js dev server proxies /api to it. "setup" runs first-run setup + login once.
 */
const webPort = Number(process.env.E2E_PORT ?? 3100);
const apiPort = Number(process.env.E2E_API_PORT ?? 18080);
// E2E_PROD=true tests the production build (`next start`, run `next build` first): strict CSP without dev allowances.
const production = process.env.E2E_PROD === "true";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: {
    baseURL: `http://127.0.0.1:${webPort}`,
    trace: "on-first-retry",
  },
  projects: [
    { name: "setup", testMatch: /.*\.setup\.ts/ },
    { name: "chromium", use: { ...devices["Desktop Chrome"] }, dependencies: ["setup"] },
  ],
  webServer: [
    {
      command: "./e2e/support/start-backend.sh",
      url: `http://127.0.0.1:${apiPort}/actuator/health`,
      env: { E2E_API_PORT: String(apiPort) },
      reuseExistingServer: false,
      timeout: 300_000,
    },
    {
      command: production
        ? `npx next start --hostname 127.0.0.1 --port ${webPort}`
        : `npm run dev -- --hostname 127.0.0.1 --port ${webPort}`,
      url: `http://127.0.0.1:${webPort}/login`,
      env: { CONTOR_API_URL: `http://127.0.0.1:${apiPort}` },
      reuseExistingServer: !process.env.CI && !production,
      timeout: 120_000,
    },
  ],
});
