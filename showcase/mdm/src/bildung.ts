// Zentrale per-Typ-Konfiguration ("Frontend-Registry"): bindet die GENERIERTE Schicht
// (Typen + zod + SDK-Client aus /q/openapi) an die Oberfläche. Eine gemeinsame Feldliste
// (Stammdaten) + je Typ die spezifischen Felder + das passende zod-Schema + die CRUD-Funktionen.
// So bleibt die SPA typsicher und komponiert die Pflege aus EINEM gemeinsamen + EINEM typ-Teil.
import { client } from './gen/client.gen';
import * as sdk from './gen/sdk.gen';
import * as z from './gen/zod.gen';
import type {
  SeminarDto,
  TagungDto,
  BerufsschuljahrDto,
  StudiengangDto,
  BildungsangebotTyp,
} from './gen/types.gen';

// Cockpit läuft same-origin (Vite-Proxy / nginx) → relative URLs statt der generierten
// Default-Basis http://localhost:8090 (sonst CORS). /bildung + /q werden weitergeleitet.
client.setConfig({ baseUrl: '' });

export type Typ = BildungsangebotTyp;
export type AngebotDto = SeminarDto | TagungDto | BerufsschuljahrDto | StudiengangDto;

export type FeldArt = 'text' | 'textarea' | 'number' | 'date' | 'checkbox' | 'select';

export interface FeldDef {
  name: string;
  label: string;
  art: FeldArt;
  options?: readonly string[];
}

/** Gemeinsame Stammdaten-Felder — in jeder Typ-Maske identisch (eine wiederverwendete Komponente). */
export const gemeinsameFelder: FeldDef[] = [
  { name: 'code', label: 'Code', art: 'text' },
  { name: 'titel', label: 'Titel', art: 'text' },
  { name: 'bereich', label: 'Bereich', art: 'select', options: z.zBereich.options },
  { name: 'status', label: 'Status', art: 'select', options: z.zAngebotStatus.options },
  { name: 'preisModell', label: 'Preismodell', art: 'select', options: z.zPreisModell.options },
  { name: 'gueltigAb', label: 'Gültig ab', art: 'date' },
  { name: 'gueltigBis', label: 'Gültig bis', art: 'date' },
  { name: 'verantwortlich', label: 'Verantwortlich', art: 'text' },
  { name: 'zielgruppe', label: 'Zielgruppe', art: 'text' },
  { name: 'shopVerkauf', label: 'Im Shop verkaufen', art: 'checkbox' },
  { name: 'kurzbeschreibung', label: 'Kurzbeschreibung', art: 'textarea' },
];

type ApiResult = Promise<{ data?: unknown; error?: unknown; response?: Response }>;

interface TypConfig {
  label: string;
  pfad: string;
  schema: unknown; // z.Zod* — als toTypedSchema-Eingabe in der Maske verwendet
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
    schema: z.zSeminarDto,
    felder: [
      { name: 'kategorie', label: 'Kategorie', art: 'select', options: z.zSeminarKategorie.options },
      { name: 'dauerUE', label: 'Dauer (UE)', art: 'number' },
      { name: 'minTN', label: 'Min. Teilnehmer', art: 'number' },
      { name: 'maxTN', label: 'Max. Teilnehmer', art: 'number' },
      { name: 'abschluss', label: 'Abschluss', art: 'text' },
      { name: 'zertifikat', label: 'Zertifikat', art: 'checkbox' },
    ],
    list: () => sdk.getBildungSeminare(),
    byId: (id) => sdk.getBildungSeminareById({ path: { id } }),
    create: (body) => sdk.postBildungSeminare({ body: body as SeminarDto }),
    update: (id, body) => sdk.putBildungSeminareById({ path: { id }, body: body as SeminarDto }),
    remove: (id) => sdk.deleteBildungSeminareById({ path: { id } }),
  },
  TAGUNG: {
    label: 'Tagung',
    pfad: 'tagungen',
    schema: z.zTagungDto,
    felder: [
      { name: 'thema', label: 'Thema', art: 'text' },
      { name: 'terminVon', label: 'Termin von', art: 'date' },
      { name: 'terminBis', label: 'Termin bis', art: 'date' },
      { name: 'ort', label: 'Ort', art: 'text' },
      { name: 'programmUrl', label: 'Programm-URL', art: 'text' },
      { name: 'maxTN', label: 'Max. Teilnehmer', art: 'number' },
    ],
    list: () => sdk.getBildungTagungen(),
    byId: (id) => sdk.getBildungTagungenById({ path: { id } }),
    create: (body) => sdk.postBildungTagungen({ body: body as TagungDto }),
    update: (id, body) => sdk.putBildungTagungenById({ path: { id }, body: body as TagungDto }),
    remove: (id) => sdk.deleteBildungTagungenById({ path: { id } }),
  },
  BERUFSSCHULJAHR: {
    label: 'Berufsschuljahr',
    pfad: 'berufsschuljahre',
    schema: z.zBerufsschuljahrDto,
    felder: [
      { name: 'fachrichtung', label: 'Fachrichtung', art: 'text' },
      { name: 'schuljahr', label: 'Schuljahr (JJJJ/JJ)', art: 'text' },
      { name: 'jahrgang', label: 'Jahrgang', art: 'number' },
      { name: 'beginn', label: 'Beginn', art: 'date' },
      { name: 'schildNrwSchluessel', label: 'Schild-NRW-Schlüssel', art: 'text' },
      { name: 'plaetze', label: 'Plätze', art: 'number' },
    ],
    list: () => sdk.getBildungBerufsschuljahre(),
    byId: (id) => sdk.getBildungBerufsschuljahreById({ path: { id } }),
    create: (body) => sdk.postBildungBerufsschuljahre({ body: body as BerufsschuljahrDto }),
    update: (id, body) => sdk.putBildungBerufsschuljahreById({ path: { id }, body: body as BerufsschuljahrDto }),
    remove: (id) => sdk.deleteBildungBerufsschuljahreById({ path: { id } }),
  },
  STUDIENGANG: {
    label: 'Studiengang',
    pfad: 'studiengaenge',
    schema: z.zStudiengangDto,
    felder: [
      { name: 'abschluss', label: 'Abschluss', art: 'select', options: z.zStudienabschluss.options },
      { name: 'studienform', label: 'Studienform', art: 'select', options: z.zStudienform.options },
      { name: 'startsemester', label: 'Startsemester (WS/SS+Jahr)', art: 'text' },
      { name: 'regelstudienzeitSemester', label: 'Regelstudienzeit (Sem.)', art: 'number' },
      { name: 'akkreditierungBis', label: 'Akkreditierung bis', art: 'date' },
      { name: 'ratenAnzahl', label: 'Raten-Anzahl', art: 'number' },
      { name: 'plaetze', label: 'Plätze', art: 'number' },
    ],
    list: () => sdk.getBildungStudiengaenge(),
    byId: (id) => sdk.getBildungStudiengaengeById({ path: { id } }),
    create: (body) => sdk.postBildungStudiengaenge({ body: body as StudiengangDto }),
    update: (id, body) => sdk.putBildungStudiengaengeById({ path: { id }, body: body as StudiengangDto }),
    remove: (id) => sdk.deleteBildungStudiengaengeById({ path: { id } }),
  },
};

export const alleTypen = Object.keys(typen) as Typ[];

/**
 * Shop-Projektion (P1.3, §11.6): stößt das Anlegen/Aktualisieren des Vendure-Produkts an und
 * bekommt die vendureProductId zurück. Typ-übergreifend (gemeinsames Feld shopVerkauf) → ein
 * Endpunkt über der Registry-ID, kein per-Typ-Aufruf.
 */
export const projiziereInShop = (id: number) =>
  sdk.postBildungAngeboteByIdShopProjektion({ path: { id } });

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
  if (typ === 'STUDIENGANG') Object.assign(base, { abschluss: 'BACHELOR', studienform: 'VOLLZEIT', startsemester: '', regelstudienzeitSemester: 1, ratenAnzahl: 0, plaetze: 0 });
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
