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

test('Katalog P4: Gast-Checkout end-to-end (Warenkorb → Bestellung)', async ({ request }) => {
  // request-Fixture hält Cookies (vendure_token) über die Aufrufe → eine Session.
  const prod = await (await request.get(`${STOREFRONT}/api/product?slug=online-seminar-betriebskostenabrechnung`)).json();
  const variantId = prod.variants[0].id;

  const added = await (await request.post(`${STOREFRONT}/api/cart`, { data: { variantId } })).json();
  expect(added.totalQuantity).toBeGreaterThan(0);

  const cart = await (await request.get(`${STOREFRONT}/api/cart`)).json();
  expect(cart.lines.length).toBeGreaterThan(0);
  expect(cart.state).toBe('AddingItems');

  const res = await request.post(`${STOREFRONT}/api/checkout`, {
    data: {
      email: 'gast@example.de',
      firstName: 'Gabi',
      lastName: 'Gast',
      address: { streetLine1: 'Teststr. 1', city: 'Bochum', postalCode: '44801', countryCode: 'DE' },
      paymentMethod: 'rechnung',
    },
  });
  expect(res.status()).toBe(200);
  const order = await res.json();
  expect(order.code).toBeTruthy();
  expect(order.state).toBe('PaymentSettled');
});

test('Katalog P4: Detailseite zeigt aktiven Warenkorb-Button + Kasse leer (SSR)', async ({ request }) => {
  const detail = await (await request.get(`${STOREFRONT}/online-seminar-betriebskostenabrechnung?termin=SVA015729`)).text();
  expect(detail).toContain('In den Warenkorb');
  // Frische Session (eigener request-Context ohne Cookies) → leere Kasse.
  const kasse = await (await request.get(`${STOREFRONT}/kasse`)).text();
  expect(kasse).toMatch(/Warenkorb ist leer|Kontakt/);
});

test('Katalog P7: Frühbucherrabatt greift automatisch (Termin in der Zukunft)', async ({ request }) => {
  const prod = await (await request.get(`${STOREFRONT}/api/product?slug=online-seminar-betriebskostenabrechnung`)).json();
  const cart = await (await request.post(`${STOREFRONT}/api/cart`, { data: { variantId: prod.variants[0].id } })).json();
  // Zukünftiger Termin → Frühbucher-Condition erfüllt → 10 % Order-Rabatt automatisch.
  expect(cart.discounts.length).toBeGreaterThan(0);
  expect(cart.discounts[0].description).toMatch(/Frühbucher/);
  expect(cart.discounts[0].amountWithTax).toBeLessThan(0);
  expect(cart.totalWithTax).toBeLessThan(cart.lines[0].linePriceWithTax + 1);
});

test('Katalog P6: Navigation (Collections + CMS-Menüseiten)', async ({ request }) => {
  const nav = await (await request.get(`${STOREFRONT}/api/navigation`)).json();
  expect(nav.collections.length).toBeGreaterThan(0);
  // Nur veröffentlichte imMenu-Seiten; „agb" (nicht im Menü) darf NICHT erscheinen.
  const slugs = nav.pages.map((p: { slug: string }) => p.slug);
  expect(slugs).toContain('ueber-uns');
  expect(slugs).toContain('kontakt');
  expect(slugs).not.toContain('agb');
});

test('Katalog P6: CMS-Seite wird serverseitig gerendert + Menü im Home-SSR', async ({ request }) => {
  const seite = await (await request.get(`${STOREFRONT}/seite/ueber-uns`)).text();
  expect(seite).toContain('Bildungsdienstleister');
  expect(seite).toMatch(/rel="canonical"[^>]*seite\/ueber-uns/);

  const home = await (await request.get(`${STOREFRONT}/`)).text();
  expect(home).toContain('/seite/ueber-uns');
  expect(home).toContain('/seite/kontakt');

  // Nicht-Menü-Seite ist dennoch direkt erreichbar.
  const agb = await request.get(`${STOREFRONT}/seite/agb`);
  expect(agb.status()).toBe(200);
});

test('Vertragsangebot zeigt Anmelde-Deeplink statt Warenkorb', async ({ request }) => {
  const res = await request.get(`${STOREFRONT}/studiengang-bachelor-real-estate`);
  expect(res.status()).toBe(200);
  const html = await res.text();
  expect(html).toContain('Anmeldung / Vertrag');
  expect(html).not.toContain('In den Warenkorb');
});
