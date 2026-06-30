import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { keycloakLogin, STAFF } from './helpers/sso';

/**
 * Anmeldung Berufsschule — Browser-Durchstich durch BEIDE SPAs (Außenportal :5175 + mdm-Cockpit :5174).
 * Schließt die UI-Lücke: die Prozesslogik war API-seitig live verifiziert, hier wird jeder Screen wirklich
 * im Browser bedient.
 *
 * Test 1 (Happy-Path, eindeutige Firma → kein Dubletten-Review):
 *   A Portal (öffentlich) Anfrage → C (API) Einladung+Passwort → D Portal(Firma) Azubi anmelden →
 *   E Cockpit(Staff) EBZ-Bestätigung → F Portal(Firma) Vertrag bestätigen (→ AKTIV).
 * Test 2 (Cockpit Dubletten-Review): zweite Anfrage gleichen Namens erzeugt eine Dublette → der KI-Fall
 *   erscheint im Cockpit und wird per „Neuanlage bestätigen" entschieden.
 *
 * Zwei Browser-Kontexte trennen die SSO-Sessions (Staff @ebz-staff, Firma @ebz-customers). Backend-Plumbing
 * ohne eigene UI (Einladung mit UPDATE_PASSWORD-Pflichtaktion → Set-Password-Mail) per API: das Passwort
 * setzt der Test als Admin-Fixture (simuliert „Nutzer hat den Mail-Link genutzt").
 */

const KC = process.env.KEYCLOAK_URL ?? 'http://127.0.0.1:8080';
const KC_HOST = process.env.KEYCLOAK_HOST ?? 'keycloak.localhost:8080';
const API = process.env.API_URL ?? 'http://localhost:8090';
const PORTAL = process.env.PORTAL_URL ?? 'http://localhost:5175';
const MDM = process.env.MDM_URL ?? 'http://localhost:5174';
const FIRMA_PW = 'Test12345!';

async function tokenOf(request: APIRequestContext, realm: string, clientId: string, user: string, pass: string) {
  const res = await request.post(`${KC}/realms/${realm}/protocol/openid-connect/token`, {
    headers: { Host: KC_HOST },
    form: { grant_type: 'password', client_id: clientId, username: user, password: pass },
  });
  expect(res.ok(), `Token ${realm}/${user}`).toBeTruthy();
  return (await res.json()).access_token as string;
}

/** Klickt den globalen „Anmelden"-Button, füllt Keycloak und steuert danach die Zielroute frisch an
 * (der Post-Login-Callback nutzt replaceState statt Router-Push → erneuter goto erzwingt sauberen Mount
 * mit bereitstehender Auth). */
async function spaLogin(page: Page, creds: { username: string; password: string }, zielUrl: string) {
  await page.getByRole('button', { name: 'Anmelden' }).click();
  await page.waitForURL(/keycloak/, { timeout: 30_000 }); // Klick kehrt sofort zurück → Redirect abwarten
  await keycloakLogin(page, creds);
  await page.waitForURL((u) => !u.toString().includes('keycloak'), { timeout: 60_000 });
  await page.goto(zielUrl, { waitUntil: 'networkidle' });
}

/** Sendet die öffentliche Ausbildungsbetrieb-Anfrage im Browser ab und liefert die erzeugten IDs. */
async function anfrageAbsenden(page: Page, firma: string, ansprechName: string, ansprechEmail: string) {
  await page.goto(`${PORTAL}/`, { waitUntil: 'networkidle' });
  await page.getByLabel('Firmenname').fill(firma);
  await page.getByLabel('Name', { exact: true }).fill(ansprechName);
  await page.getByLabel('E-Mail', { exact: true }).fill(ansprechEmail);
  const [resp] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/party/anfragen/ausbildungsbetrieb') && r.request().method() === 'POST'),
    page.getByRole('button', { name: 'Anfrage absenden' }).click(),
  ]);
  await expect(page.getByText('Vielen Dank')).toBeVisible({ timeout: 15_000 });
  return (await resp.json()) as { organisationId: number; ansprechpartner: { id: number } };
}

test('Anmeldung Berufsschule — Happy-Path A→F (Portal + Cockpit)', async ({ browser, request }) => {
  test.setTimeout(150_000);
  const ts = Date.now();
  const firma = `E2E-Betrieb ${ts}`;
  const ansprechName = `Anna Ansprech ${ts}`;
  const ansprechEmail = `firma-e2e-${ts}@example.de`;
  const azubiName = `Azubi ${ts}`;
  const azubiEmail = `azubi-e2e-${ts}@example.de`;

  const staffCtx = await browser.newContext();
  const firmaCtx = await browser.newContext();
  const sp = await staffCtx.newPage();
  const fp = await firmaCtx.newPage();

  // A — öffentliche Anfrage (Portal, kein Login); IDs aus der Antwort übernehmen.
  const { ansprechpartner } = await test.step('A: öffentliche Anfrage', () =>
    anfrageAbsenden(fp, firma, ansprechName, ansprechEmail));

  // C — Einladung + Passwort-Fixture (API, keine eigene UI).
  await test.step('C: einladen + Login bereitstellen', async () => {
    const staffTok = await tokenOf(request, 'ebz-staff', 'staff-frontend', 'staff', 'staff');
    const einl = await request.post(`${API}/party/personen/${ansprechpartner.id}/einladung`, {
      headers: { Authorization: `Bearer ${staffTok}` },
    });
    expect(einl.ok(), 'Einladung provisioniert').toBeTruthy();

    const adminTok = await tokenOf(request, 'master', 'admin-cli', 'admin', 'admin');
    const aAuth = { Authorization: `Bearer ${adminTok}`, Host: KC_HOST };
    const users = await (await request.get(
      `${KC}/admin/realms/ebz-customers/users?email=${encodeURIComponent(ansprechEmail)}`, { headers: aAuth },
    )).json();
    expect(users.length, 'eingeladener Keycloak-User existiert').toBeGreaterThanOrEqual(1);
    const uid = users[0].id as string;
    await request.put(`${KC}/admin/realms/ebz-customers/users/${uid}`, {
      headers: { ...aAuth, 'Content-Type': 'application/json' },
      data: { enabled: true, emailVerified: true, requiredActions: [] },
    });
    await request.put(`${KC}/admin/realms/ebz-customers/users/${uid}/reset-password`, {
      headers: { ...aAuth, 'Content-Type': 'application/json' },
      data: { type: 'password', value: FIRMA_PW, temporary: false },
    });
  });

  // D — Portal (Firma): Login → Azubi anmelden.
  await test.step('D: Firma meldet Azubi an', async () => {
    await fp.goto(`${PORTAL}/azubis`, { waitUntil: 'networkidle' });
    await spaLogin(fp, { username: ansprechEmail, password: FIRMA_PW }, `${PORTAL}/azubis`);
    await expect(fp.getByText(firma), 'buchungsberechtigter Betrieb sichtbar').toBeVisible({ timeout: 20_000 });
    await fp.getByLabel('Name', { exact: true }).fill(azubiName);
    await fp.getByLabel('E-Mail', { exact: true }).fill(azubiEmail);
    await fp.getByLabel('Schuljahr').fill('2025/2026');
    await fp.getByRole('button', { name: 'Azubi anmelden' }).click();
    await expect(fp.getByText(/angemeldet \(Status: angefragt\)/)).toBeVisible({ timeout: 15_000 });
  });

  // E — Cockpit (Staff): EBZ-Bestätigung.
  await test.step('E: EBZ bestätigt die Anmeldung', async () => {
    await sp.goto(`${MDM}/anmeldungen`, { waitUntil: 'networkidle' });
    await spaLogin(sp, STAFF, `${MDM}/anmeldungen`);
    const zeile = sp.getByRole('row').filter({ hasText: azubiName });
    await expect(zeile, 'Azubi-Anmeldung erscheint').toBeVisible({ timeout: 20_000 });
    await zeile.getByRole('button', { name: /EBZ bestätigen/ }).click();
    await expect(sp.getByText(/bestätigt — Azubi & Firma benachrichtigt/)).toBeVisible({ timeout: 15_000 });
  });

  // F — Portal (Firma): Vertrag bestätigen (→ AKTIV).
  await test.step('F: Firma bestätigt den Vertrag', async () => {
    await fp.goto(`${PORTAL}/azubis`, { waitUntil: 'networkidle' });
    const zeile = fp.getByRole('row').filter({ hasText: azubiName });
    await expect(zeile).toBeVisible({ timeout: 20_000 });
    await zeile.getByRole('button', { name: /Vertrag bestätigen/ }).click();
    await expect(fp.getByText(/Vertrag für .* bestätigt/)).toBeVisible({ timeout: 15_000 });
    await expect(fp.getByRole('row').filter({ hasText: azubiName }).getByText('AKTIV', { exact: true }))
      .toBeVisible();
  });

  await staffCtx.close();
  await firmaCtx.close();
});

test('Anmeldung Berufsschule — Cockpit Dubletten-Review (KI-Vorschlag → Neuanlage bestätigen)', async ({ browser, request }) => {
  test.setTimeout(120_000);
  const ts = Date.now();
  const firma = `Dubletten-Demo ${ts}`;

  // Zwei Anfragen gleichen Firmennamens → die zweite hat die erste als Dubletten-Kandidatin (Review-Fall).
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await anfrageAbsenden(page, firma, `Erst Kontakt ${ts}`, `dub1-${ts}@example.de`);
  await anfrageAbsenden(page, firma, `Zweit Kontakt ${ts}`, `dub2-${ts}@example.de`);

  // Cockpit (Staff): der Fall erscheint mit KI-Vorschlag → „Neuanlage bestätigen" (→ Org AKTIV).
  await page.goto(`${MDM}/reviews`, { waitUntil: 'networkidle' });
  await spaLogin(page, STAFF, `${MDM}/reviews`);
  // Die Queue rankt KI-gestützt (kann beim ersten Laden dauern) → großzügig warten + ggf. neu laden.
  const zeile = page.getByRole('row').filter({ hasText: firma }).first();
  await expect(async () => {
    if (!(await zeile.isVisible())) {
      await page.getByRole('button').filter({ has: page.locator('.i-lucide-refresh-cw, [class*="refresh"]') }).first().click().catch(() => {});
    }
    await expect(zeile, 'Dubletten-Firma im Review').toBeVisible({ timeout: 10_000 });
  }).toPass({ timeout: 60_000 });
  await zeile.getByRole('button', { name: /Neuanlage/ }).click();
  await expect(page.getByText(/als eigenständige Neuanlage bestätigt/)).toBeVisible({ timeout: 15_000 });

  await ctx.close();
});
