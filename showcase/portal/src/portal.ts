// Bindung an die GENERIERTE party-Schicht (Typen + SDK aus /q/openapi). Same-origin über den
// Vite-Proxy (/party) → baseUrl ''. Öffentliche Anfrage ohne Login; die übrigen Aufrufe verlangen
// einen Login (Token via auth.ts-Interceptor).
import { client } from './gen/client.gen';
import * as sdk from './gen/sdk.gen';
import type {
  AusbildungsbetriebAnfrage,
  AzubiAnmeldungDto,
  BuchungZeile,
  KontextView,
  Login,
  PersonView,
} from './gen/types.gen';

client.setConfig({ baseUrl: '' });

export type { AusbildungsbetriebAnfrage, AzubiAnmeldungDto, BuchungZeile, KontextView, PersonView };

/** Fehler mit HTTP-Status, damit Views 401 (→ Login) / 403 / 429 unterscheiden können. */
export class ApiFehler extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

// data ist bei Response-Endpunkten (JAX-RS) als `unknown` generiert → Param entkoppelt, Rückgabe cast.
function ergebnis<T>(r: { data?: unknown; error?: unknown; response?: Response }): T {
  if (r.error || (r.response && !r.response.ok)) {
    const status = r.response?.status;
    const msg = (r.error as { message?: string } | undefined)?.message;
    throw new ApiFehler(msg ?? `Anfrage fehlgeschlagen (HTTP ${status ?? '?'}).`, status);
  }
  return r.data as T;
}

// ── Öffentlich: Ausbildungsbetrieb-Anfrage (Lead, kein Login) ──
export const anfrageStellen = (body: AusbildungsbetriebAnfrage) =>
  sdk.postPartyAnfragenAusbildungsbetrieb({ body }).then(ergebnis);

// ── Login-gebunden ──
/** Claimt/aktiviert die Person zum Token und liefert sie (mit id). */
export const partyLogin = async (body: Login): Promise<PersonView> =>
  ergebnis<PersonView>(await sdk.postPartyPersonenLogin({ body }));

export const kontexte = async (personId: number): Promise<KontextView[]> =>
  ergebnis<KontextView[]>(await sdk.getPartyPersonenByIdKontexte({ path: { id: personId } })) ?? [];

export const firmensicht = async (organisationId: number): Promise<BuchungZeile[]> =>
  ergebnis<BuchungZeile[]>(await sdk.getPartyFirmensichtByOrganisationId({ path: { organisationId } })) ?? [];

export const azubiAnmelden = (body: AzubiAnmeldungDto) =>
  sdk.postPartyPortalAzubiAnmeldung({ body }).then(ergebnis);

export const vertragBestaetigen = (id: number) =>
  sdk.postPartyPortalAnmeldungenByIdVertragBestaetigen({ path: { id } }).then(ergebnis);
