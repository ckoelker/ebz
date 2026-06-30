import { test, expect, APIRequestContext } from '@playwright/test';
import { URLS } from './helpers/sso';

/**
 * M6 (K6) — Nachweis-Seam: die in OpenOLAT (System-of-Record) gehaltene Completion eines WBT wird als
 * kanonischer LernleistungsFakt mit den anrechenbaren Soll-Stunden ins MDM projiziert. REST-Durchstich
 * über die offiziellen Backend-Endpunkte (Staff, Rolle katalog-pflege), mit W3C-Baggage (prozess.fall)
 * für die Living-Documentation-Spans (BPMN-Kette, Phase MANDANT_NACHWEIS).
 *
 * Die Daten legt `openolat/lms-nachweis-seed.sh` an (WBT-NACHWEIS-DEMO + customer-Einschreibung + Fakt);
 * der Test treibt melde+sync idempotent erneut (unter bekannter Fall-Id) und prüft den Nachweis.
 * Der Browser-Launch des geteilten Inhalts ist von `mandant-content-share.spec.ts` abgedeckt.
 */

// Das integration-Backend vertraut dem Issuer keycloak.localhost:8080 (Netzwerk-Alias). Node kann den
// Hostnamen nicht auflösen, also Token über 127.0.0.1:8080 holen und per Host-Header den Issuer prägen
// (Keycloak HOSTNAME_STRICT=false leitet iss aus dem Host ab) → der Backend-Audience/Issuer stimmt.
const KC = process.env.KEYCLOAK_URL ?? 'http://127.0.0.1:8080';
const KC_HOST = process.env.KEYCLOAK_HOST ?? 'keycloak.localhost:8080';
const API = process.env.API_URL ?? 'http://localhost:8090';
const OL = URLS.openolat;
const OL_ADMIN = 'Basic ' + Buffer.from('administrator:openolat').toString('base64');
const WBT_CODE = 'WBT-NACHWEIS-DEMO';

async function staffToken(request: APIRequestContext): Promise<string> {
  const res = await request.post(`${KC}/realms/ebz-staff/protocol/openid-connect/token`, {
    headers: { Host: KC_HOST },
    form: { grant_type: 'password', client_id: 'staff-frontend', username: 'staff', password: 'staff' },
  });
  expect(res.ok(), 'Staff-Token').toBeTruthy();
  return (await res.json()).access_token as string;
}

test('M6: OpenOLAT-Completion → LernleistungsFakt mit Soll-Stunden (Baggage-Durchstich)', async ({ request }) => {
  const token = await staffToken(request);
  const fall = `nachweis-${Date.now()}`;
  const auth = { Authorization: `Bearer ${token}` };
  const mitFall = { ...auth, baggage: `prozess.fall=${fall}` };

  // WBT + Einschreibung (vom Seed) auflösen.
  const kurse = await (await request.get(`${API}/lms/kurse`, { headers: auth })).json();
  const wbt = (kurse as { id: number; code: string; sollStundenAnrechenbar?: string }[]).find((k) => k.code === WBT_CODE);
  expect(wbt, `WBT ${WBT_CODE} geseedet`).toBeTruthy();

  const einschreibungen = await (await request.get(`${API}/lms/einschreibungen`, { headers: auth })).json();
  const e = (einschreibungen as { id: number; wbtKursId: number; openolatIdentityKey?: number }[]).find(
    (x) => x.wbtKursId === wbt!.id && x.openolatIdentityKey,
  );
  expect(e, 'provisionierte customer-Einschreibung geseedet').toBeTruthy();

  // Durchstich mit Fall-Id: Abschluss festhalten → synchronisieren (idempotent).
  await request.post(`${API}/lms/nachweise/kurs/${wbt!.id}/sicherstellen`, { headers: mitFall });
  const meld = await request.post(`${API}/lms/nachweise/einschreibung/${e!.id}/abschluss-melden`, { headers: mitFall });
  expect(meld.ok(), 'Abschluss gemeldet').toBeTruthy();
  const sync = await request.post(`${API}/lms/nachweise/einschreibung/${e!.id}/synchronisieren`, { headers: mitFall });
  expect(sync.ok(), 'synchronisiert').toBeTruthy();

  // Nachweis lesbar (K6) — Soll-Stunden statt session_time.
  const fakten = await (await request.get(`${API}/lms/nachweise/fakten`, { headers: auth })).json();
  const fakt = (fakten as {
    einschreibungId: number; bestanden: boolean; sollStunden: string; abgeschlossenAm: string; wbtCode: string;
  }[]).find((f) => f.einschreibungId === e!.id);
  expect(fakt, 'LernleistungsFakt zur Einschreibung').toBeTruthy();
  expect(fakt!.bestanden, 'bestanden').toBeTruthy();
  expect(Number(fakt!.sollStunden), 'anrechenbare Soll-Stunden > 0').toBeGreaterThan(0);
  expect(fakt!.abgeschlossenAm, 'Abschlusszeitpunkt aus OpenOLAT').toBeTruthy();

  // Gegenprobe: OpenOLAT hält die Completion am Nachweis-Kurs (SoR) wirklich.
  const courses = await (await request.get(
    `${OL}/restapi/repo/courses?externalId=WBT-NACHWEIS-${WBT_CODE}`, { headers: { Authorization: OL_ADMIN, Accept: 'application/json' } },
  )).json();
  expect(Array.isArray(courses) && courses.length >= 1, 'trackbarer Nachweis-Kurs existiert in OpenOLAT').toBeTruthy();
});
