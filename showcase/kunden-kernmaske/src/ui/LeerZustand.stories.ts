import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import LeerZustand from '@ui-base/ui/LeerZustand.vue'

const meta = {
  title: 'Listen & Masken/LeerZustand',
  component: LeerZustand,
  argTypes: {
    icon: { control: 'text' },
    titel: { control: 'text' },
    text: { control: 'text' },
    loading: { control: 'boolean' },
  },
  args: {
    titel: 'Noch keine Trainings gebucht',
    text: 'Sobald Sie ein Training buchen, erscheint es hier.',
    icon: 'i-lucide-graduation-cap',
    loading: false,
  },
} satisfies Meta<typeof LeerZustand>

export default meta
type Story = StoryObj<typeof meta>

export const Leer: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(canvas.getByText('Noch keine Trainings gebucht')).toBeInTheDocument()
  },
}

export const Laedt: Story = { args: { loading: true } }

export const MitAktion: Story = {
  render: (args) => ({
    components: { LeerZustand },
    setup: () => ({ args }),
    template: `<LeerZustand v-bind="args"><template #aktion><UButton icon="i-lucide-search">Kurse entdecken</UButton></template></LeerZustand>`,
  }),
}
