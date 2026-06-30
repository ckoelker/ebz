import type { Meta, StoryObj } from '@storybook/vue3-vite'
import HealthDot from '@crm-ui/ui/HealthDot.vue'

const meta = {
  title: 'Primitives/HealthDot',
  component: HealthDot,
  argTypes: {
    health: { control: 'inline-radio', options: ['ok', 'warn', 'err'] },
    label: { control: 'text' },
  },
  args: { health: 'ok', label: 'OK' },
} satisfies Meta<typeof HealthDot>

export default meta
type Story = StoryObj<typeof meta>

export const OK: Story = {}
export const Achtung: Story = { args: { health: 'warn', label: 'Achtung' } }
export const Fehler: Story = { args: { health: 'err', label: 'Fehler' } }
