// Showcase M2 — Katalog-Seed (idempotent).
// Richtet die nötige Grundkonfiguration ein (Land, Zone, Default-Steuerzone,
// Steuersatz) und legt drei repräsentative Produkte über die Vendure Admin-API an:
//   F1 physisches Buch (Versand)   · fulfillmentType = physical
//   F2 PDF-Download (digital)      · fulfillmentType = digital
//   F3 Tagesseminar (Plätze)       · fulfillmentType = seminar
// Aufruf:  npm run seed     (Server muss laufen: docker compose up -d)
// Keine Abhängigkeiten — nutzt das globale fetch von Node 18+.

const ADMIN_API = process.env.ADMIN_API || 'http://localhost:3000/admin-api';
const USER = process.env.SUPERADMIN_USERNAME || 'superadmin';
const PASS = process.env.SUPERADMIN_PASSWORD || 'superadmin';

let authToken = null;

async function gql(query, variables = {}) {
    const headers = { 'content-type': 'application/json' };
    if (authToken) headers['authorization'] = `Bearer ${authToken}`;
    const res = await fetch(ADMIN_API, {
        method: 'POST', headers,
        body: JSON.stringify({ query, variables }),
    });
    const token = res.headers.get('vendure-auth-token');
    if (token) authToken = token;
    const json = await res.json();
    if (json.errors) throw new Error('GraphQL error: ' + JSON.stringify(json.errors, null, 2));
    return json.data;
}

async function login() {
    const d = await gql(
        `mutation($u:String!,$p:String!){ login(username:$u,password:$p,rememberMe:false){
            __typename ... on CurrentUser { identifier } ... on ErrorResult { message } } }`,
        { u: USER, p: PASS });
    if (d.login.__typename !== 'CurrentUser') throw new Error('Login failed: ' + d.login.message);
    console.log(`✓ eingeloggt als ${d.login.identifier}`);
}

// --- Grundkonfiguration ------------------------------------------------------

async function ensureCountry() {
    const d = await gql(`query { countries(options:{take:300}){ items { id code } } }`);
    const found = d.countries.items.find(c => c.code === 'DE');
    if (found) { console.log('• Land DE existiert'); return found.id; }
    const r = await gql(
        `mutation($input:CreateCountryInput!){ createCountry(input:$input){ id } }`,
        { input: { code: 'DE', enabled: true, translations: [
            { languageCode: 'en', name: 'Germany' }, { languageCode: 'de', name: 'Deutschland' }] } });
    console.log('✓ Land DE angelegt');
    return r.createCountry.id;
}

async function ensureZone(countryId) {
    const d = await gql(`query { zones(options:{take:200}){ items { id name } } }`);
    const found = d.zones.items.find(z => z.name === 'Deutschland');
    if (found) { console.log('• Zone Deutschland existiert'); return found.id; }
    const r = await gql(
        `mutation($input:CreateZoneInput!){ createZone(input:$input){ id } }`,
        { input: { name: 'Deutschland', memberIds: [countryId] } });
    console.log('✓ Zone Deutschland angelegt');
    return r.createZone.id;
}

async function ensureChannelTaxZone(zoneId) {
    const d = await gql(`query { activeChannel { id defaultCurrencyCode defaultTaxZone { id } defaultShippingZone { id } } }`);
    const ch = d.activeChannel;
    if (ch.defaultTaxZone && ch.defaultShippingZone && ch.defaultCurrencyCode === 'EUR') {
        console.log('• Channel bereits konfiguriert (Zonen + EUR)'); return;
    }
    await gql(
        `mutation($input:UpdateChannelInput!){ updateChannel(input:$input){ __typename ... on Channel { id } } }`,
        { input: {
            id: ch.id,
            defaultTaxZoneId: zoneId,
            defaultShippingZoneId: zoneId,
            defaultCurrencyCode: 'EUR',
            availableCurrencyCodes: ['EUR'],
        } });
    console.log('✓ Channel: Default-Tax-/Shipping-Zone + Währung EUR gesetzt');
}

async function ensureTaxCategory() {
    const d = await gql(`query { taxCategories(options:{take:50}){ items { id name } } }`);
    const found = d.taxCategories.items.find(t => t.name === 'Standard');
    if (found) { console.log('• TaxCategory Standard existiert'); return found.id; }
    const r = await gql(
        `mutation($input:CreateTaxCategoryInput!){ createTaxCategory(input:$input){ id } }`,
        { input: { name: 'Standard', isDefault: true } });
    console.log('✓ TaxCategory Standard angelegt');
    return r.createTaxCategory.id;
}

async function ensureTaxRate(categoryId, zoneId) {
    const d = await gql(`query { taxRates(options:{take:50}){ items { id name } } }`);
    if (d.taxRates.items.find(t => t.name === 'Standard DE 19%')) { console.log('• Steuersatz 19% existiert'); return; }
    await gql(
        `mutation($input:CreateTaxRateInput!){ createTaxRate(input:$input){ id } }`,
        { input: { name: 'Standard DE 19%', enabled: true, value: 19, categoryId, zoneId } });
    console.log('✓ Steuersatz Standard DE 19% angelegt');
}

// Read-only Rolle für Staff-SSO (Keycloak ebz-staff). Die Admin-Strategy mappt
// SSO-Mitarbeiter auf diese Rolle.
async function ensureStaffRole() {
    const d = await gql(`query { roles(options:{take:100}){ items { code } } activeChannel { id } }`);
    if (d.roles.items.find(r => r.code === 'sso-staff')) { console.log('• Rolle sso-staff existiert'); return; }
    await gql(
        `mutation($input:CreateRoleInput!){ createRole(input:$input){ id } }`,
        { input: {
            code: 'sso-staff',
            description: 'SSO Staff (Keycloak ebz-staff) — read-only Showcase-Rolle',
            permissions: ['ReadCatalog', 'ReadOrder', 'ReadCustomer', 'ReadSettings'],
            channelIds: [d.activeChannel.id],
        } });
    console.log('✓ Rolle sso-staff angelegt');
}

// --- Versand & Zahlung -------------------------------------------------------
// Ohne mindestens eine Versandart kann ein Order nicht nach ArrangingPayment
// transitionen (Leitplanke §8a-11) — auch Download/Seminar brauchen daher eine
// 0-€-Versandart ("digitale Bereitstellung"). Zahlart nutzt den dummyPaymentHandler
// aus vendure-config.ts (Rechnungs-/Showcase-Pfad, automatischer Settle).

async function ensureShippingMethods() {
    const d = await gql(`query { shippingMethods(options:{take:100}){ items { id code } } }`);
    const have = new Set(d.shippingMethods.items.map(m => m.code));
    const methods = [
        { code: 'standard', rate: 490, de: 'Standardversand', en: 'Standard shipping',
          desc: 'Versand physischer Ware per Post' },
        { code: 'digital', rate: 0, de: 'Digitale Bereitstellung', en: 'Digital delivery',
          desc: 'Kein Versand — Download/Seminar (0 €)' },
    ];
    for (const m of methods) {
        if (have.has(m.code)) { console.log(`• Versandart ${m.code} existiert`); continue; }
        await gql(
            `mutation($input:CreateShippingMethodInput!){ createShippingMethod(input:$input){ id } }`,
            { input: {
                code: m.code,
                fulfillmentHandler: 'manual-fulfillment',
                checker: { code: 'default-shipping-eligibility-checker',
                           arguments: [{ name: 'orderMinimum', value: '0' }] },
                calculator: { code: 'default-shipping-calculator', arguments: [
                    { name: 'rate', value: String(m.rate) },
                    { name: 'includesTax', value: 'auto' },
                    { name: 'taxRate', value: '19' },
                ] },
                translations: [
                    { languageCode: 'de', name: m.de, description: m.desc },
                    { languageCode: 'en', name: m.en, description: m.desc },
                ],
            } });
        console.log(`✓ Versandart angelegt: ${m.de} (${(m.rate / 100).toFixed(2)} €)`);
    }
}

async function ensurePaymentMethod() {
    const d = await gql(`query { paymentMethods(options:{take:100}){ items { id code } } }`);
    if (d.paymentMethods.items.find(p => p.code === 'rechnung')) {
        console.log('• Zahlart rechnung existiert'); return;
    }
    await gql(
        `mutation($input:CreatePaymentMethodInput!){ createPaymentMethod(input:$input){ id } }`,
        { input: {
            code: 'rechnung', enabled: true,
            handler: { code: 'dummy-payment-handler',
                       arguments: [{ name: 'automaticSettle', value: 'true' }] },
            translations: [
                { languageCode: 'de', name: 'Rechnung', description: 'Zahlung per Rechnung (Showcase)' },
                { languageCode: 'en', name: 'Invoice', description: 'Pay by invoice (showcase)' },
            ],
        } });
    console.log('✓ Zahlart angelegt: Rechnung (dummyPaymentHandler, automaticSettle)');
}

// --- Produkte ----------------------------------------------------------------

function tr(name, slug, description) {
    return ['en', 'de'].map(languageCode => ({ languageCode, name, slug, description }));
}

async function listProducts() {
    const d = await gql(`query { products(options:{take:200}){ items { id slug variants { id } } } }`);
    const map = new Map();
    for (const p of d.products.items) map.set(p.slug, p);
    return map;
}

async function ensureProduct(def, existing, taxCategoryId) {
    let product = existing.get(def.slug);
    if (!product) {
        const r = await gql(
            `mutation($input:CreateProductInput!){ createProduct(input:$input){ id slug } }`,
            { input: { enabled: true, translations: tr(def.name, def.slug, def.description) } });
        product = { id: r.createProduct.id, variants: [] };
        console.log(`✓ Produkt angelegt: ${def.name}`);
    }
    if (product.variants && product.variants.length > 0) {
        console.log(`• Variante existiert: ${def.name}`);
        return;
    }
    await gql(
        `mutation($input:[CreateProductVariantInput!]!){ createProductVariants(input:$input){ id sku } }`,
        { input: [{
            productId: product.id,
            translations: ['en', 'de'].map(languageCode => ({ languageCode, name: def.name })),
            sku: def.sku, price: def.price, taxCategoryId,
            stockOnHand: def.stockOnHand, trackInventory: def.trackInventory, optionIds: [],
            customFields: { fulfillmentType: def.fulfillmentType, ...(def.subscription ?? {}) },
        }] });
    console.log(`✓ Variante: ${def.name}  (${def.fulfillmentType}, ${(def.price / 100).toFixed(2)} €, Bestand ${def.stockOnHand})`);
}

const PRODUCTS = [
    { name: 'Fachbuch: Immobilienbewertung kompakt', slug: 'fachbuch-immobilienbewertung',
      description: 'Gedrucktes Fachbuch, wird per Post versendet (Showcase F1 — physische Ware).',
      sku: 'BOOK-IMMO-01', price: 4990, stockOnHand: 100, trackInventory: 'TRUE', fulfillmentType: 'physical' },
    { name: 'Skript: Maklerrecht (PDF-Download)', slug: 'skript-maklerrecht-download',
      description: 'Digitales Skript als Download, kein Versand (Showcase F2 — Digitalprodukt).',
      sku: 'DL-MAKLER-01', price: 1500, stockOnHand: 0, trackInventory: 'FALSE', fulfillmentType: 'digital' },
    { name: 'Tagesseminar: Mietrecht aktuell', slug: 'tagesseminar-mietrecht-aktuell',
      description: 'Eintägiges Präsenzseminar mit begrenzter Teilnehmerzahl (Showcase F3 — Seminar; Teilnehmerdaten je Position).',
      sku: 'SEM-MIET-2026-09', price: 19000, stockOnHand: 20, trackInventory: 'TRUE', fulfillmentType: 'seminar' },

    // --- M3: wiederkehrende Abrechnung (Subscriptions) ---
    { name: 'Abo: Veranstaltungsreihe (monatlich)', slug: 'abo-veranstaltungsreihe',
      description: 'Monatliches Abo für eine wiederkehrende Veranstaltungsreihe (Showcase F4 — unbefristet).',
      sku: 'ABO-REIHE-01', price: 2900, stockOnHand: 0, trackInventory: 'FALSE', fulfillmentType: 'subscription',
      subscription: { subscriptionInterval: 'month', subscriptionIntervalCount: 1, subscriptionTotalCount: 0 } },
    { name: 'Berufsschule: Halbjahresbeitrag', slug: 'berufsschule-halbjahresbeitrag',
      description: 'Halbjährliche Schulgebühr über die Ausbildung (Showcase F5 — 6 Raten alle 6 Monate, 3 Jahre).',
      sku: 'BS-HALBJAHR-01', price: 60000, stockOnHand: 0, trackInventory: 'FALSE', fulfillmentType: 'subscription',
      subscription: { subscriptionInterval: 'month', subscriptionIntervalCount: 6, subscriptionTotalCount: 6 } },
    { name: 'Studiengang: Monatsrate (36 Monate)', slug: 'studiengang-monatsrate',
      description: 'Monatliche Studiengebühr über 3 Jahre (Showcase F6 — 36 Monatsraten, festes Ende).',
      sku: 'STUD-MONAT-01', price: 30000, stockOnHand: 0, trackInventory: 'FALSE', fulfillmentType: 'subscription',
      subscription: { subscriptionInterval: 'month', subscriptionIntervalCount: 1, subscriptionTotalCount: 36 } },
];

// --- Seminar-Kosten (M2) -----------------------------------------------------
// Beispiel-Kostenpositionen für das Tagesseminar (Deckungsbeitragsrechnung).
// Reproduziert das Rechenbeispiel des Plans: Fix 1.800 € + variabel 25 €/TN.
// Beträge in Cent, netto. Idempotent (vorhandene Positionen je Variante werden übersprungen).
const SEMINAR_COSTS = {
    'tagesseminar-mietrecht-aktuell': [
        { costType: 'dozent',   label: 'Dozentenhonorar',   amount: 120000, isVariable: false, perParticipant: false },
        { costType: 'raum',     label: 'Raummiete',         amount:  60000, isVariable: false, perParticipant: false },
        { costType: 'material', label: 'Seminarunterlagen', amount:   1500, isVariable: true,  perParticipant: true  },
        { costType: 'catering', label: 'Tagungsverpflegung', amount:  1000, isVariable: true,  perParticipant: true  },
    ],
};

async function ensureSeminarCosts() {
    const d = await gql(`query { products(options:{take:200}){ items { slug variants { id name } } } }`);
    for (const [slug, costs] of Object.entries(SEMINAR_COSTS)) {
        const product = d.products.items.find(p => p.slug === slug);
        const variant = product?.variants?.[0];
        if (!variant) { console.log(`• Seminar-Variante für ${slug} (noch) nicht vorhanden — übersprungen`); continue; }
        const ex = await gql(`query($id:ID!){ seminarCosts(productVariantId:$id){ costType } }`, { id: variant.id });
        const have = new Set(ex.seminarCosts.map(c => c.costType));
        for (const c of costs) {
            if (have.has(c.costType)) { console.log(`• Kostenposition ${c.costType} (${variant.name}) existiert`); continue; }
            await gql(
                `mutation($input:CreateSeminarCostInput!){ createSeminarCost(input:$input){ id } }`,
                { input: { productVariantId: variant.id, currencyCode: 'EUR', ...c } });
            const kind = `${c.isVariable ? 'variabel' : 'fix'}${c.perParticipant ? '/TN' : ''}`;
            console.log(`✓ Kostenposition: ${c.label} (${(c.amount / 100).toFixed(2)} €, ${kind})`);
        }
    }
}

async function main() {
    console.log(`→ Seed gegen ${ADMIN_API}`);
    await login();
    const countryId = await ensureCountry();
    const zoneId = await ensureZone(countryId);
    await ensureChannelTaxZone(zoneId);
    const taxCategoryId = await ensureTaxCategory();
    await ensureTaxRate(taxCategoryId, zoneId);
    await ensureStaffRole();
    await ensureShippingMethods();
    await ensurePaymentMethod();
    const existing = await listProducts();
    for (const def of PRODUCTS) await ensureProduct(def, existing, taxCategoryId);
    await ensureSeminarCosts();
    console.log('\nFertig.');
}

main().catch(err => { console.error(err); process.exit(1); });
