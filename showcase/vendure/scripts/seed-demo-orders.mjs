// Showcase M2 — Demo-Bestelldaten für das Controlling-Warehouse.
// Erzeugt über die Shop-API realistische Bewegungsdaten, damit die dlt-Pipeline
// (vendure → controlling) und die dbt-Marts (M3) echte Zahlen haben:
//   • mehrere PLATZIERTE Seminar-Buchungen (Tagesseminar Mietrecht) mit variabler
//     Teilnehmerzahl  → Ist-Erlös, Join-Partner der Seminar-Kosten (Break-even)
//   • eine platzierte Buch-Bestellung (F1)                       → Ist-Erlös (physisch)
//   • ein Subscription-Auftrag (F5 Berufsschule) + Ratenplan     → gesicherter Erlös
// Guest-Checkout (setCustomerForOrder) — kein Keycloak nötig. Zahlung über den
// dummyPaymentHandler ('rechnung', automaticSettle). Idempotenz: legt nur an,
// wenn noch keine platzierten Bestellungen existieren (sonst überspringen).
//
// Aufruf:  node scripts/seed-demo-orders.mjs   (Stack muss laufen, `pnpm run seed` zuvor)

const SHOP_API = process.env.SHOP_API || 'http://localhost:3000/shop-api';
const ADMIN_API = process.env.ADMIN_API || 'http://localhost:3000/admin-api';
const USER = process.env.SUPERADMIN_USERNAME || 'superadmin';
const PASS = process.env.SUPERADMIN_PASSWORD || 'superadmin';
const EUR = c => (c / 100).toFixed(2) + ' €';

// Ein Shop-Client mit eigenem Cookie-Jar = eine Session = ein aktiver Order.
function shopClient() {
    const jar = new Map();
    function store(res) {
        const list = typeof res.headers.getSetCookie === 'function'
            ? res.headers.getSetCookie()
            : [res.headers.get('set-cookie')].filter(Boolean);
        for (const c of list) {
            const [pair] = c.split(';');
            const i = pair.indexOf('=');
            if (i > 0) jar.set(pair.slice(0, i).trim(), pair.slice(i + 1).trim());
        }
    }
    return async function gql(query, variables = {}) {
        const headers = { 'content-type': 'application/json' };
        if (jar.size) headers['cookie'] = [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
        const res = await fetch(SHOP_API, { method: 'POST', headers, body: JSON.stringify({ query, variables }) });
        store(res);
        const json = await res.json();
        if (json.errors) throw new Error('GraphQL: ' + JSON.stringify(json.errors));
        return json.data;
    };
}

function adminClient() {
    let token = null;
    return async function gql(query, variables = {}) {
        const headers = { 'content-type': 'application/json' };
        if (token) headers['authorization'] = `Bearer ${token}`;
        const res = await fetch(ADMIN_API, { method: 'POST', headers, body: JSON.stringify({ query, variables }) });
        const t = res.headers.get('vendure-auth-token');
        if (t) token = t;
        const json = await res.json();
        if (json.errors) throw new Error('GraphQL: ' + JSON.stringify(json.errors));
        return json.data;
    };
}

function assertOrder(result, field) {
    if (result.__typename !== 'Order') {
        throw new Error(`✗ ${field}: ${result.__typename} ${result.errorCode || ''} ${result.message || ''}`);
    }
    return result;
}

// Vollständiger Guest-Checkout einer Variante; gibt den platzierten Order zurück.
async function placeOrder({ variantId, quantity, shippingCode, customer, enrollmentType }) {
    const gql = shopClient();
    const add = await gql(
        `mutation($id:ID!,$q:Int!){ addItemToOrder(productVariantId:$id, quantity:$q){
            __typename ... on Order { code totalQuantity } ... on ErrorResult { errorCode message } } }`,
        { id: variantId, q: quantity });
    const cart = assertOrder(add.addItemToOrder, 'addItemToOrder');

    await gql(`mutation($i:CreateCustomerInput!){ setCustomerForOrder(input:$i){
        __typename ... on Order { id } ... on ErrorResult { errorCode message } } }`, { i: customer })
        .then(d => assertOrder(d.setCustomerForOrder, 'setCustomerForOrder'));

    if (enrollmentType) {
        await gql(`mutation($i:UpdateOrderInput!){ setOrderCustomFields(input:$i){
            __typename ... on Order { id } ... on ErrorResult { message } } }`,
            { i: { customFields: { enrollmentType } } });
    }

    await gql(`mutation($i:CreateAddressInput!){ setOrderShippingAddress(input:$i){
        __typename ... on Order { id } ... on ErrorResult { errorCode message } } }`,
        { i: { fullName: `${customer.firstName} ${customer.lastName}`, streetLine1: 'Musterweg 1',
               city: 'Bochum', postalCode: '44801', countryCode: 'DE' } })
        .then(d => assertOrder(d.setOrderShippingAddress, 'setOrderShippingAddress'));

    const ship = await gql(`{ eligibleShippingMethods { id code name } }`);
    const method = ship.eligibleShippingMethods.find(m => m.code === shippingCode)
        || ship.eligibleShippingMethods[0];
    await gql(`mutation($ids:[ID!]!){ setOrderShippingMethod(shippingMethodId:$ids){
        __typename ... on Order { id } ... on ErrorResult { errorCode message } } }`, { ids: [method.id] })
        .then(d => assertOrder(d.setOrderShippingMethod, 'setOrderShippingMethod'));

    await gql(`mutation{ transitionOrderToState(state:"ArrangingPayment"){
        __typename ... on Order { state } ... on OrderStateTransitionError { message transitionError } } }`)
        .then(d => assertOrder(d.transitionOrderToState, 'transitionOrderToState'));

    const pay = await gql(`mutation($i:PaymentInput!){ addPaymentToOrder(input:$i){
        __typename ... on Order { code state totalWithTax orderPlacedAt } ... on ErrorResult { errorCode message } } }`,
        { i: { method: 'rechnung', metadata: {} } });
    const placed = assertOrder(pay.addPaymentToOrder, 'addPaymentToOrder');
    console.log(`✓ ${placed.code}: ${quantity}× → ${EUR(placed.totalWithTax)} brutto  [${placed.state}]`);
    return placed;
}

async function main() {
    console.log(`→ Demo-Bestellungen gegen ${SHOP_API}\n`);
    const admin = adminClient();
    await admin(`mutation($u:String!,$p:String!){ login(username:$u,password:$p){ __typename } }`, { u: USER, p: PASS });

    // Idempotenz: nur seeden, wenn noch keine platzierten Bestellungen existieren.
    const existing = await admin(`{ orders(options:{ filter:{ active:{ eq:false } }, take:1 }){ totalItems } }`);
    if (existing.orders.totalItems > 0) {
        console.log(`• ${existing.orders.totalItems} platzierte Bestellung(en) vorhanden — Demo-Seed übersprungen.`);
        return;
    }

    const cat = await shopClient()(`{ products(options:{take:50}){ items { slug variants { id name } } } }`);
    const variant = slug => {
        const p = cat.products.items.find(x => x.slug === slug);
        if (!p) throw new Error(`Produkt ${slug} fehlt — erst \`pnpm run seed\` ausführen.`);
        return p.variants[0];
    };
    const seminar = variant('tagesseminar-mietrecht-aktuell');
    const book = variant('fachbuch-immobilienbewertung');
    const f5 = variant('berufsschule-halbjahresbeitrag');

    // 1) Platzierte Seminar-Buchungen mit variabler Teilnehmerzahl (Ist-Erlös).
    const seminarBookings = [
        { quantity: 3, customer: { firstName: 'Bau', lastName: 'GmbH', emailAddress: 'seminar1@example.com' } },
        { quantity: 2, customer: { firstName: 'Haus', lastName: 'AG', emailAddress: 'seminar2@example.com' } },
        { quantity: 4, customer: { firstName: 'Wohnbau', lastName: 'eG', emailAddress: 'seminar3@example.com' } },
    ];
    let participants = 0;
    for (const b of seminarBookings) {
        await placeOrder({ variantId: seminar.id, quantity: b.quantity, shippingCode: 'digital',
                           customer: b.customer, enrollmentType: 'seminar' });
        participants += b.quantity;
    }
    console.log(`  → Seminar "${seminar.name}": ${participants} Teilnehmer:innen über ${seminarBookings.length} Buchungen`);

    // 2) Eine physische Buch-Bestellung (F1).
    await placeOrder({ variantId: book.id, quantity: 1, shippingCode: 'standard',
                       customer: { firstName: 'Carla', lastName: 'Kundin', emailAddress: 'buch@example.com' } });

    // 3) Subscription-Auftrag (F5) + Ratenplan materialisieren (gesicherter Erlös).
    //    materializeInstallments erzeugt die Raten ohne Checkout/Stripe (vgl. Rechnungslauf-Smoke).
    const sg = shopClient();
    const add = await sg(`mutation($id:ID!){ addItemToOrder(productVariantId:$id, quantity:1){
        __typename ... on Order { id code } ... on ErrorResult { message } } }`, { id: f5.id });
    const subOrder = assertOrder(add.addItemToOrder, 'addItemToOrder(F5)');
    const mat = await admin(`mutation($id:ID!){ materializeInstallments(orderId:$id) }`, { id: subOrder.id });
    console.log(`✓ ${subOrder.code}: Ratenplan F5 materialisiert — ${mat.materializeInstallments} Raten (gesicherter Erlös)`);

    console.log('\nFertig — Bewegungsdaten für dlt/dbt erzeugt.');
}

main().catch(err => { console.error('\n' + err.message); process.exit(1); });
