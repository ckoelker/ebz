import type { Meta, StoryObj } from '@storybook/vue3-vite'
import FormFeld from '@ui-base/ui/FormFeld.vue'

const meta = {
  title: 'Listen & Masken/FormFeld',
  component: FormFeld,
  argTypes: {
    label: { control: 'text' },
    required: { control: 'boolean' },
    help: { control: 'text' },
    error: { control: 'text' },
  },
  args: { label: 'E-Mail', required: true, help: 'Wir nutzen sie nur für Buchungsbestätigungen.' },
  render: (args) => ({
    components: { FormFeld },
    setup: () => ({ args }),
    template: `<div class="max-w-sm"><FormFeld v-bind="args"><UInput placeholder="name@firma.de" class="w-full" /></FormFeld></div>`,
  }),
} satisfies Meta<typeof FormFeld>

export default meta
type Story = StoryObj<typeof meta>

export const Pflichtfeld: Story = {}
export const MitFehler: Story = { args: { error: 'Bitte eine gültige E-Mail angeben.' } }
export const Optional: Story = { args: { required: false, help: '' } }
