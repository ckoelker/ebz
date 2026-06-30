import { test, expect } from '@playwright/test';
import { URLS, keycloakLogin, CUSTOMER } from './helpers/sso';

const OL = URLS.openolat;
const ADMIN = 'Basic ' + Buffer.from('administrator:openolat').toString('base64');
const H = { headers: { Authorization: ADMIN, Accept: 'application/json' } };

/**
 * M4 (C1/K5) — Content-share-once: EIN importiertes Repo-Entry (das "Video-Nugget") wird über MEHRERE
 * org-skopierte Curricula geteilt (EBZ-Default-Org + DEMO_AG), bei Storage x 1 (genau ein Repo-Entry,
 * n Curriculum-Elemente referenzieren es). Setup macht `openolat/lms-share-seed.sh`.
 *
 * Hintergrund (C2-Audit): Catalog-2.0-Offers sind in OpenOLAT 20.3 NICHT per REST anlegbar (kein /offer
 * in restapi/openapi.json). Der offizielle, voll REST-fähige Weg für org-skopiertes, startbares Sharing
 * ist das Curriculum — genau das wird hier nachgewiesen.
 */

test('M4: ein Repo-Entry in ≥2 Org-Curricula geteilt (Storage ×1) — REST-Topologie', async ({ request }) => {
  const entries = await (await request.get(`${OL}/restapi/repo/entries`, H)).json();
  const shared = (Array.isArray(entries) ? entries : []).filter((e: { displayname?: string }) =>
    (e.displayname ?? '').includes('H5P Showcase'),
  );
  expect(shared.length, 'genau EIN geteiltes Nugget existiert (Storage ×1)').toBe(1);
  const key = shared[0].key as number;

  // Dasselbe Entry wird von ≥2 Curriculum-Elementen referenziert …
  const elements = await (await request.get(`${OL}/restapi/repo/entries/${key}/curriculum/elements`, H)).json();
  expect(Array.isArray(elements) ? elements.length : 0, '≥2 Curriculum-Elemente teilen dasselbe Entry').toBeGreaterThanOrEqual(2);
  // … in ≥2 verschiedenen Curricula (= verschiedene Orgs).
  const curricula = new Set((elements as { curriculumKey?: number }[]).map((e) => e.curriculumKey));
  expect(curricula.size, '≥2 verschiedene Curricula').toBeGreaterThanOrEqual(2);

  // Und das Entry ist an ≥2 Organisationen gelinkt, inkl. DEMO_AG.
  const orgs = await (await request.get(`${OL}/restapi/repo/entries/${key}/organisations`, H)).json();
  expect(Array.isArray(orgs) ? orgs.length : 0, 'Entry an ≥2 Orgs gelinkt').toBeGreaterThanOrEqual(2);
  expect(
    (orgs as { externalId?: string }[]).some((o) => o.externalId === 'DEMO_AG'),
    'DEMO_AG unter den teilenden Orgs',
  ).toBeTruthy();
});

// M4-Browser-Beleg: customer (EBZ-Curriculum-Teilnehmer) öffnet den geteilten Kurs. Setzt voraus, dass
// OpenOLAT-SSO Identity-Claims bekommt — die fehlten zwischenzeitlich, weil die ebz-customers-Realm-JSON
// ein `clientScopes`-Array hatte und Keycloak deshalb die Built-in-Scopes (profile/email/…) NICHT anlegte.
// Fix: Standard-Scopes explizit in die Realm-JSON aufgenommen.
test('M4: EBZ-Teilnehmer (customer) startet den geteilten Kurs im Browser', async ({ page, request }) => {
  const entries = await (await request.get(`${OL}/restapi/repo/entries`, H)).json();
  const key = (entries as { displayname?: string; key: number }[]).find((e) =>
    (e.displayname ?? '').includes('H5P Showcase'),
  )?.key;
  expect(key, 'geteiltes Nugget gefunden').toBeTruthy();

  await page.goto(`${OL}/`, { waitUntil: 'domcontentloaded' });
  await keycloakLogin(page, CUSTOMER);
  for (const sel of ['button:has-text("Akzeptieren")', 'a:has-text("Akzeptieren")']) {
    const l = page.locator(sel);
    if (await l.count()) await l.first().click().catch(() => {});
  }
  const no = page.getByRole('button', { name: /^(No|Nein)$/ });
  if (await no.count()) await no.first().click().catch(() => {});

  // Permalink des geteilten Kurses — als Curriculum-Teilnehmer muss customer ihn öffnen können.
  await page.goto(`${OL}/auth/RepositoryEntry/${key}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  const body = await page.evaluate(() => document.body.innerText);
  expect(/keine Berechtigung|no access|nicht berechtigt|not allowed/i.test(body), 'kein Zugriffsfehler').toBeFalsy();
  expect(body.includes('Course Presentation'), 'geteilter Kurs ist gerendert/startbar').toBeTruthy();
  await page.screenshot({ path: 'screenshots/mandant-content-share-ebz.png' });
});
