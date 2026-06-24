import type { Meta, StoryObj } from '@storybook/vue3-vite'
import PartyAvatar from '@crm-ui/ui/PartyAvatar.vue'
import HealthDot from '@crm-ui/ui/HealthDot.vue'
import StatusBadges from '@crm-ui/ui/StatusBadges.vue'
import MasterListItem from '@crm-ui/ui/MasterListItem.vue'
import ChipList from './ChipList.vue'
import KeyValueGrid from './KeyValueGrid.vue'
import SegmentedControl from '@crm-ui/ui/SegmentedControl.vue'
import KontaktpunktList from './KontaktpunktList.vue'
import Stepper from './Stepper.vue'
import { PERSONEN } from '../data/mock'

const meta: Meta = { title: 'Primitives/UI-Bausteine' }
export default meta
type Story = StoryObj

export const Avatare: Story = {
  render: () => ({
    components: { PartyAvatar },
    template: `<div class="flex items-center gap-3">
      <PartyAvatar name="Anna Schmidt" size="lg" />
      <PartyAvatar name="Wohnbau Rhein-Ruhr eG" :org="true" size="lg" />
      <PartyAvatar name="Markus Meyer" />
      <PartyAvatar name="Meyer Immobilien" :org="true" size="2xs" />
    </div>`,
  }),
}

export const Health: Story = {
  render: () => ({
    components: { HealthDot },
    template: `<div class="flex gap-6">
      <HealthDot health="ok" label="OK" /><HealthDot health="warn" label="Achtung" /><HealthDot health="err" label="Fehler" />
    </div>`,
  }),
}

export const Status: Story = {
  render: () => ({
    components: { StatusBadges },
    template: `<div class="space-y-2">
      <StatusBadges status="AKTIV" />
      <StatusBadges status="PROVISORISCH" :unvollstaendig="true" />
      <StatusBadges status="AKTIV" :werbesperre="true" :auskunftssperre="true" />
    </div>`,
  }),
}

export const Listeneintraege: Story = {
  name: 'MasterListItem (warn/blocked)',
  render: () => ({
    components: { MasterListItem },
    template: `<div class="w-80 ring-1 ring-default rounded-lg overflow-hidden">
      <MasterListItem label="Anna Schmidt" sub="Wohnbau Rhein-Ruhr eG · Vorstand" sub2="Düsseldorf" :active="true" />
      <MasterListItem label="Markus Meyer" sub="Privatkontakt" sub2="*1981" :warn="true" />
      <MasterListItem label="Petra Sperr" sub="Privatkontakt" :blocked="true" />
      <MasterListItem label="Meyer Immobilien GmbH" sub="Makler · Köln" sub2="2 Personen verknüpft" :org="true" />
    </div>`,
  }),
}

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
