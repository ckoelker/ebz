// Domänen-Typen der CRM-Kernmaske (aus crm-planung/crm-plan.md + Wireframe abgeleitet).
// Für die Storybook-Abnahme: bewusst flach, deckt aber das abgestimmte N:M-Modell ab.

export type Geschlecht = 'M' | 'W' | 'D' | 'X'
export type Status = 'AKTIV' | 'PROVISORISCH' | 'GESPERRT' | 'INAKTIV'
export type Health = 'ok' | 'warn' | 'err'
export type KontaktTyp = 'EMAIL' | 'TELEFON' | 'ADRESSE'
export type Kontext = 'privat' | 'dienstlich'
export type EinwilligungStatus = 'ERTEILT' | 'AUSSTEHEND' | 'WIDERRUFEN'

export interface Kontaktpunkt {
  typ: KontaktTyp
  primaer?: boolean
  status?: Status
  kontext?: Kontext
  // EMAIL
  email?: string
  // TELEFON
  nummerAnzeige?: string
  nummerE164?: string
  // ADRESSE
  strasse?: string
  hausnummer?: string
  plz?: string
  ort?: string
  land?: string
}

export interface Einwilligung {
  kanal: string
  zweck: string
  status: EinwilligungStatus
  kontext: string
  quelle: string
  datum: string
  rechtsgrundlage: string
}

export interface Beziehung {
  typ: string
  personId: string
  hinweis?: string
}

export interface Person {
  id: string
  vorname: string
  nachname: string
  geschlecht: Geschlecht
  titel?: string
  geburtsdatum?: string | null
  staatsangehoerigkeit?: string[]
  korrespondenzsprache?: string
  status: Status
  werbesperre: boolean
  auskunftssperre: boolean
  leadQuelle?: string
  unvollstaendig?: boolean
  minderjaehrig?: boolean
  foto?: string | null
  login?: { loginEmail: string; verifiziert: boolean }[]
  kontaktpunkte: Kontaktpunkt[]
  einwilligungen: Einwilligung[]
  beziehungen: Beziehung[]
}

export interface Organisation {
  id: string
  name: string
  rechtsform?: string
  ustId?: string
  website?: string
  branche?: string
  unternehmenstyp?: string
  schwerpunkte?: string[]
  verbaende?: string[]
  bestandsgroesse?: number | null
  erlaubnis34c?: { vorhanden: boolean; behoerde: string; datum: string }
  ausbildungsbetrieb?: boolean
  ihk?: string
  uebergeordneteOrgId?: string | null
  status: Status
  kontaktpunkte: Kontaktpunkt[]
}

export interface Mitgliedschaft {
  id: string
  personId: string
  orgId: string
  rollen: string[]
  hauptzugehoerigkeit: boolean
  hauptansprechpartner: boolean
  buchungsberechtigt: boolean
  rechnungsempfaenger: boolean
  position?: string
  abteilung?: string
  gueltigVon?: string
  gueltigBis?: string | null
  dienstKontaktpunkte: Kontaktpunkt[]
}

export interface Aktivitaet {
  id: string
  personId?: string | null
  orgId?: string | null
  typ: string
  richtung?: string
  betreff: string
  inhaltHtml: string
  bearbeiter: string
  zeitpunkt: string
  dauer?: string
  anhaenge: string[]
}

export interface Wiedervorlage {
  id: string
  personId?: string | null
  orgId?: string | null
  betreff: string
  faelligAm: string
  erledigt: boolean
  zugewiesenAn: string
  typAn: 'mitarbeiter' | 'gruppe'
  prioritaet: 'hoch' | 'mittel' | 'niedrig'
}

export interface Prozess {
  id: string
  icon: string
  name: string
  health: Health
  kennzahl: string
  detail: string
  last: string
  beschreibung: string
}

export interface Eingriff {
  id: string
  prozess: string
  schwere: Health
  alter: string
  titel: string
  detail: string
  personId?: string | null
  aktionen: { key: string; label: string }[]
}

export interface Sonderfall {
  id: string
  icon: string
  titel: string
  faellig: string
  prioritaet: 'hoch' | 'mittel' | 'niedrig'
  detail: string
  personId?: string | null
}

export interface Anruf {
  id: string
  nummerE164: string
  richtung: 'ein' | 'aus'
  zeitpunkt: string
  mitarbeiter: string
  personId?: string | null
  dauer: string
  status: string
}

export interface Weiterbildung {
  zeitraum: string
  sollStunden: number
  istStunden: number
  nachweise: { titel: string; stunden: number; jahr: number; quelle: string }[]
}
