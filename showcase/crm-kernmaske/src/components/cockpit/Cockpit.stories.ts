import type { Meta, StoryObj } from '@storybook/vue3-vite'
import ProzessStatusTabelle from './ProzessStatusTabelle.vue'
import EingriffeKarten from './EingriffeKarten.vue'
import SonderfaelleKarten from './SonderfaelleKarten.vue'
import AnrufToast from './AnrufToast.vue'

const meta: Meta = { title: 'Cockpit/Betrieb' }
export default meta
type Story = StoryObj

export const Prozesse: Story = {
  name: 'ProzessStatusTabelle',
  render: () => ({ components: { ProzessStatusTabelle }, template: '<ProzessStatusTabelle />' }),
}

export const Eingriffe: Story = {
  name: 'EingriffeKarten (HITL)',
  render: () => ({ components: { EingriffeKarten }, template: '<EingriffeKarten />' }),
}

export const Sonderfaelle: Story = {
  name: 'SonderfaelleKarten',
  render: () => ({ components: { SonderfaelleKarten }, template: '<SonderfaelleKarten />' }),
}

export const AnrufBekannt: Story = {
  name: 'AnrufToast — bekannte Nummer',
  render: () => ({ components: { AnrufToast }, template: '<AnrufToast nummer-e164="+491701112233" />' }),
}

export const AnrufUnbekannt: Story = {
  name: 'AnrufToast — unbekannte Nummer',
  render: () => ({ components: { AnrufToast }, template: '<AnrufToast nummer-e164="+49999000111" />' }),
}
