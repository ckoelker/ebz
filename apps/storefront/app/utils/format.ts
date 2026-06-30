// Geld/Datum kommen aus dem geteilten Domain-Core (@crm-ui/domain/format) — EINE Quelle für
// mdm/portal/storefront. preis() bleibt hier (Vendure-spezifischer PriceRange-Typ), nutzt aber das
// geteilte euro(). datum() ist jetzt zonenkorrekt (Europe/Berlin) statt Server-/Browser-Zone.
import { euro as euroShared, datum as datumShared } from '@crm-ui/domain/format'

export const euro = euroShared
export const datum = datumShared

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
