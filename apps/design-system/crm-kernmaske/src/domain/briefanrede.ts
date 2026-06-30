// Ableitung der Briefanrede aus Geschlecht/Titel (Geschäftsregel, nicht in der
// Präsentationsschicht). Fallback „Hallo {Vorname} {Nachname}" für divers/o. A.
import type { Person } from './types'

export function briefanrede(p: Person): string {
  const titel = p.titel ? p.titel + ' ' : ''
  if (p.geschlecht === 'W') return `Sehr geehrte Frau ${titel}${p.nachname}`
  if (p.geschlecht === 'M') return `Sehr geehrter Herr ${titel}${p.nachname}`
  return `Hallo ${p.vorname} ${p.nachname}`
}
