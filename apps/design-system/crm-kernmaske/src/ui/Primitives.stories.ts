import type { Meta, StoryObj } from '@storybook/vue3-vite'
import ChipList from './ChipList.vue'
import KeyValueGrid from './KeyValueGrid.vue'
import SegmentedControl from '@crm-ui/ui/SegmentedControl.vue'
import KontaktpunktList from './KontaktpunktList.vue'
import Stepper from './Stepper.vue'
import { PERSONEN } from '../data/mock'

// Galerie-Stories für Bausteine mit Array-/Objekt-Props (interaktive Controls hier weniger sinnvoll).
// Die prop-reinen Einzel-Primitive (PartyAvatar/HealthDot/StatusBadges/MasterListItem) haben eigene
// arg-getriebene Stories mit argTypes/play.
const meta: Meta = { title: 'Primitives/UI-Bausteine' }
export default meta
type Story = StoryObj

export const Chips: Story = {
  render: () => ({
    components: { ChipList },
    template: '<ChipList :items="[\'Vorstand\', \'Seminar-Bucher\', \'WEG-Verwalter\']" />',
  }),
}

export const KeyValue: Story = {
  render: () => ({
    components: { KeyValueGrid },
    setup: () => ({ items: [
      { label: 'Vorname', value: 'Anna' }, { label: 'Nachname', value: 'Schmidt' },
      { label: 'Geschlecht', value: 'weiblich' }, { label: 'Sprache', value: 'DE' },
    ] }),
    template: '<div class="max-w-xl"><KeyValueGrid :items="items" /></div>',
  }),
}

export const Segment: Story = {
  render: () => ({
    components: { SegmentedControl },
    data: () => ({ v: 'alle' }),
    template: '<div class="max-w-xs"><SegmentedControl v-model="v" :options="[{value:\'alle\',label:\'Alle\'},{value:\'personen\',label:\'Personen\'},{value:\'firmen\',label:\'Firmen\'}]" /><p class="text-sm text-muted mt-2">Auswahl: {{ v }}</p></div>',
  }),
}

export const Kontaktpunkte: Story = {
  render: () => ({
    components: { KontaktpunktList },
    setup: () => ({ kp: PERSONEN[0].kontaktpunkte }),
    template: '<div class="max-w-xl"><KontaktpunktList :kontaktpunkte="kp" :mit-kontext="true" /></div>',
  }),
}

export const StepperStory: Story = {
  name: 'Stepper',
  render: () => ({
    components: { Stepper },
    data: () => ({ step: 1 }),
    template: '<Stepper :steps="[\'Pflicht\', \'Marketing\', \'Zugehörigkeit\', \'Weitere\']" :model-value="step" />',
  }),
}
