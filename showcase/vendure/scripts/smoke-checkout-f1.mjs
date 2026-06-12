// Showcase M5 — End-to-End-Smoke des F1-Checkouts (Buch) auf API-Ebene.
// Spiegelt exakt die Frontend-Store-Kette: anonymer Cart → Keycloak-Login
// (authenticate) → Adresse → Versandart → ArrangingPayment → Zahlung (Rechnung).
// Der Keycloak-Token kommt per Direct-Grant (Client shop-frontend erlaubt das);
// im Browser läuft stattdessen der OIDC-Redirect (keycloak-js).
//
// Aufruf:  node scripts/smoke-checkout-f1.mjs   (Stack muss laufen)

const SHOP_API = process.env.SHOP_API || 'http://localhost:3000/shop-api';
const KC = process.env.KC || 'http://localhost:8088';
const REALM = 'ebz-customers';
const CLIENT = 'shop-frontend';
const USER = 'customer', PASS = 'customer';

// In-Memory-Cookie-Jar (entspricht der Browser-Session / §8a-2). Vendure rotiert
// das Session-Cookie beim Login (Session-Fixation-Schutz), daher alle Set-Cookie
// sauber als name→value mergen.
const jar = new Map();

function storeCookies(res) {
  const list = typeof res.headers.getSetCookie === 'function'
    ? res.headers.getSetCookie()
    : [res.headers.get('set-cookie')].filter(Boolean);
  for (const c of list) {
    const [pair] = c.split(';');
    const idx = pair.indexOf('=');
    if (idx > 0) jar.set(pair.slice(0, idx).trim(), pair.slice(idx + 1).trim());
  }
}
function cookieHeader() {
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
}

async function gql(query, variables = {}) {
  const headers = { 'content-type': 'application/json' };
  if (jar.size) headers['cookie'] = cookieHeader();
  const res = await fetch(SHOP_API, { method: 'POST', headers, body: JSON.stringify({ query, variables }) });
  storeCookies(res);
  const json = await res.json();
  if (json.errors) throw new Error('GraphQL: ' + JSON.stringify(json.errors));
  return json.data;
}

function check(cond, msg) { if (!cond) throw new Error('✗ ' + msg); console.log('✓ ' + msg); }
function assertOrder(result, field) {
  if (result.__typename !== 'Order') throw new Error(`✗ ${field}: ${result.__typename} ${result.errorCode || ''} ${result.message || ''}`);
  return result;
}

async function getKeycloakToken() {
  const body = new URLSearchParams({ grant_type: 'password', client_id: CLIENT, username: USER, password: PASS });
  const res = await fetch(`${KC}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body,
  });
  const json = await res.json();
  if (!json.access_token) throw new Error('Kein Keycloak-Token: ' + JSON.stringify(json));
  return json.access_token;
}

async function main() {
  console.log(`→ F1-Checkout gegen ${SHOP_API}\n`);

  // 1) Variant des Buchs holen
  const cat = await gql(`{ product(slug:"fachbuch-immobilienbewertung"){ variants { id priceWithTax } } }`);
  const variant = cat.product.variants[0];
  check(variant?.id, `Buch-Variante gefunden (${(variant.priceWithTax / 100).toFixed(2)} € brutto)`);

  // 2) Anonym in den Warenkorb
  const add = await gql(
    `mutation($id:ID!){ addItemToOrder(productVariantId:$id, quantity:1){ __typename ... on Order { code totalQuantity } ... on ErrorResult { errorCode message } } }`,
    { id: variant.id });
  const order = assertOrder(add.addItemToOrder, 'addItemToOrder');
  check(order.totalQuantity === 1, `anonymer Warenkorb angelegt (${order.code})`);

  // 3) Keycloak-Login → Vendure-Session (§8a-1/3)
  const token = await getKeycloakToken();
  const auth = await gql(
    `mutation($t:String!){ authenticate(input:{ keycloak:{ token:$t } }){ __typename ... on CurrentUser { identifier } ... on ErrorResult { errorCode message } } }`,
    { t: token });
  check(auth.authenticate.__typename === 'CurrentUser', `authenticate ok (${auth.authenticate.identifier})`);

  // 4) Cart überlebt den Login und ist demselben Kunden zugeordnet (§8a-3)
  const after = await gql(`{ activeCustomer { emailAddress } activeOrder { code totalQuantity } }`);
  check(after.activeCustomer?.emailAddress, `eingeloggt als ${after.activeCustomer?.emailAddress}`);
  check(after.activeOrder?.code === order.code && after.activeOrder.totalQuantity === 1,
    `Warenkorb nach Login erhalten (${after.activeOrder?.code})`);

  // 5) Lieferadresse
  const addr = await gql(
    `mutation($i:CreateAddressInput!){ setOrderShippingAddress(input:$i){ __typename ... on Order { state } ... on ErrorResult { errorCode message } } }`,
    { i: { fullName: 'Carla Kundin', streetLine1: 'Musterweg 1', city: 'Bochum', postalCode: '44801', countryCode: 'DE' } });
  assertOrder(addr.setOrderShippingAddress, 'setOrderShippingAddress');
  check(true, 'Lieferadresse gesetzt');

  // 6) eligibleShippingMethods (erst nach Adresse, §8a-12)
  const ship = await gql(`{ eligibleShippingMethods { id name priceWithTax } }`);
  check(ship.eligibleShippingMethods.length > 0, `Versandarten verfügbar: ${ship.eligibleShippingMethods.map(m => m.name).join(', ')}`);
  const standard = ship.eligibleShippingMethods.find(m => /standard/i.test(m.name)) || ship.eligibleShippingMethods[0];

  // 7) Versandart setzen
  const setShip = await gql(
    `mutation($ids:[ID!]!){ setOrderShippingMethod(shippingMethodId:$ids){ __typename ... on Order { shippingWithTax totalWithTax } ... on ErrorResult { errorCode message } } }`,
    { ids: [standard.id] });
  const shipped = assertOrder(setShip.setOrderShippingMethod, 'setOrderShippingMethod');
  check(true, `Versandart "${standard.name}" gesetzt (Versand ${(shipped.shippingWithTax / 100).toFixed(2)} €, Gesamt ${(shipped.totalWithTax / 100).toFixed(2)} €)`);

  // 8) → ArrangingPayment
  const trans = await gql(
    `mutation($s:String!){ transitionOrderToState(state:$s){ __typename ... on Order { state } ... on OrderStateTransitionError { errorCode message transitionError } } }`,
    { s: 'ArrangingPayment' });
  const arranging = assertOrder(trans.transitionOrderToState, 'transitionOrderToState');
  check(arranging.state === 'ArrangingPayment', `Order in ${arranging.state}`);

  // 9) eligiblePaymentMethods
  const pm = await gql(`{ eligiblePaymentMethods { code name isEligible } }`);
  const eligible = pm.eligiblePaymentMethods.filter(m => m.isEligible);
  check(eligible.length > 0, `Zahlarten verfügbar: ${eligible.map(m => m.name).join(', ')}`);

  // 10) Zahlung (Rechnung / dummyPaymentHandler, automaticSettle)
  const pay = await gql(
    `mutation($i:PaymentInput!){ addPaymentToOrder(input:$i){ __typename ... on Order { code state } ... on ErrorResult { errorCode message } } }`,
    { i: { method: 'rechnung', metadata: {} } });
  const paid = assertOrder(pay.addPaymentToOrder, 'addPaymentToOrder');
  check(['PaymentSettled', 'PaymentAuthorized'].includes(paid.state),
    `Bestellung ${paid.code} abgeschlossen — Status ${paid.state}`);

  console.log('\n✅ F1-Checkout komplett grün.');
}

main().catch(e => { console.error('\n' + e.message); process.exit(1); });
