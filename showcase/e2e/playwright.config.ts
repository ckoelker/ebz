import { defineConfig, devices } from '@playwright/test';

/**
 * Projektweite E2E-Konfiguration. Die Tests laufen gegen den bereits laufenden
 * docker-compose-Stack (showcase/docker-compose.yml) — sie starten KEINE Services selbst,
 * damit man sie schnell gegen die Live-Umgebung fahren kann.
 *
 * Service-URLs sind über Env überschreibbar (CI / abweichende Ports), Defaults entsprechen
 * den compose-Defaults:
 *   OPENOLAT_URL  http://localhost:8089
 *   PORTAL_URL    http://localhost:5175
 *   KEYCLOAK_URL  http://keycloak.localhost:8080
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    actionTimeout: 15_000,
    navigationTimeout: 60_000,
    ignoreHTTPSErrors: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    viewport: { width: 1400, height: 900 },
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
