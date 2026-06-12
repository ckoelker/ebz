// Showcase M2 — Smoke-Test gegen die Shop-API.
// Belegt: (1) Katalog mit fulfillmentType je Variante sichtbar,
//         (2) Custom Fields end-to-end setzbar:
//             - OrderLine: participantName/participantEmail (Teilnehmer ≠ Besteller)
//             - Order: enrollmentType/trainingCompany (Anmeldeart)
// Aufruf:  node scripts/smoke-shop.mjs   (Server muss laufen)

const SHOP_API = process.env.SHOP_API || 'http://localhost:3000/shop-api';
let token = null;

async function gql(query, variables = {}) {
    const headers = { 'content-type': 'application/json' };
    if (token) headers['authorization'] = `Bearer ${token}`;
    const res = await fetch(SHOP_API, { method: 'POST', headers, body: JSON.stringify({ query, variables }) });
    const t = res.headers.get('vendure-auth-token');
    if (t) token = t; // Session-/Order-Token über die Calls hinweg halten
    const json = await res.json();
    if (json.errors) throw new Error('GraphQL error: ' + JSON.stringify(json.errors, null, 2));
    return json.data;
}

async function main() {
    console.log(`→ Shop-API ${SHOP_API}\n`);

    // (1) Katalog
    const cat = await gql(`{ products(options:{take:10}){ totalItems items {
        name variants { id sku priceWithTax stockLevel customFields { fulfillmentType } } } } }`);
    console.log(`Katalog (${cat.products.totalItems} Produkte):`);
    for (const p of cat.products.items) {
        for (const v of p.variants) {
            console.log(`  • ${p.name}  [${v.customFields.fulfillmentType}]  ${(v.priceWithTax / 100).toFixed(2)} € brutto  Bestand:${v.stockLevel}`);
        }
    }

    // (2) Seminarvariante in den Warenkorb — mit Teilnehmerdaten (≠ Besteller)
    const seminar = cat.products.items
        .flatMap(p => p.variants)
        .find(v => v.customFields.fulfillmentType === 'seminar');
    if (!seminar) throw new Error('Keine Seminar-Variante gefunden');

    const add = await gql(
        `mutation($id:ID!,$qty:Int!,$cf:OrderLineCustomFieldsInput){
            addItemToOrder(productVariantId:$id, quantity:$qty, customFields:$cf){
                __typename
                ... on Order { id lines { quantity customFields { participantName participantEmail } } }
                ... on ErrorResult { errorCode message }
            } }`,
        { id: seminar.id, qty: 1, cf: { participantName: 'Erika Musterfrau', participantEmail: 'erika@example.com' } });
    if (add.addItemToOrder.__typename !== 'Order') throw new Error('addItemToOrder: ' + add.addItemToOrder.message);
    const line = add.addItemToOrder.lines[0];
    console.log(`\nWarenkorb-Position: qty ${line.quantity}, Teilnehmer:in „${line.customFields.participantName}" <${line.customFields.participantEmail}>`);

    // (3) Anmeldeart auf der Bestellung setzen
    const upd = await gql(
        `mutation($cf:UpdateOrderCustomFieldsInput){
            setOrderCustomFields(input:{ customFields:$cf }){
                __typename
                ... on Order { customFields { enrollmentType trainingCompany } }
                ... on ErrorResult { errorCode message }
            } }`,
        { cf: { enrollmentType: 'seminar', trainingCompany: 'EBZ Showcase GmbH' } });
    if (upd.setOrderCustomFields.__typename !== 'Order') throw new Error('setOrderCustomFields: ' + upd.setOrderCustomFields.message);
    const ocf = upd.setOrderCustomFields.customFields;
    console.log(`Bestelldaten: Anmeldeart „${ocf.enrollmentType}", Ausbildungsbetrieb „${ocf.trainingCompany}"`);

    console.log('\n✓ M2-Smoke-Test bestanden: Katalog + Custom Fields (OrderLine & Order) end-to-end.');
}

main().catch(err => { console.error(err); process.exit(1); });
