import { test, expect } from '@playwright/test';

/**
 * Storefront-Smoke (Produktkatalog P2-Gerüst). Verifiziert echtes SSR: das ROHE HTML
 * (per request.get, ohne JS-Ausführung) enthält bereits den von Vendure gelieferten Inhalt.
 * Setzt den laufenden compose-Stack voraus (server + storefront) und einen initialisierten
 * Katalog (POST /shop/init am Integrationsbackend).
 *
 * URL über STOREFRONT_URL überschreibbar (compose-Default :3001).
 */
const STOREFRONT = process.env.STOREFRONT_URL || 'http://localhost:3001';

test('Katalog-Startseite wird serverseitig gerendert (SSR)', async ({ page, request }) => {
  // SSR-Beweis: View-Source enthält Produktnamen aus Vendure (nicht erst nach Hydration).
  const res = await request.get(`${STOREFRONT}/`);
  expect(res.status()).toBe(200);
  const html = await res.text();
  expect(html).toContain('EBZ Akademie');
  expect(html).toMatch(/Betriebskostenabrechnung|Zertifikatslehrgang|Fachtagung/);

  const errs: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  page.on('pageerror', (e) => errs.push(String(e)));
  await page.goto(`${STOREFRONT}/`, { waitUntil: 'networkidle' });
  await expect(page.getByRole('heading', { name: /Weiterbildung/ })).toBeVisible();
  expect(errs, errs.join('\n')).toHaveLength(0);
});

test('Detailseite: Speaking-URL + canonical ohne ?termin + Detailblöcke (SSR)', async ({ request }) => {
  const res = await request.get(`${STOREFRONT}/online-seminar-betriebskostenabrechnung?termin=SVA015729`);
  expect(res.status()).toBe(200);
  const html = await res.text();
  expect(html).toContain('Die Betriebskostenabrechnung');
  expect(html).toContain('Inhalte');
  expect(html).toContain('Sabine Brinkmann'); // Ansprechpartner aus CRM-Sync
  expect(html).toMatch(/rel="canonical"[^>]*online-seminar-betriebskostenabrechnung"/);
  expect(html).not.toMatch(/rel="canonical"[^>]*termin=/);
});

test('Vertragsangebot zeigt Anmelde-Deeplink statt Warenkorb', async ({ request }) => {
  const res = await request.get(`${STOREFRONT}/studiengang-bachelor-real-estate`);
  expect(res.status()).toBe(200);
  const html = await res.text();
  expect(html).toContain('Anmeldung / Vertrag');
  expect(html).not.toContain('In den Warenkorb');
});
