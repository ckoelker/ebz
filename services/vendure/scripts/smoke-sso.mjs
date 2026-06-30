// Showcase SSO — Smoke-Test der Keycloak-Anbindung (beide Flächen, strikt getrennt).
// 1) Kunde: Token aus Realm ebz-customers -> Shop-API authenticate -> CurrentUser.
// 2) Staff: Token aus Realm ebz-staff     -> Admin-API authenticate -> CurrentUser.
// Tokens werden headless via Direct-Access-Grant geholt (kein Browser nötig).
// Aufruf:  node scripts/smoke-sso.mjs   (Stack inkl. keycloak muss laufen)

const KC = process.env.KEYCLOAK_URL || 'http://localhost:8088';
const SHOP_API = process.env.SHOP_API || 'http://localhost:3000/shop-api';
const ADMIN_API = process.env.ADMIN_API || 'http://localhost:3000/admin-api';

async function getToken(realm, clientId, username, password) {
    const body = new URLSearchParams({ grant_type: 'password', client_id: clientId, username, password });
    const res = await fetch(`${KC}/realms/${realm}/protocol/openid-connect/token`, {
        method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body,
    });
    const json = await res.json();
    if (!json.access_token) throw new Error(`Kein Token (${realm}): ${JSON.stringify(json)}`);
    return json.access_token;
}

async function authenticate(api, token) {
    const res = await fetch(api, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
            query: `mutation($t:String!){ authenticate(input:{ keycloak:{ token:$t } }){
                __typename
                ... on CurrentUser { identifier channels { code permissions } }
                ... on ErrorResult { errorCode message } } }`,
            variables: { t: token },
        }),
    });
    const json = await res.json();
    if (json.errors) throw new Error('GraphQL: ' + JSON.stringify(json.errors));
    return json.data.authenticate;
}

async function main() {
    let failures = 0;

    console.log('→ Kunde (Realm ebz-customers → Shop-API)');
    const customerToken = await getToken('ebz-customers', 'shop-frontend', 'customer', 'customer');
    const c = await authenticate(SHOP_API, customerToken);
    if (c.__typename === 'CurrentUser') {
        console.log(`  ✓ eingeloggt als Customer: ${c.identifier}`);
    } else { console.log(`  ✗ ${c.errorCode}: ${c.message}`); failures++; }

    console.log('\n→ Staff (Realm ebz-staff → Admin-API)');
    const staffToken = await getToken('ebz-staff', 'staff-frontend', 'staff', 'staff');
    const s = await authenticate(ADMIN_API, staffToken);
    if (s.__typename === 'CurrentUser') {
        const perms = s.channels?.[0]?.permissions ?? [];
        console.log(`  ✓ eingeloggt als Administrator: ${s.identifier}  (Rechte: ${perms.join(', ')})`);
    } else { console.log(`  ✗ ${s.errorCode}: ${s.message}`); failures++; }

    console.log('\n→ Negativtest: Kunden-Token darf NICHT an der Admin-API gelten');
    const cross = await authenticate(ADMIN_API, customerToken);
    if (cross.__typename === 'CurrentUser') {
        console.log('  ✗ Kunden-Token wurde an der Admin-API akzeptiert (Realm-Trennung verletzt!)'); failures++;
    } else {
        console.log(`  ✓ korrekt abgelehnt (${cross.errorCode})`);
    }

    if (failures) { console.error(`\n${failures} Fehler.`); process.exit(1); }
    console.log('\n✓ SSO-Smoke-Test bestanden: Kunden- und Staff-SSO getrennt über zwei Realms.');
}

main().catch(err => { console.error(err); process.exit(1); });
