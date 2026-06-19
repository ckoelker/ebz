// Admin↔Person-Nachrichten im Cockpit (K2): bindet die per orval GENERIERTE Admin-Kommunikations-Schicht
// (Threads + Verlauf + Antwort + Co-Pilot-Entwurf) an die Oberfläche. Same-origin/Auth über den
// orval-Mutator src/api/http.ts; Schreiben braucht die Rolle crm-pflege.
import type { AxiosError } from 'axios';
import {
  getKommunikationAdminKonversationen, getKommunikationAdminKonversationenIdNachrichten,
  postKommunikationAdminVorgaenge, postKommunikationAdminKonversationenIdNachrichten,
  postKommunikationAdminKonversationenIdEntwurf, postKommunikationAdminKonversationenIdGelesen,
} from '@/api/endpoints/admin-kommunikation-resource/admin-kommunikation-resource';
import type { KonversationView, NachrichtView, SendenDto } from '@/api/model';

export type { KonversationView, NachrichtView, SendenDto };

/** Fehler mit HTTP-Status, damit die Views 401 (→ Login) / 403 unterscheiden können. */
export class ApiFehler extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

async function run<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    const ax = e as AxiosError<{ message?: string }>;
    const status = ax.response?.status;
    throw new ApiFehler(ax.response?.data?.message ?? `Anfrage fehlgeschlagen (HTTP ${status ?? '?'}).`, status);
  }
}

/** Alle Admin-Vorgänge (Support-Pool), neueste zuerst. */
export const adminKonversationen = async (): Promise<KonversationView[]> =>
  ((await run(() => getKommunikationAdminKonversationen())) as KonversationView[]) ?? [];

/** Verlauf eines Vorgangs (chronologisch). */
export const adminNachrichten = async (id: number): Promise<NachrichtView[]> =>
  ((await run(() => getKommunikationAdminKonversationenIdNachrichten(id))) as NachrichtView[]) ?? [];

/** Neuen Vorgang an eine Person eröffnen (erste Nachricht inklusive). */
export const adminEroeffne = (body: SendenDto) =>
  run(() => postKommunikationAdminVorgaenge(body));

/** Antwort des Mitarbeiters in einem Vorgang. */
export const adminAntworten = (id: number, inhaltHtml: string) =>
  run(() => postKommunikationAdminKonversationenIdNachrichten(id, { inhaltHtml }));

/** Co-Pilot: KI-Antwortvorschlag (HITL — der Mitarbeiter prüft, bearbeitet und sendet selbst). */
export const adminEntwurf = async (id: number): Promise<string> =>
  (await run(() => postKommunikationAdminKonversationenIdEntwurf(id)))?.entwurf ?? '';

/** Markiert einen Vorgang als gelesen. */
export const adminGelesen = (id: number) =>
  run(() => postKommunikationAdminKonversationenIdGelesen(id));
