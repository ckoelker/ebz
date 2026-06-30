// Reine Party-Helfer (domänenrein, keine Daten-/UI-Abhängigkeit).
import type { Person } from './types'

export const personName = (p: Pick<Person, 'titel' | 'vorname' | 'nachname'>) =>
  [p.titel, p.vorname, p.nachname].filter(Boolean).join(' ')

export const initialen = (s: string) =>
  s.split(' ').filter(w => !w.includes('.')).slice(0, 2).map(w => w[0]).join('').toUpperCase()
