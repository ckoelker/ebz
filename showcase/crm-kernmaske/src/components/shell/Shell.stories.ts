import type { Meta, StoryObj } from '@storybook/vue3-vite'
import Topbar from './Topbar.vue'
import Rail from './Rail.vue'
import CockpitShell from './CockpitShell.vue'

const meta: Meta = {
  title: 'Shell/Übersicht',
}
export default meta
type Story = StoryObj

export const TopbarStory: Story = {
  name: 'Topbar (Suche + CTI + User)',
  render: () => ({
    components: { Topbar },
    template: '<div class="-m-4"><Topbar /></div>',
  }),
}

export const RailStory: Story = {
  name: 'Rail (Navigation + Quicklinks)',
  render: () => ({
    components: { Rail },
    template: '<div class="-m-4 h-[600px]"><Rail active="kundenstamm" :kundenstamm-aktiv="true" /></div>',
  }),
}

export const Shell: Story = {
  name: 'CockpitShell (komplett)',
  render: () => ({
    components: { CockpitShell },
    template: '<div class="-m-4"><CockpitShell /></div>',
  }),
}
