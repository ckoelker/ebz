import type { Meta, StoryObj } from '@storybook/vue3-vite'
import MasterListItem from '@crm-ui/ui/MasterListItem.vue'

const meta = {
  title: 'Primitives/MasterListItem',
  component: MasterListItem,
  argTypes: {
    label: { control: 'text' },
    sub: { control: 'text' },
    sub2: { control: 'text' },
    org: { control: 'boolean', description: 'Firma (eckiger Avatar).' },
    active: { control: 'boolean', description: 'Ausgewählter Eintrag (Akzentbalken).' },
    warn: { control: 'boolean' },
    blocked: { control: 'boolean' },
  },
  args: {
    label: 'Anna Schmidt', sub: 'Wohnbau Rhein-Ruhr eG · Vorstand', sub2: 'Düsseldorf',
    org: false, active: true, warn: false, blocked: false,
  },
  // Listeneintrag in einem schmalen, gerahmten Container zeigen (wie in der echten Master-Liste).
  decorators: [() => ({ template: '<div class="w-80 ring-1 ring-default rounded-lg overflow-hidden"><story /></div>' })],
} satisfies Meta<typeof MasterListItem>

export default meta
type Story = StoryObj<typeof meta>

export const Aktiv: Story = {}
export const Warnung: Story = { args: { label: 'Markus Meyer', sub: 'Privatkontakt', sub2: '*1981', active: false, warn: true } }
export const Gesperrt: Story = { args: { label: 'Petra Sperr', sub: 'Privatkontakt', sub2: '', active: false, blocked: true } }
export const Firma: Story = {
  args: { label: 'Meyer Immobilien GmbH', sub: 'Makler · Köln', sub2: '2 Personen verknüpft', active: false, org: true },
}
