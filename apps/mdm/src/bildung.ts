// Zentrale per-Typ-Konfiguration ("Frontend-Registry"): bindet die per orval GENERIERTE Schicht
// (axios-Funktionen + TS-Modelle/Enums + zod aus /q/openapi) an die Oberfläche. Eine gemeinsame
// Feldliste (Stammdaten) + je Typ die spezifischen Felder + das passende zod-Schema + CRUD.
// Same-origin (Vite-Proxy / nginx) + Auth laufen über den orval-Mutator src/api/http.ts.
import type { AxiosError } from 'axios';
import {
  getBildungSeminare, getBildungSeminareId, postBildungSeminare, putBildungSeminareId, deleteBildungSeminareId,
  getBildungTagungen, getBildungTagungenId, postBildungTagungen, putBildungTagungenId, deleteBildungTagungenId,
  getBildungBerufsschuljahre, getBildungBerufsschuljahreId, postBildungBerufsschuljahre, putBildungBerufsschuljahreId, deleteBildungBerufsschuljahreId,
  getBildungStudiengaenge, getBildungStudiengaengeId, postBildungStudiengaenge, putBildungStudiengaengeId, deleteBildungStudiengaengeId,
  postBildungAngeboteIdShopProjektion,
} from '@/api/endpoints/bildung-resource/bildung-resource';
import {
  Bereich, AngebotStatus, PreisModell, SeminarKategorie, Studienabschluss, Studienform, BildungsangebotTyp,
} from '@/api/model';
import type { SeminarDto, TagungDto, BerufsschuljahrDto, StudiengangDto } from '@/api/model';
import {
  PostBildungSeminareBody, PostBildungTagungenBody, PostBildungBerufsschuljahreBody, PostBildungStudiengaengeBody,
} from '@/api/zod/bildung-resource/bildung-resource.zod';

export type Typ = BildungsangebotTyp;
export type AngebotDto = SeminarDto | TagungDto | BerufsschuljahrDto | StudiengangDto;

export type FeldArt = 'text' | 'textarea' | 'number' | 'date' | 'checkbox' | 'select';

export interface FeldDef {
  name: string;
  label: string;
  art: FeldArt;
  options?: readonly string[];
}

// Enum-Optionen für die Dropdowns aus den generierten TS-Enums (enumGenerationType: 'enum').
const opts = <T extends Record<string, string>>(e: T): string[] => Object.values(e);

/** Gemeinsame Stammdaten-Felder — in jeder Typ-Maske identisch (eine wiederverwendete Komponente). */
export const gemeinsameFelder: FeldDef[] = [
  { name: 'code', label: 'Code', art: 'text' },
  { name: 'titel', label: 'Titel', art: 'text' },
  { name: 'bereich', label: 'Bereich', art: 'select', options: opts(Bereich) },
  { name: 'status', label: 'Status', art: 'select', options: opts(AngebotStatus) },
  { name: 'preisModell', label: 'Preismodell', art: 'select', options: opts(PreisModell) },
  { name: 'preisCent', label: 'Preis je Rate (Cent)', art: 'number' },
  { name: 'abrechnungIntervallMonate', label: 'Abrechnungsintervall (Monate)', art: 'number' },
  { name: 'ratenGesamt', label: 'Raten gesamt (0 = unbefristet)', art: 'number' },
  { name: 'gueltigAb', label: 'Gültig ab', art: 'date' },
  { name: 'gueltigBis', label: 'Gültig bis', art: 'date' },
  { name: 'verantwortlich', label: 'Verantwortlich', art: 'text' },
  { name: 'zielgruppe', label: 'Zielgruppe', art: 'text' },
  { name: 'shopVerkauf', label: 'Im Shop verkaufen', art: 'checkbox' },
  { name: 'kurzbeschreibung', label: 'Kurzbeschreibung', art: 'textarea' },
];

// Hey-api-kompatible Result-Hülle {data,error,response}, damit die Views unverändert bleiben.
// orval-Funktionen liefern die Daten direkt bzw. werfen AxiosError bei non-2xx.
type ApiResult = Promise<{ data?: unknown; error?: unknown; response?: { status: number; ok: boolean } }>;
async function call(fn: () => Promise<unknown>): ApiResult {
  try {
    return { data: await fn() };
  } catch (e) {
    const ax = e as AxiosError;
    return ax.response
      ? { error: ax.response.data, response: { status: ax.response.status, ok: false } }
      : { error: e };
  }
}

interface TypConfig {
  label: string;
  pfad: string;
  schema: unknown; // orval-zod (Body) — als toTypedSchema-Eingabe in der Maske verwendet
  felder: FeldDef[];
  list: () => ApiResult;
  byId: (id: number) => ApiResult;
  create: (body: AngebotDto) => ApiResult;
  update: (id: number, body: AngebotDto) => ApiResult;
  remove: (id: number) => ApiResult;
}

export const typen: Record<Typ, TypConfig> = {
  SEMINAR: {
    label: 'Seminar',
    pfad: 'seminare',
    schema: PostBildungSeminareBody,
    felder: [
      { name: 'kategorie', label: 'Kategorie', art: 'select', options: opts(SeminarKategorie) },
      { name: 'dauerUE', label: 'Dauer (UE)', art: 'number' },
      { name: 'minTN', label: 'Min. Teilnehmer', art: 'number' },
      { name: 'maxTN', label: 'Max. Teilnehmer', art: 'number' },
      { name: 'abschluss', label: 'Abschluss', art: 'text' },
      { name: 'zertifikat', label: 'Zertifikat', art: 'checkbox' },
    ],
    list: () => call(() => getBildungSeminare()),
    byId: (id) => call(() => getBildungSeminareId(id)),
    create: (body) => call(() => postBildungSeminare(body as SeminarDto)),
    update: (id, body) => call(() => putBildungSeminareId(id, body as SeminarDto)),
    remove: (id) => call(() => deleteBildungSeminareId(id)),
  },
  TAGUNG: {
    label: 'Tagung',
    pfad: 'tagungen',
    schema: PostBildungTagungenBody,
    felder: [
      { name: 'thema', label: 'Thema', art: 'text' },
      { name: 'terminVon', label: 'Termin von', art: 'date' },
      { name: 'terminBis', label: 'Termin bis', art: 'date' },
      { name: 'ort', label: 'Ort', art: 'text' },
      { name: 'programmUrl', label: 'Programm-URL', art: 'text' },
      { name: 'maxTN', label: 'Max. Teilnehmer', art: 'number' },
    ],
    list: () => call(() => getBildungTagungen()),
    byId: (id) => call(() => getBildungTagungenId(id)),
    create: (body) => call(() => postBildungTagungen(body as TagungDto)),
    update: (id, body) => call(() => putBildungTagungenId(id, body as TagungDto)),
    remove: (id) => call(() => deleteBildungTagungenId(id)),
  },
  BERUFSSCHULJAHR: {
    label: 'Berufsschuljahr',
    pfad: 'berufsschuljahre',
    schema: PostBildungBerufsschuljahreBody,
    felder: [
      { name: 'fachrichtung', label: 'Fachrichtung', art: 'text' },
      { name: 'schuljahr', label: 'Schuljahr (JJJJ/JJ)', art: 'text' },
      { name: 'jahrgang', label: 'Jahrgang', art: 'number' },
      { name: 'beginn', label: 'Beginn', art: 'date' },
      { name: 'schildNrwSchluessel', label: 'Schild-NRW-Schlüssel', art: 'text' },
      { name: 'plaetze', label: 'Plätze', art: 'number' },
    ],
    list: () => call(() => getBildungBerufsschuljahre()),
    byId: (id) => call(() => getBildungBerufsschuljahreId(id)),
    create: (body) => call(() => postBildungBerufsschuljahre(body as BerufsschuljahrDto)),
    update: (id, body) => call(() => putBildungBerufsschuljahreId(id, body as BerufsschuljahrDto)),
    remove: (id) => call(() => deleteBildungBerufsschuljahreId(id)),
  },
  STUDIENGANG: {
    label: 'Studiengang',
    pfad: 'studiengaenge',
    schema: PostBildungStudiengaengeBody,
    felder: [
      { name: 'abschluss', label: 'Abschluss', art: 'select', options: opts(Studienabschluss) },
      { name: 'studienform', label: 'Studienform', art: 'select', options: opts(Studienform) },
      { name: 'startsemester', label: 'Startsemester (WS/SS+Jahr)', art: 'text' },
      { name: 'regelstudienzeitSemester', label: 'Regelstudienzeit (Sem.)', art: 'number' },
      { name: 'akkreditierungBis', label: 'Akkreditierung bis', art: 'date' },
      { name: 'plaetze', label: 'Plätze', art: 'number' },
    ],
    list: () => call(() => getBildungStudiengaenge()),
    byId: (id) => call(() => getBildungStudiengaengeId(id)),
    create: (body) => call(() => postBildungStudiengaenge(body as StudiengangDto)),
    update: (id, body) => call(() => putBildungStudiengaengeId(id, body as StudiengangDto)),
    remove: (id) => call(() => deleteBildungStudiengaengeId(id)),
  },
};

export const alleTypen = Object.keys(typen) as Typ[];

/**
 * Shop-Projektion (P1.3, §11.6): stößt das Anlegen/Aktualisieren des Vendure-Produkts an und
 * bekommt die vendureProductId zurück. Typ-übergreifend → ein Endpunkt über der Registry-ID.
 */
export const projiziereInShop = (id: number) => call(() => postBildungAngeboteIdShopProjektion(id));

/** Sinnvolle Defaults für ein neues Angebot eines Typs (Pflichtfelder vorbelegt). */
export function leeresAngebot(typ: Typ): Record<string, unknown> {
  const base: Record<string, unknown> = {
    typ,
    code: '',
    titel: '',
    bereich: typ === 'BERUFSSCHULJAHR' ? 'BERUFSSCHULE' : typ === 'STUDIENGANG' ? 'HOCHSCHULE' : 'AKADEMIE',
    status: 'ENTWURF',
    preisModell: 'EINMALIG',
    gueltigAb: new Date().toISOString().slice(0, 10),
    shopVerkauf: false,
  };
  if (typ === 'SEMINAR') Object.assign(base, { kategorie: 'SONSTIGE', dauerUE: 1, minTN: 0, maxTN: 1, zertifikat: false });
  if (typ === 'TAGUNG') Object.assign(base, { thema: '', terminVon: base.gueltigAb, maxTN: 1 });
  if (typ === 'BERUFSSCHULJAHR') Object.assign(base, { fachrichtung: '', schuljahr: '', jahrgang: 1, plaetze: 0 });
  if (typ === 'STUDIENGANG') Object.assign(base, { abschluss: 'BACHELOR', studienform: 'VOLLZEIT', startsemester: '', regelstudienzeitSemester: 1, plaetze: 0 });
  return base;
}

/**
 * Mappt eine 400-Violation-Antwort (Quarkus) auf Feld→Meldung. Die Cross-Field-Regel
 * (gueltigBis ≥ gueltigAb) kommt NUR hierüber (server-seitig, nicht im zod-Schema, §11.9-D).
 */
export function violationsZuFehlern(error: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  const vs = (error as { violations?: { field?: string; message?: string }[] } | undefined)?.violations;
  if (Array.isArray(vs)) {
    for (const v of vs) {
      const feld = (v.field ?? '').split('.').pop() ?? '';
      if (feld) out[feld] = v.message ?? 'ungültig';
    }
  }
  return out;
}
