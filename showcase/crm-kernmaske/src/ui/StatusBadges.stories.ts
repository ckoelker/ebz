import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import StatusBadges from '@crm-ui/ui/StatusBadges.vue'

const meta = {
  title: 'Primitives/StatusBadges',
  component: StatusBadges,
  argTypes: {
    status: { control: 'text' },
    werbesperre: { control: 'boolean' },
    auskunftssperre: { control: 'boolean' },
    unvollstaendig: { control: 'boolean' },
  },
  args: { status: 'AKTIV', werbesperre: false, auskunftssperre: false, unvollstaendig: false },
} satisfies Meta<typeof StatusBadges>

export default meta
type Story = StoryObj<typeof meta>

export const Aktiv: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(canvas.getByText('AKTIV')).toBeInTheDocument()
  },
}

export const Provisorisch: Story = { args: { status: 'PROVISORISCH', unvollstaendig: true } }
export const MitSperren: Story = { args: { werbesperre: true, auskunftssperre: true } }
