// Referenz-/Lookup-Daten (im Backend pflegbar gedacht). Domänen-Schicht, keine
// Instanzdaten — daher hier und nicht in data/mock.
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
