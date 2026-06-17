import type { Meta, StoryObj } from '@storybook/vue3-vite'
import KundenMasterListe from './KundenMasterListe.vue'
import KontaktDetailHeader from './KontaktDetailHeader.vue'
import TabBar from './TabBar.vue'
import KundenMasterDetailDemo from './KundenMasterDetailDemo.vue'
import { PERSONEN, ORGANISATIONEN, MITGLIEDSCHAFTEN } from '../../data/mock'

const meta: Meta = { title: 'Kundenstamm/Master-Detail' }
export default meta
type Story = StoryObj

export const MasterDetail: Story = {
  name: 'Master-Detail (kompletter Flow)',
  render: () => ({ components: { KundenMasterDetailDemo }, template: '<KundenMasterDetailDemo />' }),
}

export const Masterliste: Story = {
  render: () => ({
    components: { KundenMasterListe },
    setup: () => ({ personen: PERSONEN, organisationen: ORGANISATIONEN, mitgliedschaften: MITGLIEDSCHAFTEN }),
    template: '<KundenMasterListe :personen="personen" :organisationen="organisationen" :mitgliedschaften="mitgliedschaften" initial-selected="p1" />',
  }),
}

export const DetailkopfPerson: Story = {
  name: 'KontaktDetailHeader — Person (Werbesperre)',
  render: () => ({
    components: { KontaktDetailHeader },
    setup: () => ({ p: PERSONEN[2] }),
    template: '<KontaktDetailHeader :person="p" />',
  }),
}

export const DetailkopfFirma: Story = {
  name: 'KontaktDetailHeader — Firma',
  render: () => ({
    components: { KontaktDetailHeader },
    setup: () => ({ o: ORGANISATIONEN[0] }),
    template: '<KontaktDetailHeader :org="o" />',
  }),
}

export const Tabs: Story = {
  render: () => ({
    components: { TabBar },
    data: () => ({ tab: 'stammdaten', tabs: [
      { key: 'stammdaten', label: 'Stammdaten' },
      { key: 'zugehoerigkeiten', label: 'Zugehörigkeiten' },
      { key: 'login', label: 'Loginversuche', bubble: 1 },
    ] }),
    template: '<TabBar v-model="tab" :tabs="tabs" />',
  }),
}
