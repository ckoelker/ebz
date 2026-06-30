// Kontaktpunkt-Helfer (Anzeige-Label + Icon), domänenrein.
import type { Kontaktpunkt, KontaktTyp } from './types'

export const kontaktIcon = (typ: KontaktTyp): string =>
  typ === 'EMAIL' ? 'i-lucide-mail' : typ === 'TELEFON' ? 'i-lucide-phone' : 'i-lucide-map-pin'

export const kontaktpunktLabel = (k: Kontaktpunkt): string =>
  k.email
  ?? k.nummerAnzeige
  ?? [k.strasse, k.hausnummer, k.plz && '·', k.plz, k.ort].filter(Boolean).join(' ')
