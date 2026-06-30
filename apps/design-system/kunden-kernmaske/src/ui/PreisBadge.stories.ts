import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import PreisBadge from '@customer-ui/ui/PreisBadge.vue'

const meta = {
  title: 'UI/PreisBadge',
  component: PreisBadge,
  argTypes: {
    cent: { control: { type: 'number', step: 100 }, description: 'Betrag in Minor Units (Cent)' },
    currency: { control: 'inline-radio', options: ['EUR', 'CHF', 'USD'] },
    size: { control: 'inline-radio', options: ['sm', 'md', 'lg', 'xl'] },
    tone: { control: 'inline-radio', options: ['default', 'primary'] },
  },
  args: { cent: 149900, currency: 'EUR', size: 'md', tone: 'default' },
} satisfies Meta<typeof PreisBadge>

export default meta
type Story = StoryObj<typeof meta>

export const Standard: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(canvas.getByText('1.499,00 €')).toBeInTheDocument()
  },
}

export const MarkenAkzent: Story = { args: { tone: 'primary', size: 'xl' } }
export const Gross: Story = { args: { size: 'xl' } }
export const Schweizerfranken: Story = { args: { currency: 'CHF' } }
export const Gratis: Story = { args: { cent: 0 } }
