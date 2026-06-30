import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import PartyAvatar from '@crm-ui/ui/PartyAvatar.vue'

const meta = {
  title: 'Primitives/PartyAvatar',
  component: PartyAvatar,
  argTypes: {
    name: { control: 'text', description: 'Anzeigename — Initialen werden abgeleitet.' },
    org: { control: 'boolean', description: 'Firma (eckig) statt Person (rund).' },
    size: { control: 'select', options: ['3xs', '2xs', 'xs', 'sm', 'md', 'lg', 'xl'] },
  },
  args: { name: 'Anna Schmidt', org: false, size: 'lg' },
} satisfies Meta<typeof PartyAvatar>

export default meta
type Story = StoryObj<typeof meta>

export const Person: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    // initialen('Anna Schmidt') → „AS"
    await expect(canvas.getByText('AS')).toBeInTheDocument()
  },
}

export const Firma: Story = { args: { name: 'Wohnbau Rhein-Ruhr eG', org: true } }
export const Klein: Story = { args: { size: '2xs' } }
