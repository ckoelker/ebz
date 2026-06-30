// Reine Party-Helfer — domänenrein, ohne Daten-/UI-/App-Typ-Abhängigkeit.
// Geteilt zwischen Storybook (crm-kernmaske) und mdm-Cockpit über den Vite-Source-Alias @crm-ui.
// Bewusst auf strukturelle Minimal-Typen getippt, damit weder die Storybook-Mock-Typen noch die
// orval-generierten mdm-API-Typen hier hereinlecken (eine Quelle, beidseitig konsumierbar).

export interface NamensTeile {
  titel?: string | null;
  vorname?: string | null;
  nachname?: string | null;
}

export const personName = (p: NamensTeile) =>
  [p.titel, p.vorname, p.nachname].filter(Boolean).join(' ');

export const initialen = (s: string) =>
  s.split(' ').filter((w) => !w.includes('.')).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
