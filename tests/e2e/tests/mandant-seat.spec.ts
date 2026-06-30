import { test, expect, APIRequestContext } from '@playwright/test';
import { URLS } from './helpers/sso';

/**
 * M5 (K2) — Seat-Cap: der B2B-Mandant DEMO_AG (ENTERPRISE_FLAT) wird gegen sein seatLimit überbucht.
 * Live-Durchstich gegen den Stack (echtes OpenOLAT für die Belegung, Backend-Endpunkte mit Staff-OIDC,
 * Rolle mandant-pflege): die Überbuchung wird WEICH durchgelassen, erzeugt aber eine HITL-Meldung, die
 * im Cockpit bestätigbar ist. EBZ-Kontexte sind unbegrenzt (separat per SeatLimitTest abgedeckt).
 *
 * Die Belegung = OpenOLAT-Org-Mitglieder in Rolle `user`; der Test nimmt deterministisch genau einen
 * Test-Member auf (administrator), setzt das Limit darunter und räumt ihn danach wieder ab (keine
 * bleibende Verschiebung der DEMO_AG-Baseline).
 */

const KC = process.env.KEYCLOAK_URL ?? 'http://127.0.0.1:8080';
const KC_HOST = process.env.KEYCLOAK_HOST ?? 'keycloak.localhost:8080';
const API = process.env.API_URL ?? 'http://localhost:8090';
const OL = URLS.openolat;
const OL_ADMIN = 'Basic ' + Buffer.from('administrator:openolat').toString('base64');
const OLH = { Authorization: OL_ADMIN, Accept: 'application/json' };

async function staffToken(request: APIRequestContext): Promise<string> {
  const res = await request.post(`${KC}/realms/ebz-staff/protocol/openid-connect/token`, {
    headers: { Host: KC_HOST },
    form: { grant_type: 'password', client_id: 'staff-frontend', username: 'staff', password: 'staff' },
  });
  expect(res.ok(), 'Staff-Token').toBeTruthy();
  return (await res.json()).access_token as string;
}

test('M5: Seat-Cap überbucht weich + HITL-Meldung (live gegen OpenOLAT-Org)', async ({ request }) => {
  const token = await staffToken(request);
  const auth = { Authorization: `Bearer ${token}` };

  // DEMO_AG (B2B) auflösen — nur ENTERPRISE_FLAT hat ein Seat-Limit.
  const mandanten = await (await request.get(`${API}/mandant`, { headers: auth })).json();
  const demo = (mandanten as { id: number; schluessel: string; vertragstyp: string; openolatOrganisationKey: number }[])
    .find((m) => m.schluessel === 'DEMO_AG');
  expect(demo, 'DEMO_AG geseedet').toBeTruthy();
  expect(demo!.vertragstyp, 'DEMO_AG ist ENTERPRISE_FLAT (seat-begrenzt)').toBe('ENTERPRISE_FLAT');
  expect(demo!.openolatOrganisationKey, 'DEMO_AG hat eine OpenOLAT-Org').toBeTruthy();
  const mid = demo!.id;
  const orgKey = demo!.openolatOrganisationKey;

  // Einen Org-Member (administrator) in Rolle `user` deterministisch aufnehmen → Belegung >= 1.
  const adminKey = (await (await request.get(`${OL}/restapi/users?login=administrator`, { headers: OLH })).json())[0].key as number;
  await request.put(`${OL}/restapi/organisations/${orgKey}/user/${adminKey}`, { headers: OLH });

  try {
    const vor = await (await request.get(`${API}/mandant/${mid}/seats`, { headers: auth })).json();
    const belegung = vor.belegung as number;
    expect(belegung, 'Belegung aus echten OpenOLAT-Org-Mitgliedern').toBeGreaterThanOrEqual(1);

    // Limit unter die Belegung legen → überbucht.
    const limit = belegung - 1;
    const liz = await request.post(`${API}/mandant/${mid}/lizenzen`, {
      headers: auth, data: { seatLimit: limit, gueltigVon: '2026-01-01', aktiv: true },
    });
    expect(liz.ok(), 'Seat-Lizenz hinterlegt').toBeTruthy();

    // Report zeigt die Überbuchung korrekt.
    const seats = await (await request.get(`${API}/mandant/${mid}/seats`, { headers: auth })).json();
    expect(seats.begrenzt, 'seat-begrenzt').toBeTruthy();
    expect(seats.belegung, 'Belegung korrekt').toBe(belegung);
    expect(seats.ueberbucht, 'überbucht (Belegung > Limit)').toBeTruthy();

    // Aufnahme eines weiteren Mitglieds: WEICH durchgelassen, aber HITL-Meldung.
    const auf = await (await request.post(`${API}/mandant/${mid}/seat-aufnahme`, { headers: auth })).json();
    expect(auf.entscheidung, 'Überbuchung durchgelassen').toBe('UEBERBUCHT');
    expect(auf.meldungId, 'HITL-Meldung erzeugt').toBeTruthy();

    // Meldung steht in der offenen HITL-Liste.
    const meldungen = await (await request.get(`${API}/mandant/seat-meldungen`, { headers: auth })).json();
    const meld = (meldungen as { id: number; mandantId: number; status: string }[]).find((x) => x.id === auf.meldungId);
    expect(meld, 'HITL-Meldung offen gelistet').toBeTruthy();
    expect(meld!.mandantId).toBe(mid);
    expect(meld!.status).toBe('OFFEN');

    // Bestätigen (HITL-Aktion) → BESTAETIGT, Bearbeiter = Staff-Principal.
    const best = await request.post(`${API}/mandant/seat-meldungen/${auf.meldungId}/bestaetigen`, { headers: auth });
    expect(best.ok(), 'Meldung bestätigt').toBeTruthy();
    expect((await best.json()).status).toBe('BESTAETIGT');
  } finally {
    // Test-Member wieder abräumen (DEMO_AG-Baseline unverändert lassen).
    await request.delete(`${OL}/restapi/organisations/${orgKey}/user/${adminKey}`, { headers: OLH }).catch(() => {});
  }
});
