// Showcase M5 — Preisformatierung (Leitplanke §8a-10).
// Vendure liefert Geldbeträge als GANZZAHL in Minor Units (Cent): 2900 = 29,00 €.
// Immer durch 100 teilen und als de-DE-Währung ausgeben. Im Shop wird der
// BRUTTO-Wert (priceWithTax) angezeigt, weil der Channel netto rechnet (§8a-5).
const eur = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

/** Minor Units (Cent) → "29,00 €". */
export function formatPrice(minorUnits: number): string {
  return eur.format(minorUnits / 100);
}
