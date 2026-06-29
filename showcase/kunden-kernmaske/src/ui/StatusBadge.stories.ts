import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import StatusBadge from './StatusBadge.vue'

// State-of-the-art: interaktive Controls über argTypes, je Story ein Args-Satz, Interaction-Test (play).
const meta = {
  title: 'UI/StatusBadge',
  component: StatusBadge,
  argTypes: {
    art: {
      control: 'inline-radio',
      options: ['einschreibung', 'rechnung'],
      description: 'Fachlicher Kontext — bestimmt Farb-/Text-Mapping aus dem Domain-Core.',
    },
    status: {
      control: 'select',
      options: ['ANGEFORDERT', 'EINGESCHRIEBEN', 'FEHLGESCHLAGEN', 'AUSGESTELLT', 'BEZAHLT'],
    },
    variant: { control: 'inline-radio', options: ['soft', 'solid', 'outline', 'subtle'] },
    size: { control: 'inline-radio', options: ['sm', 'md', 'lg'] },
  },
  args: { art: 'einschreibung', status: 'EINGESCHRIEBEN', variant: 'soft', size: 'sm' },
} satisfies Meta<typeof StatusBadge>

export default meta
type Story = StoryObj<typeof meta>

export const Eingeschrieben: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    // Klartext kommt aus dem geteilten Domain-Core (einschreibungStatusText: EINGESCHRIEBEN → „verfügbar").
    await expect(canvas.getByText('verfügbar')).toBeInTheDocument()
  },
}

export const WirdBereitgestellt: Story = { args: { status: 'ANGEFORDERT' } }
export const Fehlgeschlagen: Story = { args: { status: 'FEHLGESCHLAGEN' } }
export const RechnungBezahlt: Story = { args: { art: 'rechnung', status: 'BEZAHLT' } }
export const RechnungAusgestellt: Story = { args: { art: 'rechnung', status: 'AUSGESTELLT' } }
