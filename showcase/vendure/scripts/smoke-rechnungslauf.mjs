// Showcase M4 — Smoke-Test des internen Rechnungslaufs (Rechnungs-/SEPA-Pfad).
// 1) Shop-API: F6-Studiengang (36 Monatsraten) in den Warenkorb legen.
// 2) Admin-API: Ratenplan materialisieren  -> erwartet 36 Installments.
// 3) Admin-API: Rechnungslauf ausführen    -> nur die heute fällige Rate 1 wird 'invoiced'.
// Belegt die wiederkehrende Abrechnung intern, ohne externen Zahlungsanbieter.
// Aufruf:  node scripts/smoke-rechnungslauf.mjs   (Server muss laufen, Seed gelaufen)

const SHOP_API = process.env.SHOP_API || 'http://localhost:3000/shop-api';
const ADMIN_API = process.env.ADMIN_API || 'http://localhost:3000/admin-api';
const USER = process.env.SUPERADMIN_USERNAME || 'superadmin';
const PASS = process.env.SUPERADMIN_PASSWORD || 'superadmin';

function client(url) {
    let token = null;
    return async function gql(query, variables = {}) {
        const headers = { 'content-type': 'application/json' };
        if (token) headers['authorization'] = `Bearer ${token}`;
        const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify({ query, variables }) });
        const t = res.headers.get('vendure-auth-token');
        if (t) token = t;
        const json = await res.json();
        if (json.errors) throw new Error('GraphQL error: ' + JSON.stringify(json.errors, null, 2));
        return json.data;
    };
}

const shop = client(SHOP_API);
const admin = client(ADMIN_API);
const EUR = c => (c / 100).toFixed(2) + ' €';
const DAY = d => new Date(d).toISOString().slice(0, 10);

async function main() {
    // --- 1) Bestellung mit F6 anlegen ---
    const cat = await shop(`{ products(options:{take:50}){ items { slug variants { id name } } } }`);
    const f6 = cat.products.items.find(p => p.slug === 'studiengang-monatsrate');
    if (!f6) throw new Error('F6 (studiengang-monatsrate) nicht gefunden — erst seeden.');
    const variantId = f6.variants[0].id;

    const add = await shop(
        `mutation($id:ID!){ addItemToOrder(productVariantId:$id, quantity:1){
            __typename ... on Order { id code totalWithTax } ... on ErrorResult { message } } }`,
        { id: variantId });
    if (add.addItemToOrder.__typename !== 'Order') throw new Error('addItemToOrder: ' + add.addItemToOrder.message);
    const order = add.addItemToOrder;
    console.log(`Bestellung ${order.code} (id ${order.id}) angelegt — ${f6.variants[0].name}, ${EUR(order.totalWithTax)} jetzt fällig\n`);

    // --- 2) Admin: Ratenplan materialisieren ---
    await admin(`mutation($u:String!,$p:String!){ login(username:$u,password:$p){ __typename } }`, { u: USER, p: PASS });
    const mat = await admin(`mutation($id:ID!){ materializeInstallments(orderId:$id) }`, { id: order.id });
    const createdCount = mat.materializeInstallments;
    console.log(`Ratenplan materialisiert: ${createdCount} Raten`);

    const before = await admin(
        `query($id:ID!){ installmentsForOrder(orderId:$id){ sequence amount currencyCode dueDate status } }`,
        { id: order.id });
    const rows = before.installmentsForOrder;
    const first = rows[0], last = rows[rows.length - 1];
    console.log(`  Rate 1:  ${EUR(first.amount)} ${first.currencyCode}  fällig ${DAY(first.dueDate)}  [${first.status}]`);
    console.log(`  Rate ${last.sequence}: ${EUR(last.amount)} ${last.currencyCode}  fällig ${DAY(last.dueDate)}  [${last.status}]`);

    // --- 3) Rechnungslauf ausführen ---
    const run = await admin(`mutation { runRecurringInvoiceRun }`);
    console.log(`\nRechnungslauf ausgeführt: ${run.runRecurringInvoiceRun} Rate(n) in Rechnung gestellt`);

    const after = await admin(
        `query($id:ID!){ installmentsForOrder(orderId:$id){ sequence status } }`,
        { id: order.id });
    const invoiced = after.installmentsForOrder.filter(i => i.status === 'invoiced');
    const scheduled = after.installmentsForOrder.filter(i => i.status === 'scheduled');
    console.log(`  Status danach: ${invoiced.length} invoiced (Rate(n) ${invoiced.map(i => i.sequence).join(',')}), ${scheduled.length} scheduled`);

    // --- Prüfungen ---
    const ok =
        createdCount === 36 &&
        rows.length === 36 &&
        invoiced.length === 1 && invoiced[0].sequence === 1 &&
        scheduled.length === 35;
    if (!ok) { console.error('\n✗ ABWEICHUNG von der Erwartung (36 Raten, 1 fällig).'); process.exit(1); }
    console.log('\n✓ M4-Smoke-Test bestanden: interner Rechnungslauf materialisiert 36 Raten und stellt die fällige Rate in Rechnung.');
}

main().catch(err => { console.error(err); process.exit(1); });
