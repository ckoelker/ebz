import { Page, expect } from '@playwright/test';

export const URLS = {
  openolat: process.env.OPENOLAT_URL ?? 'http://localhost:8089',
  portal: process.env.PORTAL_URL ?? 'http://localhost:5175',
  keycloak: process.env.KEYCLOAK_URL ?? 'http://keycloak.localhost:8080',
};

/** Default-Testkunde im Realm ebz-customers (Carla Kundin). */
export const CUSTOMER = { username: 'customer', password: 'customer' };

/**
 * Füllt das Keycloak-Login-Formular aus, falls die aktuelle Seite eines ist, und wartet,
 * bis der Redirect zurück zur Anwendung erfolgt ist. No-op, wenn keine Keycloak-Seite
 * sichtbar ist (z.B. bestehende Session). Wiederverwendbar für alle SSO-Services.
 */
export async function keycloakLogin(
  page: Page,
  creds: { username: string; password: string } = CUSTOMER,
) {
  const onKeycloak = page.url().includes('keycloak') || (await page.locator('#username').count()) > 0;
  if (!onKeycloak) return;

  await expect(page.locator('#username')).toBeVisible();
  await page.fill('#username', creds.username);
  await page.fill('#password', creds.password);
  await Promise.all([
    page.waitForURL((u) => !u.toString().includes('keycloak'), { timeout: 60_000 }),
    page.click('#kc-login, input[type=submit], button[type=submit]'),
  ]);
}
