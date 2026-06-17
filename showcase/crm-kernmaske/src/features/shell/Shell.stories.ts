import type { Meta, StoryObj } from '@storybook/vue3-vite'
import Topbar from './Topbar.vue'
import Rail from './Rail.vue'
import CockpitShell from './CockpitShell.vue'
import { PERSONEN, ORGANISATIONEN, MITARBEITER, EINGRIFFE, SONDERFAELLE } from '../../data/mock'
import { personName } from '../../domain/party'

const meta: Meta = {
  title: 'Shell/Übersicht',
}
export default meta
type Story = StoryObj

export const TopbarStory: Story = {
  name: 'Topbar (Suche + CTI + User)',
  render: () => ({
    components: { Topbar },
    setup: () => ({ personen: PERSONEN, organisationen: ORGANISATIONEN, benutzer: { name: MITARBEITER.name, rolle: MITARBEITER.rolle } }),
    template: '<div class="-m-4"><Topbar :personen="personen" :organisationen="organisationen" :benutzer="benutzer" /></div>',
  }),
}

export const RailStory: Story = {
  name: 'Rail (Navigation + Quicklinks)',
  render: () => ({
    components: { Rail },
    setup: () => ({
      eingriffe: EINGRIFFE.length,
      sonderfaelle: SONDERFAELLE.length,
      quicklinks: [
        { id: 'p1', label: personName(PERSONEN[0]), org: false },
        { id: 'o1', label: ORGANISATIONEN[0].name, org: true },
      ],
      benutzer: { name: MITARBEITER.name, gruppe: MITARBEITER.gruppen[0] },
    }),
    template: `<div class="-m-4 h-[600px]"><Rail active="kundenstamm" :kundenstamm-aktiv="true"
      :eingriffe="eingriffe" :sonderfaelle="sonderfaelle" :quicklinks="quicklinks" :benutzer="benutzer" /></div>`,
  }),
}

export const Shell: Story = {
  name: 'CockpitShell (komplett)',
  render: () => ({
    components: { CockpitShell },
    template: '<div class="-m-4"><CockpitShell /></div>',
  }),
}
