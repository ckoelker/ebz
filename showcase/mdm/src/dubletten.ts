// HITL-Cockpit (Anmeldung Berufsschule, Schritt I): bindet die GENERIERTE party-Schicht (Typen +
// SDK aus /q/openapi) an die Oberfläche — Dubletten-Review-Queue + Entscheidung sowie offene
// Anmeldungen + EBZ-Bestätigung. Same-origin über den Vite-Proxy (/party), daher baseUrl ''.
import { client } from './gen/client.gen';
import * as sdk from './gen/sdk.gen';
import type { AnmeldungStatus, EntscheidungDto, Fall, OffeneAnmeldungView } from './gen/types.gen';

client.setConfig({ baseUrl: '' });

export type { EntscheidungDto, Fall, OffeneAnmeldungView };

/** Fehler mit HTTP-Status, damit die Views 401 (→ Login) / 403 unterscheiden können. */
export class ApiFehler extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

function ergebnis<T>(r: { data?: T; error?: unknown; response?: Response }): T {
  if (r.error || (r.response && !r.response.ok)) {
    const status = r.response?.status;
    const msg = (r.error as { message?: string } | undefined)?.message;
    throw new ApiFehler(msg ?? `Anfrage fehlgeschlagen (HTTP ${status ?? '?'}).`, status);
  }
  return r.data as T;
}

// ── Dubletten-Review ──
export const reviewQueue = async (): Promise<Fall[]> => ergebnis(await sdk.getPartyReviewsQueue()) ?? [];

export const entscheide = (body: EntscheidungDto) =>
  sdk.postPartyReviewsEntscheidung({ body }).then(ergebnis);

// ── Offene Anmeldungen + EBZ-Bestätigung ──
export const offeneAnmeldungen = async (status?: AnmeldungStatus): Promise<OffeneAnmeldungView[]> =>
  ergebnis(await sdk.getPartyAnmeldungen({ query: status ? { status } : {} })) ?? [];

export const bestaetigeAnmeldung = (id: number) =>
  sdk.postPartyAnmeldungenByIdBestaetigung({ path: { id } }).then(ergebnis);

/** Anzeige-/Farb-Mapping der KI-Einschätzung (MATCH = wahrscheinlich Dublette → Achtung). */
export const einschaetzungSchwere: Record<string, 'danger' | 'warn' | 'secondary'> = {
  MATCH: 'danger',
  UNSICHER: 'warn',
  KEIN_MATCH: 'secondary',
};
