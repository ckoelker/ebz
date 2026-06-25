import { test, expect } from '@playwright/test';
import { URLS, keycloakLogin } from './helpers/sso';

const OL = URLS.openolat;
const ADMIN = 'Basic ' + Buffer.from('administrator:openolat').toString('base64');

/**
 * M0-Spike (CI/Branding, war 🔴-Risiko): Klärt, ob OpenOLAT die per-Org cssClass `mandant-demo-ag`
 * (vom Backend über `OrganisationVO.cssClass` gesetzt, via REST verifiziert) in die SEITEN eines
 * Org-Mitglieds rendert — der vermeintliche „native" per-Org-Branding-Hebel.
 *
 * BEFUND (dokumentiert, reproduzierbar): NEIN. Die cssClass ist ein reines Datenmodell-/Admin-Feld;
 * die OpenOLAT-GUI leitet daraus KEIN per-User-Theme ab (body trägt nur `o_lang_*`, die Klasse taucht
 * nirgends im DOM auf). Dieser Test hält den Befund fest — schlägt er künftig fehl (Klasse erscheint),
 * würde OpenOLAT-internes per-Org-Theming doch funktionieren und wir würden es nutzen.
 *
 * KONSEQUENZ (Entscheidung D5): die sichtbare per-Kunde-CI lebt NICHT in OpenOLAT, sondern
 *   (a) auf der per-IdP gebrandeten Keycloak-Login-Seite (Realm ebz-kunde-demo ist bereits distinkt), und
 *   (b) in den SPAs über den `mandant`-Claim + die Mandant-Branding-Felder (primaerFarbe/logoUrl), die
 *       der A4-Endpunkt `/lms/portal/landing` schon ausliefert.
 */
test('M0: OpenOLAT rendert die per-Org cssClass NICHT in Mitglieder-Seiten (Branding gehört an Login/SPA)', async ({
  page,
}) => {
  // 1) Setup (idempotent, reproduzierbar): customer (Carla Kundin) als Mitglied der DEMO_AG-Org.
  const orgs = await (
    await page.request.get(`${OL}/restapi/organisations`, {
      headers: { Authorization: ADMIN, Accept: 'application/json' },
    })
  ).json();
  const demo = orgs.find((o: { externalId?: string }) => o.externalId === 'DEMO_AG');
  expect(demo, 'DEMO_AG-Org existiert (mandanten-seed + /mandant/{id}/projizieren)').toBeTruthy();
  expect(demo.cssClass, 'Backend hat die per-Org cssClass gesetzt').toBe('mandant-demo-ag');

  const users = await (
    await page.request.get(`${OL}/restapi/users?login=customer`, {
      headers: { Authorization: ADMIN, Accept: 'application/json' },
    })
  ).json();
  const cust = (Array.isArray(users) ? users : [users])[0];
  await page.request.put(`${OL}/restapi/organisations/${demo.key}/users/${cust.key}`, {
    headers: { Authorization: ADMIN },
  });

  // 2) Login als Mitglied → OpenOLAT-Oberfläche.
  await page.goto(`${OL}/`, { waitUntil: 'domcontentloaded' });
  await keycloakLogin(page);
  const accept = page.locator(
    'a:has-text("Akzeptieren"), button:has-text("Akzeptieren"), a:has-text("Accept")',
  );
  if (await accept.count()) {
    await accept.first().click();
    await page.waitForLoadState('networkidle').catch(() => {});
  }
  const resume = page.getByRole('button', { name: /^(No|Nein)$/ });
  if (await resume.count()) {
    await resume.first().click();
    await page.waitForLoadState('networkidle').catch(() => {});
  }
  await page.waitForTimeout(1000);

  // 3) Befund: die per-Org cssClass ist NICHT im DOM (kein per-User-Theming durch OpenOLAT).
  const html = await page.evaluate(() => document.documentElement.outerHTML);
  expect(
    html.includes('mandant-demo-ag'),
    'OpenOLAT rendert die Org-cssClass NICHT in Mitglieder-Seiten (M0-Befund → Branding via Login/SPA)',
  ).toBe(false);
});
