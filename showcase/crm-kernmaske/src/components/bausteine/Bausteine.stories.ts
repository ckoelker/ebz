import type { Meta, StoryObj } from '@storybook/vue3-vite'
import FeldRenderer from './FeldRenderer.vue'
import LookupCheckboxListe from './LookupCheckboxListe.vue'
import StammdatenForm from './StammdatenForm.vue'
import KanalEdit from './KanalEdit.vue'
import MitgliedschaftEdit from './MitgliedschaftEdit.vue'
import VerknuepfungDialog from './VerknuepfungDialog.vue'
import { PERSONEN, ORGANISATIONEN, MITGLIEDSCHAFTEN, LOOKUPS } from '../../mock/data'

const meta: Meta = { title: 'Bausteine/Inline-Pflege' }
export default meta
type Story = StoryObj

const personFelder = [
  { key: 'titel', label: 'Titel', typ: 'text' as const, placeholder: 'Dr.' },
  { key: 'vorname', label: 'Vorname', typ: 'text' as const, required: true },
  { key: 'nachname', label: 'Nachname', typ: 'text' as const, required: true },
  { key: 'geschlecht', label: 'Geschlecht', typ: 'select' as const, required: true,
    items: LOOKUPS.geschlecht.map(g => ({ label: g.bezeichnung, value: g.code })) },
  { key: 'geburtsdatum', label: 'Geburtsdatum', typ: 'date' as const },
  { key: 'korrespondenzsprache', label: 'Korrespondenzsprache', typ: 'select' as const,
    items: LOOKUPS.sprachen.map(s => ({ label: s.name, value: s.code })) },
]
const firmaFelder = [
  { key: 'name', label: 'Name', typ: 'text' as const, required: true },
  { key: 'rechtsform', label: 'Rechtsform', typ: 'text' as const },
  { key: 'ustId', label: 'USt-IdNr.', typ: 'text' as const, hint: 'VIES-prüfbar' },
  { key: 'unternehmenstyp', label: 'Unternehmenstyp', typ: 'select' as const, items: LOOKUPS.unternehmenstyp },
  { key: 'bestandsgroesse', label: 'Bestandsgröße (WE)', typ: 'number' as const },
  { key: 'ausbildungsbetrieb', label: 'Ausbildungsbetrieb', typ: 'checkbox' as const, placeholder: 'ist Ausbildungsbetrieb' },
]

export const Felder: Story = {
  name: 'FeldRenderer (alle Typen)',
  render: () => ({
    components: { FeldRenderer },
    setup: () => ({ felder: personFelder }),
    data: () => ({ v: { titel: 'Dr.', vorname: 'Anna', nachname: 'Schmidt', geschlecht: 'W', geburtsdatum: '1985-04-12', korrespondenzsprache: 'DE' } as Record<string, unknown> }),
    template: `<div class="grid sm:grid-cols-2 gap-4 max-w-2xl">
      <FeldRenderer v-for="f in felder" :key="f.key" :field="f" v-model="v[f.key]"
        :error="f.key === 'nachname' && !v.nachname ? 'Pflichtfeld' : undefined" />
    </div>`,
  }),
}

export const StammdatenPerson: Story = {
  name: 'StammdatenForm — Person (Ansicht ⇄ Edit)',
  render: () => ({
    components: { StammdatenForm },
    setup: () => ({ felder: personFelder, werte: PERSONEN[0] }),
    template: '<div class="max-w-3xl"><StammdatenForm titel="Stammdaten" :felder="felder" :werte="werte" /></div>',
  }),
}

export const StammdatenFirma: Story = {
  name: 'StammdatenForm — Firma',
  render: () => ({
    components: { StammdatenForm },
    setup: () => ({ felder: firmaFelder, werte: ORGANISATIONEN[0] }),
    template: '<div class="max-w-3xl"><StammdatenForm titel="Firmenstammdaten" :felder="felder" :werte="werte" /></div>',
  }),
}

export const Kanaele: Story = {
  name: 'KanalEdit (Kontaktpunkte)',
  render: () => ({
    components: { KanalEdit },
    setup: () => ({ kp: PERSONEN[0].kontaktpunkte }),
    template: '<div class="max-w-xl"><KanalEdit :kanaele="kp" :mit-kontext="true" /></div>',
  }),
}

export const Mitgliedschaft: Story = {
  name: 'MitgliedschaftEdit (N:M-Rolle)',
  render: () => ({
    components: { MitgliedschaftEdit },
    setup: () => ({ m: MITGLIEDSCHAFTEN[0] }),
    template: '<div class="max-w-2xl"><MitgliedschaftEdit :mitgliedschaft="m" /></div>',
  }),
}

export const Verknuepfung: Story = {
  name: 'VerknuepfungDialog (Bestand zuerst)',
  render: () => ({
    components: { VerknuepfungDialog },
    template: '<VerknuepfungDialog richtung="person-zu-firma" :open="true" />',
  }),
}

export const Lookup: Story = {
  name: 'LookupCheckboxListe (Verbände)',
  render: () => ({
    components: { LookupCheckboxListe },
    data: () => ({ sel: ['GdW', 'vdw'] as string[] }),
    setup: () => ({ opts: LOOKUPS.verbaende }),
    template: '<div class="max-w-md"><LookupCheckboxListe v-model="sel" :options="opts" label="Verbände" :filterable="true" /></div>',
  }),
}
