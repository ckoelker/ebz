// Selector: baut das View-Model der Kundenstamm-Master-Liste (pure Funktion über
// übergebene Daten → testbar, UI bleibt dünn). Vorher in KundenMasterListe.vue.
import type { Person, Organisation, Mitgliedschaft } from './types'
import { personName } from './party'

export interface KundenListItem {
  id: string
  label: string
  org: boolean
  sub: string
  sub2: string
  warn: boolean
  blocked: boolean
}

export type KundenFilter = 'alle' | 'personen' | 'firmen'

export function buildKundenListe(
  personen: Person[],
  organisationen: Organisation[],
  mitgliedschaften: Mitgliedschaft[],
  filter: KundenFilter,
  query: string,
): KundenListItem[] {
  const s = query.trim().toLowerCase()
  const orgById = (id: string) => organisationen.find(o => o.id === id)

  const hauptInfo = (pid: string) => {
    const m = mitgliedschaften.find(x => x.personId === pid && x.hauptzugehoerigkeit && !x.gueltigBis)
    if (!m) return null
    const o = orgById(m.orgId)
    return o ? `${o.name} · ${m.rollen[0] ?? ''}` : null
  }

  const personItems: KundenListItem[] = personen
    .filter(p => !s || personName(p).toLowerCase().includes(s))
    .map(p => {
      const addr = p.kontaktpunkte.find(k => k.typ === 'ADRESSE')
      return {
        id: p.id, label: personName(p), org: false,
        sub: hauptInfo(p.id) ?? 'Privatkontakt',
        sub2: [addr?.ort, p.geburtsdatum ? '*' + p.geburtsdatum.slice(0, 4) : null].filter(Boolean).join(' · '),
        warn: !!p.unvollstaendig, blocked: !!p.werbesperre,
      }
    })

  const firmaItems: KundenListItem[] = organisationen
    .filter(o => !s || o.name.toLowerCase().includes(s))
    .map(o => {
      const addr = o.kontaktpunkte.find(k => k.typ === 'ADRESSE')
      const n = mitgliedschaften.filter(m => m.orgId === o.id && !m.gueltigBis).length
      return {
        id: o.id, label: o.name, org: true,
        sub: [o.unternehmenstyp, addr?.ort].filter(Boolean).join(' · '),
        sub2: `${n} Person${n === 1 ? '' : 'en'} verknüpft`,
        warn: false, blocked: false,
      }
    })

  if (filter === 'personen') return personItems
  if (filter === 'firmen') return firmaItems
  return [...personItems, ...firmaItems]
}
