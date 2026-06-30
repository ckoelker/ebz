// Feld-Schemas als Single Source of Truth (vorher dupliziert in Dialog + Story).
// Später Basis für vee-validate/zod. FeldDef ist die typneutrale Felddefinition,
// die der UI-Baustein FeldRenderer rendert.
import { LOOKUPS } from './lookups'

export interface FeldDef {
  key: string
  label: string
  typ: 'text' | 'number' | 'date' | 'select' | 'checkbox' | 'textarea'
  items?: { label: string; value: string }[] | string[]
  placeholder?: string
  required?: boolean
  hint?: string
}

const geschlechtItems = LOOKUPS.geschlecht.map(g => ({ label: g.bezeichnung, value: g.code }))
const sprachItems = LOOKUPS.sprachen.map(s => ({ label: s.name, value: s.code }))

export const personStammdatenFelder: FeldDef[] = [
  { key: 'titel', label: 'Titel', typ: 'text', placeholder: 'Dr.' },
  { key: 'vorname', label: 'Vorname', typ: 'text', required: true },
  { key: 'nachname', label: 'Nachname', typ: 'text', required: true },
  { key: 'geschlecht', label: 'Geschlecht', typ: 'select', required: true, items: geschlechtItems },
  { key: 'geburtsdatum', label: 'Geburtsdatum', typ: 'date' },
  { key: 'korrespondenzsprache', label: 'Korrespondenzsprache', typ: 'select', items: sprachItems },
]

// Pflicht-Teilmenge für die gestufte Neuanlage (1. Schritt).
export const personPflichtFelder: FeldDef[] = [
  { key: 'titel', label: 'Titel', typ: 'text' },
  { key: 'vorname', label: 'Vorname', typ: 'text', required: true },
  { key: 'nachname', label: 'Nachname', typ: 'text', required: true },
  { key: 'geschlecht', label: 'Geschlecht', typ: 'select', required: true, items: geschlechtItems },
  { key: 'email', label: 'E-Mail (1 Kontaktpunkt Pflicht)', typ: 'text', required: true },
]

export const firmaStammdatenFelder: FeldDef[] = [
  { key: 'name', label: 'Name', typ: 'text', required: true },
  { key: 'rechtsform', label: 'Rechtsform', typ: 'text' },
  { key: 'ustId', label: 'USt-IdNr.', typ: 'text', hint: 'VIES-prüfbar' },
  { key: 'unternehmenstyp', label: 'Unternehmenstyp', typ: 'select', items: LOOKUPS.unternehmenstyp },
  { key: 'bestandsgroesse', label: 'Bestandsgröße (WE)', typ: 'number' },
  { key: 'ausbildungsbetrieb', label: 'Ausbildungsbetrieb', typ: 'checkbox', placeholder: 'ist Ausbildungsbetrieb' },
]
