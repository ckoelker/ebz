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

test('Katalog P3: Facetten-Sidebar wird serverseitig gerendert', async ({ request }) => {
  const res = await request.get(`${STOREFRONT}/`);
  const html = await res.text();
  // Alle vier (+Format) Facettengruppen aus dem Seed erscheinen schon im Roh-HTML.
  for (const facet of ['Veranstaltungsart', 'Thema', 'Branche', 'Region']) {
    expect(html, `Facette ${facet} fehlt im SSR`).toContain(facet);
  }
});

test('Katalog P3: Facetten-Filter + Aggregat-Counts + Pagination (API)', async ({ request }) => {
  const all = await (await request.get(`${STOREFRONT}/api/catalog`)).json();
  expect(all.totalItems).toBeGreaterThan(0);
  expect(all.facetGroups.length).toBeGreaterThanOrEqual(4);
  expect(all.pageCount).toBeGreaterThanOrEqual(1);

  // Facette „Veranstaltungsart = Zertifikatslehrgang" (FacetValue-ID 2) filtert auf weniger Treffer.
  const lehrgang = await request.get(`${STOREFRONT}/api/catalog?veranstaltungsart=2`);
  const filtered = await lehrgang.json();
  expect(filtered.totalItems).toBeGreaterThan(0);
  expect(filtered.totalItems).toBeLessThan(all.totalItems);

  // Pagination: Seite 2 liefert eigene Treffer (Slugs disjunkt zu Seite 1), wenn >1 Seite.
  if (all.pageCount > 1) {
    const p2 = await (await request.get(`${STOREFRONT}/api/catalog?page=2`)).json();
    expect(p2.page).toBe(2);
    const s1 = new Set(all.items.map((i: { slug: string }) => i.slug));
    expect(p2.items.some((i: { slug: string }) => !s1.has(i.slug))).toBeTruthy();
  }
});

test('Katalog P3: Sortierung nach Titel (API)', async ({ request }) => {
  const res = await request.get(`${STOREFRONT}/api/catalog?sort=name-asc`);
  const data = await res.json();
  const namen = data.items.map((i: { productName: string }) => i.productName);
  const sortiert = [...namen].sort((a, b) => a.localeCompare(b, 'de'));
  expect(namen).toEqual(sortiert);
});

test('Vertragsangebot zeigt Anmelde-Deeplink statt Warenkorb', async ({ request }) => {
  const res = await request.get(`${STOREFRONT}/studiengang-bachelor-real-estate`);
  expect(res.status()).toBe(200);
  const html = await res.text();
  expect(html).toContain('Anmeldung / Vertrag');
  expect(html).not.toContain('In den Warenkorb');
});
