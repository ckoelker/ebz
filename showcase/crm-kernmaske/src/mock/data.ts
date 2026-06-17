// Mock-Daten für die Storybook-Abnahme — portiert aus showcase/crm-wireframe/mock-data.js.
// Wegwerf-/Demo-Daten, keine Persistenz. Deckt alle Funktionen ab (Privatperson,
// Mehrfach-Zugehörigkeit + Hauptzugehörigkeit, Firmen-Hierarchie, Minderjährige,
// Werbesperre/unvollständig).
import type {
  Person, Organisation, Mitgliedschaft, Aktivitaet, Wiedervorlage, Anruf,
  Prozess, Eingriff, Sonderfall, Weiterbildung,
} from '../types'

export const LOOKUPS = {
  geschlecht: [
    { code: 'M', bezeichnung: 'männlich' },
    { code: 'W', bezeichnung: 'weiblich' },
    { code: 'D', bezeichnung: 'divers' },
    { code: 'X', bezeichnung: 'ohne Angabe' },
  ],
  rollen: [
    'Geschäftsführung', 'Vorstand', 'Prokurist',
    'WEG-Verwalter', 'Mietverwalter', 'Objektmanager', 'Vermietung', 'Technik',
    'Ausbilder', 'Ansprechpartner Studium', 'Seminar-Bucher', 'Azubi', 'Student', 'Aufsichtsrat',
  ],
  verbaende: ['GdW', 'BFW', 'IVD', 'VNW', 'vdw', 'Haus & Grund', 'DDIV'],
  unternehmenstyp: ['Wohnungsunternehmen', 'Makler', 'Hausverwaltung', 'Genossenschaft (eG)', 'Bauträger', 'Sachverständigenbüro', 'Kommunale Gesellschaft'],
  schwerpunkte: ['Vermietung', 'WEG-Verwaltung', 'Mietverwaltung', 'Verkauf', 'Bewertung', 'Finanzierung', 'Neubau'],
  branche: ['Wohnungswirtschaft', 'Immobilienvermittlung', 'Immobilienverwaltung', 'Bauträger/Projektentwicklung', 'Sachverständigenwesen'],
  laender: [
    { code: 'DE', name: 'Deutschland' }, { code: 'AT', name: 'Österreich' }, { code: 'CH', name: 'Schweiz' },
    { code: 'BE', name: 'Belgien' }, { code: 'DK', name: 'Dänemark' }, { code: 'FR', name: 'Frankreich' },
    { code: 'IT', name: 'Italien' }, { code: 'LU', name: 'Luxemburg' }, { code: 'NL', name: 'Niederlande' },
    { code: 'NO', name: 'Norwegen' }, { code: 'PL', name: 'Polen' }, { code: 'PT', name: 'Portugal' },
    { code: 'SE', name: 'Schweden' }, { code: 'ES', name: 'Spanien' }, { code: 'CZ', name: 'Tschechien' },
    { code: 'TR', name: 'Türkei' }, { code: 'HU', name: 'Ungarn' }, { code: 'US', name: 'Vereinigte Staaten' },
    { code: 'GB', name: 'Vereinigtes Königreich' },
  ],
  sprachen: [{ code: 'DE', name: 'Deutsch' }, { code: 'EN', name: 'Englisch' }],
  leadQuelle: ['Telefon', 'Web-Formular', 'Messe', 'Empfehlung', 'Shop', 'Verband', 'Bestandskunde'],
  beziehungstyp: ['Erziehungsberechtigt', 'Notfallkontakt', 'Assistenz', 'Kollege'],
  aktivitaetTyp: ['Telefon eingehend', 'Telefon ausgehend', 'E-Mail', 'Notiz', 'Brief', 'Meeting'],
}

export const MITARBEITER = { id: 'm1', name: 'Sandra Berg', kuerzel: 'SB', rolle: 'crm-pflege', gruppen: ['Team Akademie', 'Vertrieb Innendienst'] }
export const GRUPPEN = ['Team Akademie', 'Team Berufsschule', 'Vertrieb Innendienst', 'Datenschutz']

export const ORGANISATIONEN: Organisation[] = [
  {
    id: 'o1', name: 'Wohnbau Rhein-Ruhr eG', rechtsform: 'eG', ustId: 'DE811234567',
    website: 'https://wohnbau-rhein-ruhr.de', branche: 'Wohnungswirtschaft',
    unternehmenstyp: 'Genossenschaft (eG)', schwerpunkte: ['Vermietung', 'WEG-Verwaltung'],
    verbaende: ['GdW', 'vdw'], bestandsgroesse: 4200, erlaubnis34c: { vorhanden: true, behoerde: 'Stadt Essen', datum: '2019-03-01' },
    ausbildungsbetrieb: true, ihk: 'IHK Essen', uebergeordneteOrgId: null, status: 'AKTIV',
    kontaktpunkte: [
      { typ: 'TELEFON', nummerAnzeige: '+49 201 123450', nummerE164: '+49201123450', primaer: true, status: 'AKTIV' },
      { typ: 'EMAIL', email: 'info@wohnbau-rhein-ruhr.de', primaer: true, status: 'AKTIV' },
      { typ: 'ADRESSE', strasse: 'Kettwiger Str.', hausnummer: '12', plz: '45127', ort: 'Essen', land: 'DE', primaer: true, status: 'AKTIV' },
    ],
  },
  {
    id: 'o2', name: 'WBR Hausverwaltung GmbH', rechtsform: 'GmbH', ustId: 'DE811234999',
    website: 'https://wbr-hausverwaltung.de', branche: 'Immobilienverwaltung',
    unternehmenstyp: 'Hausverwaltung', schwerpunkte: ['WEG-Verwaltung', 'Mietverwaltung'],
    verbaende: ['DDIV'], bestandsgroesse: 1800, erlaubnis34c: { vorhanden: true, behoerde: 'Stadt Essen', datum: '2021-06-15' },
    ausbildungsbetrieb: false, ihk: 'IHK Essen', uebergeordneteOrgId: 'o1', status: 'AKTIV',
    kontaktpunkte: [
      { typ: 'TELEFON', nummerAnzeige: '+49 201 123460', nummerE164: '+49201123460', primaer: true, status: 'AKTIV' },
      { typ: 'EMAIL', email: 'service@wbr-hausverwaltung.de', primaer: true, status: 'AKTIV' },
      { typ: 'ADRESSE', strasse: 'Kettwiger Str.', hausnummer: '12', plz: '45127', ort: 'Essen', land: 'DE', primaer: true, status: 'AKTIV' },
    ],
  },
  {
    id: 'o3', name: 'Meyer Immobilien e.K.', rechtsform: 'e.K.', ustId: '',
    website: 'https://meyer-immobilien.example', branche: 'Immobilienvermittlung',
    unternehmenstyp: 'Makler', schwerpunkte: ['Verkauf', 'Vermietung'],
    verbaende: ['IVD'], bestandsgroesse: null, erlaubnis34c: { vorhanden: true, behoerde: 'Stadt Bochum', datum: '2018-01-10' },
    ausbildungsbetrieb: false, ihk: 'IHK Bochum', uebergeordneteOrgId: null, status: 'AKTIV',
    kontaktpunkte: [
      { typ: 'TELEFON', nummerAnzeige: '+49 234 998877', nummerE164: '+49234998877', primaer: true, status: 'AKTIV' },
      { typ: 'EMAIL', email: 'kontakt@meyer-immobilien.example', primaer: true, status: 'AKTIV' },
    ],
  },
]

export const PERSONEN: Person[] = [
  {
    id: 'p1', vorname: 'Anna', nachname: 'Schmidt', geschlecht: 'W', titel: 'Dr.',
    geburtsdatum: '1985-04-12', staatsangehoerigkeit: ['DE'], korrespondenzsprache: 'DE',
    status: 'AKTIV', werbesperre: false, auskunftssperre: false, leadQuelle: 'Verband',
    unvollstaendig: false, foto: null,
    login: [{ loginEmail: 'a.schmidt@wohnbau-rhein-ruhr.de', verifiziert: true }],
    kontaktpunkte: [
      { typ: 'EMAIL', email: 'anna.schmidt@gmx.de', primaer: true, status: 'AKTIV', kontext: 'privat' },
      { typ: 'TELEFON', nummerAnzeige: '+49 170 1112233', nummerE164: '+491701112233', primaer: true, status: 'AKTIV', kontext: 'privat' },
      { typ: 'ADRESSE', strasse: 'Lindenweg', hausnummer: '5', plz: '45131', ort: 'Essen', land: 'DE', primaer: true, status: 'AKTIV', kontext: 'privat' },
    ],
    einwilligungen: [
      { kanal: 'E-Mail', zweck: 'Newsletter', status: 'ERTEILT', kontext: 'global', quelle: 'Verband', datum: '2024-09-01', rechtsgrundlage: 'Art. 6.1.a' },
    ],
    beziehungen: [],
  },
  {
    id: 'p2', vorname: 'Tobias', nachname: 'Krüger', geschlecht: 'M', titel: '',
    geburtsdatum: '2007-11-03', staatsangehoerigkeit: ['DE'], korrespondenzsprache: 'DE',
    status: 'AKTIV', werbesperre: false, auskunftssperre: false, leadQuelle: 'Bestandskunde',
    unvollstaendig: false, foto: null, minderjaehrig: true,
    login: [],
    kontaktpunkte: [
      { typ: 'EMAIL', email: 'tobi.krueger@web.de', primaer: true, status: 'AKTIV', kontext: 'privat' },
    ],
    einwilligungen: [
      { kanal: 'E-Mail', zweck: 'Marketing', status: 'AUSSTEHEND', kontext: 'global', quelle: 'Bestandskunde', datum: '2026-06-10', rechtsgrundlage: 'Art. 6.1.a (Eltern)' },
    ],
    beziehungen: [{ typ: 'Erziehungsberechtigt', personId: 'p1', hinweis: 'Mutter, Einwilligung erforderlich' }],
  },
  {
    id: 'p3', vorname: 'Markus', nachname: 'Meyer', geschlecht: 'M', titel: 'Dipl.-Ing.',
    geburtsdatum: '1972-02-20', staatsangehoerigkeit: ['DE'], korrespondenzsprache: 'DE',
    status: 'AKTIV', werbesperre: true, auskunftssperre: false, leadQuelle: 'Messe',
    unvollstaendig: false, foto: null,
    login: [{ loginEmail: 'm.meyer@meyer-immobilien.example', verifiziert: true }],
    kontaktpunkte: [
      { typ: 'TELEFON', nummerAnzeige: '+49 234 998877', nummerE164: '+49234998877', primaer: true, status: 'AKTIV', kontext: 'dienstlich' },
    ],
    einwilligungen: [
      { kanal: 'E-Mail', zweck: 'Marketing', status: 'WIDERRUFEN', kontext: 'global', quelle: 'Messe', datum: '2025-02-11', rechtsgrundlage: 'Art. 21 Widerspruch' },
    ],
    beziehungen: [],
  },
  {
    id: 'p4', vorname: 'Lena', nachname: 'Hofmann', geschlecht: 'W', titel: '',
    geburtsdatum: null, staatsangehoerigkeit: [], korrespondenzsprache: 'DE',
    status: 'PROVISORISCH', werbesperre: false, auskunftssperre: false, leadQuelle: 'Telefon',
    unvollstaendig: true, foto: null,
    login: [],
    kontaktpunkte: [
      { typ: 'TELEFON', nummerAnzeige: '+49 221 555000', nummerE164: '+49221555000', primaer: true, status: 'AKTIV', kontext: 'privat' },
    ],
    einwilligungen: [
      { kanal: 'Telefon', zweck: 'Marketing', status: 'AUSSTEHEND', kontext: 'global', quelle: 'Telefon', datum: '2026-06-16', rechtsgrundlage: 'Art. 6.1.b Anbahnung' },
    ],
    beziehungen: [],
  },
]

export const MITGLIEDSCHAFTEN: Mitgliedschaft[] = [
  {
    id: 'mg1', personId: 'p1', orgId: 'o1', rollen: ['Vorstand', 'Seminar-Bucher'],
    hauptzugehoerigkeit: true, hauptansprechpartner: true, buchungsberechtigt: true, rechnungsempfaenger: true,
    position: 'Vorständin', abteilung: 'Vorstand', gueltigVon: '2015-01-01', gueltigBis: null,
    dienstKontaktpunkte: [
      { typ: 'EMAIL', email: 'a.schmidt@wohnbau-rhein-ruhr.de', primaer: true, status: 'AKTIV' },
      { typ: 'TELEFON', nummerAnzeige: '+49 201 123451', nummerE164: '+49201123451', primaer: true, status: 'AKTIV' },
    ],
  },
  {
    id: 'mg2', personId: 'p1', orgId: 'o2', rollen: ['Aufsichtsrat'],
    hauptzugehoerigkeit: false, hauptansprechpartner: false, buchungsberechtigt: false, rechnungsempfaenger: false,
    position: 'Aufsichtsrätin', abteilung: '', gueltigVon: '2021-06-15', gueltigBis: null,
    dienstKontaktpunkte: [],
  },
  {
    id: 'mg3', personId: 'p3', orgId: 'o3', rollen: ['Geschäftsführung', 'WEG-Verwalter'],
    hauptzugehoerigkeit: true, hauptansprechpartner: true, buchungsberechtigt: true, rechnungsempfaenger: true,
    position: 'Inhaber', abteilung: '', gueltigVon: '2010-01-01', gueltigBis: null,
    dienstKontaktpunkte: [
      { typ: 'EMAIL', email: 'm.meyer@meyer-immobilien.example', primaer: true, status: 'AKTIV' },
    ],
  },
  {
    id: 'mg4', personId: 'p3', orgId: 'o1', rollen: ['Ausbilder'],
    hauptzugehoerigkeit: false, hauptansprechpartner: false, buchungsberechtigt: false, rechnungsempfaenger: false,
    position: 'externer Dozent', abteilung: '', gueltigVon: '2017-01-01', gueltigBis: '2022-12-31',
    dienstKontaktpunkte: [],
  },
]

export const AKTIVITAETEN: Aktivitaet[] = [
  { id: 'a1', personId: 'p1', orgId: null, typ: 'Telefon eingehend', richtung: 'ein', betreff: 'Rückfrage Seminaranmeldung', inhaltHtml: '<p>Frau Dr. Schmidt fragt nach <b>Restplätzen</b> im WEG-Seminar.</p>', bearbeiter: 'SB', zeitpunkt: '2026-06-15 09:42', dauer: '6 min', anhaenge: [] },
  { id: 'a2', personId: 'p1', orgId: null, typ: 'E-Mail', richtung: 'aus', betreff: 'Angebot Inhouse-Schulung', inhaltHtml: '<p>Angebot versendet (PDF).</p>', bearbeiter: 'SB', zeitpunkt: '2026-06-12 14:10', dauer: '', anhaenge: ['Angebot_2026-114.pdf'] },
  { id: 'a3', personId: 'p3', orgId: 'o3', typ: 'Notiz', richtung: '', betreff: 'Werbewiderspruch notiert', inhaltHtml: '<p>Kunde wünscht <b>keine Werbung</b> mehr — Werbesperre gesetzt.</p>', bearbeiter: 'SB', zeitpunkt: '2025-02-11 11:00', dauer: '', anhaenge: [] },
]

export const WIEDERVORLAGEN: Wiedervorlage[] = [
  { id: 'w1', personId: 'p1', orgId: null, betreff: 'Inhouse-Angebot nachfassen', faelligAm: '2026-06-18', erledigt: false, zugewiesenAn: 'Sandra Berg', typAn: 'mitarbeiter', prioritaet: 'hoch' },
  { id: 'w2', personId: 'p4', orgId: null, betreff: 'Daten vervollständigen (Telefonanfrage)', faelligAm: '2026-06-17', erledigt: false, zugewiesenAn: 'Vertrieb Innendienst', typAn: 'gruppe', prioritaet: 'mittel' },
]

export const ANRUFE: Anruf[] = [
  { id: 'c1', nummerE164: '+491701112233', richtung: 'ein', zeitpunkt: '2026-06-15 09:42', mitarbeiter: 'SB', personId: 'p1', dauer: '6 min', status: 'erledigt' },
]

export const WEITERBILDUNG: Record<string, Weiterbildung> = {
  p1: { zeitraum: '2024–2026', sollStunden: 20, istStunden: 14, nachweise: [
    { titel: 'WEG-Recht aktuell', stunden: 8, jahr: 2024, quelle: 'EBZ' },
    { titel: 'Mietrecht-Update', stunden: 6, jahr: 2025, quelle: 'extern (IVD)' },
  ] },
  p3: { zeitraum: '2024–2026', sollStunden: 20, istStunden: 4, nachweise: [
    { titel: 'Maklerrecht Basis', stunden: 4, jahr: 2024, quelle: 'EBZ' },
  ] },
}

export const LOGINVERSUCHE: Record<string, { zeit: string; ergebnis: string; ip: string }[]> = {
  p1: [{ zeit: '2026-06-16 07:55', ergebnis: 'erfolgreich', ip: '91.0.0.1' }, { zeit: '2026-06-15 22:01', ergebnis: 'fehlgeschlagen', ip: '203.0.113.9' }],
  p3: [{ zeit: '2026-06-14 10:00', ergebnis: 'erfolgreich', ip: '91.0.0.5' }],
}

export const BUCHUNGEN: Record<string, { titel: string; datum: string; betrag: string; status: string }[]> = {
  p1: [{ titel: 'Zertifizierter WEG-Verwalter', datum: '2026-05-02', betrag: '1.290 €', status: 'bezahlt' }],
  p3: [{ titel: 'Sachkundenachweis §34a', datum: '2026-03-10', betrag: '690 €', status: 'offen' }],
}

export const QUICKLINKS = [
  { typ: 'person', id: 'p1' },
  { typ: 'org', id: 'o1' },
  { typ: 'person', id: 'p3' },
]

export const PROZESSE: Prozess[] = [
  { id: 'pr-anm', icon: '🎓', name: 'Anmeldungen (Self-Service)', health: 'warn',
    kennzahl: '12 laufen', detail: '8 abgeschlossen · 1 stockt (Zahlungseingang offen)', last: 'vor 3 Min',
    beschreibung: 'Berufsschul-/Seminar-Anmeldung mit KI-Dublettencheck und HITL-Bestätigung.' },
  { id: 'pr-rech', icon: '🧾', name: 'Rechnungslauf & Versand', health: 'ok',
    kennzahl: 'nächster Lauf 14:00', detail: 'letzter Lauf: 41 Rechnungen, 0 Fehler', last: 'heute 06:00',
    beschreibung: 'ZUGFeRD-Erzeugung, Versand, Debitoren-Nummernkreis, DATEV-Übergabe.' },
  { id: 'pr-untis', icon: '🏫', name: 'WebUntis-Provisionierung', health: 'err',
    kennzahl: '1 Dead-Letter', detail: '2 Retry laufen · 1 Vorgang nach 3 Fehlversuchen abgebrochen', last: 'vor 25 Min',
    beschreibung: 'Transaktionale Outbox → Drittsystem-Sync bei Vertragsbestätigung.' },
  { id: 'pr-lms', icon: '📚', name: 'LMS-Einschreibung (Shop→OpenOLAT)', health: 'ok',
    kennzahl: 'alle ok', detail: '6 Einschreibungen heute · Outbox leer', last: 'vor 12 Min',
    beschreibung: 'WBT-Kauf im Shop → Einschreibung in OpenOLAT-Kurs.' },
  { id: 'pr-doi', icon: '✉', name: 'Double-Opt-In (DSGVO)', health: 'warn',
    kennzahl: '5 ausstehend', detail: '5 Bestätigungen offen, davon 1 > 14 Tage', last: 'vor 1 Std',
    beschreibung: 'Einwilligungs-Bestätigung; Nachweis (Token/IP/Zeit). Marketing-Versand erst danach.' },
]

export const EINGRIFFE: Eingriff[] = [
  { id: 'e1', prozess: 'WebUntis-Provisionierung', schwere: 'err', alter: 'vor 25 Min',
    titel: 'Provisionierung fehlgeschlagen: Tobias Krüger',
    detail: 'WebUntis-Endpunkt 3× Timeout → Dead-Letter. Schüler kann sich nicht einloggen.',
    personId: 'p2', aktionen: [{ key: 'retry', label: '↻ Erneut versuchen' }, { key: 'uebernehmen', label: '✋ Manuell übernehmen' }, { key: 'kontakt', label: '↗ Kontakt öffnen' }] },
  { id: 'e2', prozess: 'Anmeldung · KI-Dublettencheck', schwere: 'warn', alter: 'vor 1 Std',
    titel: 'Mögliche Dublette: „Markus Meyer" vs. Neuanmeldung „M. Meier"',
    detail: 'KI-Ähnlichkeit 86 % (Name + Geburtsjahr + Ort). Vor Anlage prüfen.',
    personId: 'p3', aktionen: [{ key: 'kontakt', label: '↗ Bestand öffnen' }, { key: 'merge', label: '⇉ Zusammenführen' }, { key: 'ignorieren', label: '✓ Kein Treffer' }] },
  { id: 'e3', prozess: 'Double-Opt-In', schwere: 'warn', alter: 'vor 2 Tagen',
    titel: 'Opt-In seit 14 Tagen ausstehend: Lena Hofmann',
    detail: 'Keine Bestätigung. Vor Werbeversand erneut anstoßen oder verwerfen.',
    personId: 'p4', aktionen: [{ key: 'erneut', label: '✉ Erneut anstoßen' }, { key: 'kontakt', label: '↗ Kontakt öffnen' }, { key: 'verwerfen', label: '🗑 Verwerfen' }] },
]

export const SONDERFAELLE: Sonderfall[] = [
  { id: 's1', icon: '✋', titel: 'Manuelle Anmeldung (Papierformular Messe)', faellig: '2026-06-18', prioritaet: 'mittel',
    detail: 'Teilnehmer ohne Online-Konto — manuell erfassen und in Seminar einbuchen.', personId: null },
  { id: 's2', icon: '🧾', titel: 'Kulanz-Storno + Gutschrift außerhalb Frist', faellig: '2026-06-17', prioritaet: 'hoch',
    detail: 'Storno nach Widerrufsfrist, manuelle Gutschrift erstellen.', personId: 'p1' },
  { id: 's3', icon: '🏫', titel: 'Schulwechsel-Sonderfall: abweichende Klassenzuordnung', faellig: '2026-06-20', prioritaet: 'niedrig',
    detail: 'WebUntis-Automatik nicht anwendbar — Zuordnung manuell mit Berufsschule abstimmen.', personId: 'p2' },
]

// ---- abgeleitete Helfer (für Listen/Detail) ----
export const personName = (p: Person) => [p.titel, p.vorname, p.nachname].filter(Boolean).join(' ')
export const initialen = (s: string) => s.split(' ').filter(w => !w.includes('.')).slice(0, 2).map(w => w[0]).join('').toUpperCase()
export const orgById = (id?: string | null) => ORGANISATIONEN.find(o => o.id === id)
export const personById = (id?: string | null) => PERSONEN.find(p => p.id === id)
export const mitgliedschaftenVonPerson = (pid: string) => MITGLIEDSCHAFTEN.filter(m => m.personId === pid)
export const mitgliedschaftenVonOrg = (oid: string) => MITGLIEDSCHAFTEN.filter(m => m.orgId === oid)
