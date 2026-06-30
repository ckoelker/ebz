// Ableitung der Briefanrede aus Geschlecht/Titel (Geschäftsregel, nicht in der Präsentationsschicht).
// Fallback „Hallo {Vorname} {Nachname}" für divers/o. A. Strukturell getippt → app-neutral.
export interface AnredePerson {
  geschlecht?: string | null;
  titel?: string | null;
  vorname?: string | null;
  nachname?: string | null;
}

export function briefanrede(p: AnredePerson): string {
  const titel = p.titel ? p.titel + ' ' : '';
  if (p.geschlecht === 'W') return `Sehr geehrte Frau ${titel}${p.nachname}`;
  if (p.geschlecht === 'M') return `Sehr geehrter Herr ${titel}${p.nachname}`;
  return `Hallo ${p.vorname} ${p.nachname}`;
}
