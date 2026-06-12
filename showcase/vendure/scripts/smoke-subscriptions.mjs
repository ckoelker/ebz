// Showcase M3 — Smoke-Test der wiederkehrenden Abrechnung (F4–F6).
// Ruft je Subscription-Variante die Preview-Query des Stripe-Subscription-Plugins
// ab. Diese rechnet den Zahlplan über die ShowcaseSubscriptionStrategy — OHNE
// Stripe zu belasten. Belegt damit Intervall, Faktor und befristet/unbefristet.
// Aufruf:  node scripts/smoke-subscriptions.mjs   (Server muss laufen, Seed gelaufen)

const SHOP_API = process.env.SHOP_API || 'http://localhost:3000/shop-api';

async function gql(query, variables = {}) {
    const res = await fetch(SHOP_API, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ query, variables }),
    });
    const json = await res.json();
    if (json.errors) throw new Error('GraphQL error: ' + JSON.stringify(json.errors, null, 2));
    return json.data;
}

const EUR = c => (c / 100).toFixed(2) + ' €';
const DAY = d => (d ? new Date(d).toISOString().slice(0, 10) : '—');

// Erwartungen je Slug
const EXPECT = {
    'abo-veranstaltungsreihe':       { interval: 'month', intervalCount: 1, bounded: false, label: 'F4 Abo (unbefristet)' },
    'berufsschule-halbjahresbeitrag':{ interval: 'month', intervalCount: 6, bounded: true,  label: 'F5 Berufsschule (Halbjahr)' },
    'studiengang-monatsrate':        { interval: 'month', intervalCount: 1, bounded: true,  label: 'F6 Studium (36 Monate)' },
};

async function main() {
    console.log(`→ Shop-API ${SHOP_API}\n`);

    const data = await gql(`{ products(options:{take:50}){ items {
        slug variants { id name customFields {
            fulfillmentType subscriptionInterval subscriptionIntervalCount subscriptionTotalCount } } } } }`);

    const subs = data.products.items
        .filter(p => EXPECT[p.slug])
        .map(p => ({ slug: p.slug, variant: p.variants[0] }));

    let failures = 0;
    for (const { slug, variant } of subs) {
        const exp = EXPECT[slug];
        const res = await gql(
            `query($id:ID!){ previewStripeSubscriptions(productVariantId:$id){
                name amountDueNow priceIncludesTax
                recurring { amount interval intervalCount startDate endDate } } }`,
            { id: variant.id });
        const sub = res.previewStripeSubscriptions[0];
        const r = sub.recurring;
        const totalCount = variant.customFields.subscriptionTotalCount;

        console.log(`■ ${exp.label}  —  ${variant.name}`);
        console.log(`    Konfig:     interval=${variant.customFields.subscriptionInterval} ×${variant.customFields.subscriptionIntervalCount}, Raten gesamt=${totalCount === 0 ? '∞' : totalCount}`);
        console.log(`    Jetzt fällig: ${EUR(sub.amountDueNow)} (priceIncludesTax=${sub.priceIncludesTax})`);
        console.log(`    Plan:       ${EUR(r.amount)} alle ${r.intervalCount} ${r.interval}(e), ab ${DAY(r.startDate)} bis ${DAY(r.endDate)}`);

        const ok =
            r.interval === exp.interval &&
            r.intervalCount === exp.intervalCount &&
            (exp.bounded ? !!r.endDate : !r.endDate);
        console.log(`    ${ok ? '✓ erwartungsgemäß' : '✗ ABWEICHUNG'}\n`);
        if (!ok) failures++;
    }

    if (subs.length !== 3) { console.error(`Erwartet 3 Subscription-Produkte, gefunden ${subs.length}`); process.exit(1); }
    if (failures) { console.error(`${failures} Abweichung(en).`); process.exit(1); }
    console.log('✓ M3-Smoke-Test bestanden: Zahlpläne F4/F5/F6 korrekt berechnet (ohne Stripe-Charge).');
}

main().catch(err => { console.error(err); process.exit(1); });
