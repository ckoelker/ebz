// Telefon-Normalisierung auf E.164 (Backlog): Ländervorwahl als Auswahlliste (Default +49), führende
// Null der nationalen Nummer beim Bauen entfernen; `nummerE164` sauber (z. B. +49231555012),
// `nummerAnzeige` lesbar (z. B. +49 231 555012). DE/AT/CH zuerst, dann gängige Märkte.
export const VORWAHLEN: { label: string; value: string }[] = [
  { label: '🇩🇪 +49 (Deutschland)', value: '+49' },
  { label: '🇦🇹 +43 (Österreich)', value: '+43' },
  { label: '🇨🇭 +41 (Schweiz)', value: '+41' },
  { label: '🇫🇷 +33 (Frankreich)', value: '+33' },
  { label: '🇮🇹 +39 (Italien)', value: '+39' },
  { label: '🇳🇱 +31 (Niederlande)', value: '+31' },
  { label: '🇧🇪 +32 (Belgien)', value: '+32' },
  { label: '🇱🇺 +352 (Luxemburg)', value: '+352' },
  { label: '🇵🇱 +48 (Polen)', value: '+48' },
  { label: '🇪🇸 +34 (Spanien)', value: '+34' },
  { label: '🇬🇧 +44 (Großbritannien)', value: '+44' },
  { label: '🇺🇸 +1 (USA/Kanada)', value: '+1' },
];

export const VORWAHL_DEFAULT = '+49';

/** Nur Ziffern + führende Null entfernen (nationale Teilnehmernummer). */
export function nationalDigits(eingabe: string): string {
  return (eingabe ?? '').replace(/\D/g, '').replace(/^0+/, '');
}

/** Baut die normalisierte E.164-Nummer aus Vorwahl + nationaler Eingabe; leer, wenn keine Ziffern. */
export function baueE164(vorwahl: string, national: string): string {
  const d = nationalDigits(national);
  return d ? `${vorwahl}${d}` : '';
}

/** Lesbare Anzeige: Vorwahl + nationale Nummer (führende Null entfernt). */
export function baueAnzeige(vorwahl: string, national: string): string {
  const d = nationalDigits(national);
  return d ? `${vorwahl} ${d}` : '';
}

/** Zerlegt eine gespeicherte E.164-/Anzeige-Nummer in Vorwahl + nationale Ziffern (für den Edit-Modus). */
export function zerlege(wert: string | null | undefined): { vorwahl: string; national: string } {
  const roh = (wert ?? '').trim();
  if (!roh) return { vorwahl: VORWAHL_DEFAULT, national: '' };
  const plus = roh.startsWith('+') ? '+' + roh.replace(/\D/g, '') : roh.replace(/\D/g, '');
  if (plus.startsWith('+')) {
    // Längste passende Vorwahl gewinnt (+1 vs. +49 vs. +352).
    const treffer = [...VORWAHLEN].sort((a, b) => b.value.length - a.value.length)
      .find((v) => plus.startsWith(v.value));
    if (treffer) return { vorwahl: treffer.value, national: plus.slice(treffer.value.length) };
  }
  return { vorwahl: VORWAHL_DEFAULT, national: plus.replace(/^\+/, '').replace(/^0+/, '') };
}
