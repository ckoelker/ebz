import type { Meta, StoryObj } from '@storybook/vue3-vite'
import ProzessStatusTabelle from './ProzessStatusTabelle.vue'
import EingriffeKarten from './EingriffeKarten.vue'
import SonderfaelleKarten from './SonderfaelleKarten.vue'
import AnrufToast from './AnrufToast.vue'
import { PROZESSE, EINGRIFFE, SONDERFAELLE, PERSONEN, ORGANISATIONEN } from '../../data/mock'

const meta: Meta = { title: 'Cockpit/Betrieb' }
export default meta
type Story = StoryObj

export const Prozesse: Story = {
  name: 'ProzessStatusTabelle',
  render: () => ({ components: { ProzessStatusTabelle }, setup: () => ({ prozesse: PROZESSE }), template: '<ProzessStatusTabelle :prozesse="prozesse" />' }),
}

export const Eingriffe: Story = {
  name: 'EingriffeKarten (HITL)',
  render: () => ({ components: { EingriffeKarten }, setup: () => ({ eingriffe: EINGRIFFE }), template: '<EingriffeKarten :eingriffe="eingriffe" />' }),
}

export const Sonderfaelle: Story = {
  name: 'SonderfaelleKarten',
  render: () => ({ components: { SonderfaelleKarten }, setup: () => ({ faelle: SONDERFAELLE }), template: '<SonderfaelleKarten :faelle="faelle" />' }),
}

export const AnrufBekannt: Story = {
  name: 'AnrufToast — bekannte Nummer',
  render: () => ({
    components: { AnrufToast },
    setup: () => ({ p: PERSONEN[0], o: ORGANISATIONEN[0] }),
    template: '<AnrufToast nummer-e164="+491701112233" :person="p" :firma="o" />',
  }),
}

export const AnrufUnbekannt: Story = {
  name: 'AnrufToast — unbekannte Nummer',
  render: () => ({ components: { AnrufToast }, template: '<AnrufToast nummer-e164="+49999000111" />' }),
}
