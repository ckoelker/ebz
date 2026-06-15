// HITL-Cockpit (Anmeldung Berufsschule, Schritt I): bindet die per orval GENERIERTE party-Schicht
// (Typen + axios-Funktionen aus /q/openapi) an die Oberfläche — Dubletten-Review-Queue + Entscheidung
// sowie offene Anmeldungen + EBZ-Bestätigung. Same-origin/Auth über den orval-Mutator src/api/http.ts.
import type { AxiosError } from 'axios';
import {
  getPartyReviewsQueue, postPartyReviewsEntscheidung,
} from '@/api/endpoints/dubletten-review-resource/dubletten-review-resource';
import {
  getPartyAnmeldungen, postPartyAnmeldungenIdBestaetigung,
} from '@/api/endpoints/anmeldung-workflow-resource/anmeldung-workflow-resource';
import type { AnmeldungStatus, EntscheidungDto, Fall, OffeneAnmeldungView } from '@/api/model';

export type { EntscheidungDto, Fall, OffeneAnmeldungView };

/** Fehler mit HTTP-Status, damit die Views 401 (→ Login) / 403 unterscheiden können. */
export class ApiFehler extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

// orval-Funktionen werfen AxiosError bei non-2xx → in ApiFehler (mit Status) übersetzen.
async function run<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    const ax = e as AxiosError<{ message?: string }>;
    const status = ax.response?.status;
    throw new ApiFehler(ax.response?.data?.message ?? `Anfrage fehlgeschlagen (HTTP ${status ?? '?'}).`, status);
  }
}

// ── Dubletten-Review ──
export const reviewQueue = async (): Promise<Fall[]> =>
  ((await run(() => getPartyReviewsQueue())) as Fall[]) ?? [];

export const entscheide = (body: EntscheidungDto) => run(() => postPartyReviewsEntscheidung(body));

// ── Offene Anmeldungen + EBZ-Bestätigung ──
export const offeneAnmeldungen = async (status?: AnmeldungStatus): Promise<OffeneAnmeldungView[]> =>
  ((await run(() => getPartyAnmeldungen(status ? { status } : undefined))) as OffeneAnmeldungView[]) ?? [];

export const bestaetigeAnmeldung = (id: number) => run(() => postPartyAnmeldungenIdBestaetigung(id));

/** Anzeige-/Farb-Mapping der KI-Einschätzung (MATCH = wahrscheinlich Dublette → Achtung). */
export const einschaetzungSchwere: Record<string, 'danger' | 'warn' | 'secondary'> = {
  MATCH: 'danger',
  UNSICHER: 'warn',
  KEIN_MATCH: 'secondary',
};
