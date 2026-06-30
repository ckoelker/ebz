import type { Meta, StoryObj } from '@storybook/vue3-vite'
import NeuePersonDialog from './NeuePersonDialog.vue'
import NeueFirmaDialog from './NeueFirmaDialog.vue'
import NotizDialog from './NotizDialog.vue'
import WiedervorlageDialog from './WiedervorlageDialog.vue'
import RechtAufVergessenDialog from './RechtAufVergessenDialog.vue'

const meta: Meta = { title: 'Dialoge/Anlegen & DSGVO' }
export default meta
type Story = StoryObj

export const NeuePerson: Story = {
  name: 'NeuePersonDialog (gestuft + Dublettenwarnung)',
  render: () => ({ components: { NeuePersonDialog }, template: '<NeuePersonDialog :open="true" />' }),
}
export const NeueFirma: Story = {
  name: 'NeueFirmaDialog (Daten ziehen)',
  render: () => ({ components: { NeueFirmaDialog }, template: '<NeueFirmaDialog :open="true" />' }),
}
export const Notiz: Story = {
  render: () => ({ components: { NotizDialog }, template: '<NotizDialog :open="true" />' }),
}
export const Wiedervorlage: Story = {
  render: () => ({ components: { WiedervorlageDialog }, template: '<WiedervorlageDialog :open="true" />' }),
}
export const RechtAufVergessen: Story = {
  name: 'RechtAufVergessenDialog (DSGVO)',
  render: () => ({ components: { RechtAufVergessenDialog }, template: '<RechtAufVergessenDialog :open="true" name="Markus Meyer" />' }),
}
