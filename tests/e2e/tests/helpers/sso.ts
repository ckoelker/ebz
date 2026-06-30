import { Page, expect } from '@playwright/test';

export const URLS = {
  openolat: process.env.OPENOLAT_URL ?? 'http://localhost:8089',
  portal: process.env.PORTAL_URL ?? 'http://localhost:5175',
  mdm: process.env.MDM_URL ?? 'http://localhost:5174',
  keycloak: process.env.KEYCLOAK_URL ?? 'http://keycloak.localhost:8080',
};

/** Default-Testkunde im Realm ebz-customers (Carla Kundin). */
export const CUSTOMER = { username: 'customer', password: 'customer' };

/** Default-Sachbearbeiter im Realm ebz-staff (Cockpit, Rolle rechnung-pflege/katalog-pflege). */
export const STAFF = { username: 'staff', password: 'staff' };

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
  // Identity-first-Login (aktiv durch den organization-Scope): Passwort erscheint erst nach dem
  // Username-Schritt. Ist das Passwortfeld schon da (klassisches Ein-Seiten-Login), direkt ausfüllen.
  if (!(await page.locator('#password').isVisible().catch(() => false))) {
    await page.click('#kc-login, input[type=submit], button[type=submit]');
    await expect(page.locator('#password')).toBeVisible({ timeout: 30_000 });
  }
  await page.fill('#password', creds.password);
  await Promise.all([
    page.waitForURL((u) => !u.toString().includes('keycloak'), { timeout: 60_000 }),
    page.click('#kc-login, input[type=submit], button[type=submit]'),
  ]);
}
