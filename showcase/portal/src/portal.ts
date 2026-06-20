// Bindung an die per orval GENERIERTE party-Schicht (axios-Funktionen + TS-Modelle aus /q/openapi).
// Same-origin (Vite-Proxy / nginx → /party + /q) + Auth über den orval-Mutator src/api/http.ts.
// Öffentliche Anfrage ohne Login; die übrigen Aufrufe verlangen einen Login (Token im Mutator).
import type { AxiosError } from 'axios';
import {
  postPartyAnfragenAusbildungsbetrieb, postPartyPersonenLogin,
  getPartyPersonenIdKontexte, getPartyFirmensichtOrganisationId,
} from '@/api/endpoints/party-resource/party-resource';
import {
  postPartyPortalAzubiAnmeldung, postPartyPortalAnmeldungenIdVertragBestaetigen,
} from '@/api/endpoints/portal-resource/portal-resource';
import {
  getPartyPortalRechnungsKontexte, getPartyPortalRechnungen, getPartyPortalRechnungenIdZugferd,
} from '@/api/endpoints/rechnung-portal-resource/rechnung-portal-resource';
import { getLmsPortalTrainings } from '@/api/endpoints/lms-portal/lms-portal';
import {
  getKommunikationPortalEreignisse, getKommunikationPortalUngelesen,
  postKommunikationPortalEreignisseIdGelesen, postKommunikationPortalEreignisseIdBestaetigen,
  getKommunikationPortalPraeferenzen, putKommunikationPortalPraeferenzenKanal,
  getKommunikationPortalKonversationen, getKommunikationPortalKonversationenUngelesen,
  getKommunikationPortalKonversationenIdNachrichten, postKommunikationPortalKonversationenIdNachrichten,
  postKommunikationPortalKonversationenIdGelesen, postKommunikationPortalBeratung,
} from '@/api/endpoints/kommunikation-resource/kommunikation-resource';
import { Kanal } from '@/api/model';
import type {
  AusbildungsbetriebAnfrage, AzubiAnmeldungDto, BuchungZeile, KontextView, Login, PersonView,
  PortalRechnungView, RechnungsKontextView, MeinTrainingView, EreignisView, PraeferenzView,
  KonversationView, NachrichtView,
} from '@/api/model';

export { Kanal };
export type {
  AusbildungsbetriebAnfrage, AzubiAnmeldungDto, BuchungZeile, KontextView, PersonView,
  PortalRechnungView, RechnungsKontextView, MeinTrainingView, EreignisView, PraeferenzView,
  KonversationView, NachrichtView,
};

/** Fehler mit HTTP-Status, damit Views 401 (→ Login) / 403 / 429 unterscheiden können. */
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

// ── Öffentlich: Ausbildungsbetrieb-Anfrage (Lead, kein Login) ──
export const anfrageStellen = (body: AusbildungsbetriebAnfrage) =>
  run(() => postPartyAnfragenAusbildungsbetrieb(body));

// ── Login-gebunden ──
/** Claimt/aktiviert die Person zum Token und liefert sie (mit id). */
export const partyLogin = async (body: Login): Promise<PersonView> =>
  (await run(() => postPartyPersonenLogin(body))) as PersonView;

export const kontexte = async (personId: number): Promise<KontextView[]> =>
  ((await run(() => getPartyPersonenIdKontexte(personId))) as KontextView[]) ?? [];

export const firmensicht = async (organisationId: number): Promise<BuchungZeile[]> =>
  ((await run(() => getPartyFirmensichtOrganisationId(organisationId))) as BuchungZeile[]) ?? [];

export const azubiAnmelden = (body: AzubiAnmeldungDto) =>
  run(() => postPartyPortalAzubiAnmeldung(body));

export const vertragBestaetigen = (id: number) =>
  run(() => postPartyPortalAnmeldungenIdVertragBestaetigen(id));

// ── Rechnungsabruf (Self-Service): Kontexte wählen, Belege listen, ZUGFeRD-PDF laden ──
export const rechnungsKontexte = async (): Promise<RechnungsKontextView[]> =>
  ((await run(() => getPartyPortalRechnungsKontexte())) as RechnungsKontextView[]) ?? [];

/** Festgeschriebene Belege eines Kontexts; ohne organisationId ⇒ privat (Selbstzahler). */
export const meineRechnungen = async (organisationId?: number): Promise<PortalRechnungView[]> =>
  ((await run(() => getPartyPortalRechnungen(organisationId == null ? undefined : { organisationId }))) as PortalRechnungView[]) ?? [];

/** ZUGFeRD-E-Rechnung als Blob (für den Download); orval setzt responseType 'blob', Auth via Mutator. */
export const rechnungPdf = (id: number): Promise<Blob> =>
  run(() => getPartyPortalRechnungenIdZugferd(id));

// ── Meine Trainings (WBT/LMS): eigene Einschreibungen + SSO-Launch-Deeplink ──
/** WBT-Einschreibungen des eingeloggten Lernenden (eigen-skopiert über den Token-sub). */
export const meineTrainings = async (): Promise<MeinTrainingView[]> =>
  ((await run(() => getLmsPortalTrainings())) as MeinTrainingView[]) ?? [];

// ── Meine Aktivitäten (System→Person): Aktivitätslog/Zeitstrahl, Ungelesen-Badge, Quittierung, Kanäle ──
/** Personenseitiger Aktivitätslog (neueste zuerst), eigen-skopiert über den Token-sub. */
export const meineAktivitaeten = async (): Promise<EreignisView[]> =>
  ((await run(() => getKommunikationPortalEreignisse())) as EreignisView[]) ?? [];

/** Anzahl ungelesener Portal-Benachrichtigungen (Badge). */
export const ungelesenAnzahl = async (): Promise<number> =>
  (await run(() => getKommunikationPortalUngelesen()))?.anzahl ?? 0;

/** Markiert ein Ereignis als gelesen (Lese-Zeitstempel). */
export const ereignisGelesen = (id: number) =>
  run(() => postKommunikationPortalEreignisseIdGelesen(id));

/** Pflicht-Bestätigung „zur Kenntnis genommen" (Nachweis). */
export const ereignisBestaetigen = (id: number) =>
  run(() => postKommunikationPortalEreignisseIdBestaetigen(id));

/** Kanal-Präferenzen (gesetzte Zeilen; nicht gesetzte Kanäle gelten als aktiv). */
export const kanalPraeferenzen = async (): Promise<PraeferenzView[]> =>
  ((await run(() => getKommunikationPortalPraeferenzen())) as PraeferenzView[]) ?? [];

/** Schaltet einen Kanal (EMAIL/SMS) an/aus (PORTAL ist nicht abschaltbar). */
export const setzeKanalPraeferenz = (kanal: Kanal, aktiv: boolean) =>
  run(() => putKommunikationPortalPraeferenzenKanal(kanal, { aktiv }));

// ── Meine Nachrichten (Admin↔Person-Threads, K2): eigene Vorgänge, Verlauf, Antwort, Read-Receipt ──
/** Eigene Threads (Nachrichten-Inbox), neueste Aktivität zuerst. */
export const meineKonversationen = async (): Promise<KonversationView[]> =>
  ((await run(() => getKommunikationPortalKonversationen())) as KonversationView[]) ?? [];

/** Anzahl Threads mit ungelesenen Nachrichten (Inbox-Badge). */
export const konversationenUngelesen = async (): Promise<number> =>
  (await run(() => getKommunikationPortalKonversationenUngelesen()))?.anzahl ?? 0;

/** Verlauf eines eigenen Threads (chronologisch). */
export const threadNachrichten = async (id: number): Promise<NachrichtView[]> =>
  ((await run(() => getKommunikationPortalKonversationenIdNachrichten(id))) as NachrichtView[]) ?? [];

/** Eigene Antwort in einem Thread. */
export const threadAntworten = (id: number, inhaltHtml: string) =>
  run(() => postKommunikationPortalKonversationenIdNachrichten(id, { inhaltHtml }));

/** Markiert einen Thread als gelesen. */
export const threadGelesen = (id: number) =>
  run(() => postKommunikationPortalKonversationenIdGelesen(id));

// ── KI-Studienberatung (K4): autonomer FAQ-Bot — Frage stellen, Bot antwortet sofort (KI-gekennzeichnet) ──
/** Startet eine KI-Beratung mit der Frage; liefert den neuen Bot-Thread (Antwort bereits enthalten). */
export const kiBeratung = async (frage: string): Promise<KonversationView> =>
  (await run(() => postKommunikationPortalBeratung({ frage }))) as KonversationView;
