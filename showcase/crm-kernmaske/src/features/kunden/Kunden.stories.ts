import type { Meta, StoryObj } from '@storybook/vue3-vite'
import KundenMasterListe from './KundenMasterListe.vue'
import KontaktDetailHeader from './KontaktDetailHeader.vue'
import CrmKontaktDetailHeader from '@crm-ui/ui/KontaktDetailHeader.vue'
import TabBar from '@crm-ui/ui/TabBar.vue'
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

export const DetailkopfBadges: Story = {
  name: 'KontaktDetailHeader — Zusatz-Badges (#badges-Slot)',
  render: () => ({
    components: { CrmKontaktDetailHeader },
    template: `<CrmKontaktDetailHeader title="Wohnbau Rhein-Ruhr eG" :org="true" status="AKTIV" :meta="['eG','DE123456789']">
      <template #badges>
        <UBadge color="info" variant="soft" size="sm">Ausbildungsbetrieb</UBadge>
        <UBadge color="neutral" variant="soft" size="sm">WEG-Verwalter</UBadge>
      </template>
      <template #actions>
        <UButton color="primary" size="sm" icon="i-lucide-pencil">Bearbeiten</UButton>
      </template>
    </CrmKontaktDetailHeader>`,
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
