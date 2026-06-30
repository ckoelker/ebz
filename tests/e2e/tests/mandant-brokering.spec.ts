import { test, expect, Page } from '@playwright/test';
import { URLS, keycloakLogin, CUSTOMER } from './helpers/sso';

/**
 * M3-Seed-5: beweist die gebrokerte B2B-Mandanten-Strecke end-to-end im Browser.
 *
 *  - Demo-Kunde `max@demo-ag.de` loggt sich am Außenportal (Realm ebz-customers, organization-aware)
 *    ein → KC routet über die Org-Domain `demo-ag.de` zum gebrokerten Kunden-IdP `kunde-demo`
 *    (Realm ebz-kunde-demo) → JIT zurück in ebz-customers, gestempelt mit `mandant=DEMO_AG`.
 *  - Der Backend-Endpunkt `/lms/portal/landing` (Landing-Regel A4) löst den Login auf den Mandanten
 *    DEMO_AG auf.
 *  - Gegenprobe: der direkte B2C-Login (customer/customer) landet auf dem EBZ-Kernmandanten.
 *
 * Setzt den laufenden compose-Stack + den Mandanten-Seed voraus.
 */
const API = process.env.API_URL ?? 'http://localhost:8090';
const DEMO = { email: 'max.mustermann@demo-ag.de', password: 'demo' };

async function spaAccessToken(page: Page): Promise<string | null> {
  // Der OIDC-Callback (Code->Token) laeuft async nach dem Redirect — auf den oidc.user-Key warten.
  try {
    const handle = await page.waitForFunction(
      () => {
        for (const store of [window.localStorage, window.sessionStorage]) {
          for (let i = 0; i < store.length; i++) {
            const k = store.key(i);
            if (k && k.startsWith('oidc.user')) {
              try {
                const t = JSON.parse(store.getItem(k) as string).access_token;
                if (t) return t as string;
              } catch {
                /* weiter */
              }
            }
          }
        }
        return null;
      },
      null,
      { timeout: 20_000 },
    );
    return (await handle.jsonValue()) as string;
  } catch {
    return null;
  }
}

/** Klickt den „Anmelden"-Button (App.vue, @click=login → signinRedirect), falls noch nicht auf Keycloak. */
async function ensureOnKeycloak(page: Page) {
  if (page.url().includes('keycloak') || (await page.locator('#username').count()) > 0) return;
  const login = page.getByRole('button', { name: 'Anmelden', exact: true });
  await expect(login).toBeVisible({ timeout: 15_000 });
  await Promise.all([
    page.waitForURL((u) => u.toString().includes('keycloak'), { timeout: 30_000 }),
    login.click(),
  ]);
}

test('gebrokerter B2B-Login (max@demo-ag.de) landet auf Mandant DEMO_AG', async ({ page }) => {
  await page.goto(`${URLS.portal}/`, { waitUntil: 'domcontentloaded' });
  await ensureOnKeycloak(page);

  // 1) organization-aware Login: E-Mail eingeben → KC erkennt die Org-Domain demo-ag.de und bietet den
  //    verknüpften Kunden-IdP an ("Your email domain matches the DEMO AG organization").
  await expect(page.locator('#username')).toBeVisible({ timeout: 30_000 });
  await page.fill('#username', DEMO.email);
  await page.click('#kc-login, input[type=submit], button[type=submit]');

  // 2) Entweder erscheint der IdP-Button "DEMO AG (Kunden-Login)" (erster Login: noch kein Account)
  //    ODER KC leitet bekannte föderierte Nutzer direkt zum Broker-IdP weiter. Beides abfangen.
  const idpButton = page.locator('a:has-text("DEMO AG"), button:has-text("DEMO AG")');
  await Promise.race([
    idpButton.first().waitFor({ state: 'visible', timeout: 20_000 }).catch(() => {}),
    page.locator('#password').waitFor({ state: 'visible', timeout: 20_000 }).catch(() => {}),
  ]);
  if (await idpButton.first().isVisible().catch(() => false)) {
    await idpButton.first().click();
    await page.waitForLoadState('domcontentloaded');
  }

  // 3) Auf dem gebrokerten Kunden-IdP (Realm ebz-kunde-demo): klassisches Ein-Seiten-Login.
  await expect(page.locator('#password')).toBeVisible({ timeout: 30_000 });
  if (await page.locator('#username').isVisible().catch(() => false)) {
    await page.fill('#username', DEMO.email);
  }
  await page.fill('#password', DEMO.password);
  await page.click('#kc-login, input[type=submit], button[type=submit]');
  await page.waitForLoadState('domcontentloaded');

  // 4) Durch evtl. KC-Interstitials (First-Broker-Login Profil-Review) klicken, bis zurück im Portal.
  for (let i = 0; i < 6 && page.url().includes('keycloak'); i++) {
    const hatLoginFeld = await page.locator('#password, #username').first().isVisible().catch(() => false);
    if (hatLoginFeld) break; // unerwartetes Login-Formular → nicht blind klicken
    const submit = page.locator('input[type=submit], button[type=submit]');
    if (!(await submit.count())) break;
    await submit.first().click().catch(() => {});
    await page.waitForLoadState('domcontentloaded').catch(() => {});
    await page.waitForTimeout(500);
  }

  // 5) Zurück im Portal, eingeloggt als Max Mustermann (JIT) → Token aus dem SPA-Storage.
  await expect(page.getByText('Max Mustermann')).toBeVisible({ timeout: 60_000 });
  const token = await spaAccessToken(page);
  await page.screenshot({ path: 'screenshots/mandant-brokering-portal.png' });
  expect(token, 'Access-Token im SPA-Storage').toBeTruthy();

  // 5) Landing-Regel A4 über den Backend-Endpunkt: DEMO_AG.
  const res = await page.request.get(`${API}/lms/portal/landing`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.status(), 'landing 200').toBe(200);
  const body = await res.json();
  expect(body.schluessel, 'gelandet auf DEMO_AG').toBe('DEMO_AG');
  expect(body.vertragstyp).toBe('ENTERPRISE_FLAT');
});

test('direkter B2C-Login (customer) landet auf EBZ-Kernmandant', async ({ page }) => {
  await page.goto(`${URLS.portal}/`, { waitUntil: 'domcontentloaded' });
  await ensureOnKeycloak(page);
  await keycloakLogin(page, CUSTOMER);
  await page.waitForURL((u) => !u.toString().includes('keycloak'), { timeout: 60_000 });
  const token = await spaAccessToken(page);
  expect(token).toBeTruthy();
  const res = await page.request.get(`${API}/lms/portal/landing`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.status()).toBe(200);
  const body = await res.json();
  expect(body.vertragstyp, 'B2C-Direktlogin -> EBZ-Kernmandant').toBe('EBZ_CUSTOMER');
});
