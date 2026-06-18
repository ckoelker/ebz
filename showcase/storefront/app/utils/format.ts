// Vendure speichert Geld in Minor Units (Cent). Anzeige in EUR, deutsch.
export function euro(cent: number, currency = 'EUR'): string {
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format((cent ?? 0) / 100)
}

export function preis(
  p: { __typename: string; value?: number; min?: number; max?: number } | null | undefined,
  currency = 'EUR',
): string {
  if (!p) return ''
  if (p.__typename === 'PriceRange') {
    return p.min === p.max ? euro(p.min ?? 0, currency) : `ab ${euro(p.min ?? 0, currency)}`
  }
  return euro(p.value ?? 0, currency)
}

export function datum(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : new Intl.DateTimeFormat('de-DE', { dateStyle: 'long' }).format(d)
}
