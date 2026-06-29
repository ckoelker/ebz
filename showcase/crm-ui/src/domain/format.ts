// Geld- und Datumsformatierung, app-neutral und einheitlich (DE-Konventionen, Zeitzone Europe/Berlin).
// EINE Quelle für mdm/portal/storefront — Geld kommt überall als Minor Units (Cent, wie Vendure/Rechnung),
// Datum als ISO-String. Die Intl-Formatter werden modulweit gecacht (teurer Konstruktor).

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });
const DATUM_LANG = new Intl.DateTimeFormat('de-DE', { dateStyle: 'long', timeZone: 'Europe/Berlin' });
const DATUM_KURZ = new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeZone: 'Europe/Berlin' });
const DATUM_ZEIT = new Intl.DateTimeFormat('de-DE', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Europe/Berlin',
});
const DATUM_ZEIT_KURZ = new Intl.DateTimeFormat('de-DE', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Europe/Berlin',
});

/** Cent → "1.234,56 €" (Minor Units). Für EUR gecacht; andere Währungen ad hoc. */
export const euro = (cent?: number | null, currency = 'EUR'): string =>
  currency === 'EUR'
    ? EUR.format((cent ?? 0) / 100)
    : new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format((cent ?? 0) / 100);

/** ISO-String → "27. Juni 2026" (Europe/Berlin). Leerer/ungültiger Wert → "". */
export const datum = (iso?: string | null): string => fmt(iso, DATUM_LANG);

/** ISO-String → "27.06.2026" (Europe/Berlin). */
export const datumKurz = (iso?: string | null): string => fmt(iso, DATUM_KURZ);

/** ISO-String → "27.06.2026, 14:30" (Europe/Berlin). */
export const datumZeit = (iso?: string | null): string => fmt(iso, DATUM_ZEIT);

/** ISO-String → "27.06.26, 14:30" (Europe/Berlin), kompakt für Chat/Listen. */
export const datumZeitKurz = (iso?: string | null): string => fmt(iso, DATUM_ZEIT_KURZ);

function fmt(iso: string | null | undefined, f: Intl.DateTimeFormat): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : f.format(d);
}
