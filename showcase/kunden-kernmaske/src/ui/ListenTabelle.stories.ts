import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import ListenTabelle from '@ui-base/ui/ListenTabelle.vue'
import StatusBadge from '@customer-ui/ui/StatusBadge.vue'
import PreisBadge from '@customer-ui/ui/PreisBadge.vue'

const daten = [
  { nummer: 'RE-2026-0001', datum: '01.06.2026', betragCent: 149900, status: 'BEZAHLT' },
  { nummer: 'RE-2026-0002', datum: '15.06.2026', betragCent: 89000, status: 'AUSGESTELLT' },
  { nummer: 'RE-2026-0003', datum: '22.06.2026', betragCent: 24900, status: 'AUSGESTELLT' },
]

const columns = [
  { accessorKey: 'nummer', header: 'Beleg-Nr.' },
  { accessorKey: 'datum', header: 'Datum' },
  { id: 'betrag', header: 'Betrag' },
  { accessorKey: 'status', header: 'Status' },
]

// data/columns sind keine Control-tauglichen Werte (ausgeblendet); `loading`/`empty` als Args.
// Zell-Slots (PreisBadge + StatusBadge) werden durch ListenTabelle an UTable durchgereicht.
const meta = {
  title: 'Listen & Masken/ListenTabelle',
  component: ListenTabelle,
  argTypes: {
    data: { control: false },
    columns: { control: false },
    loading: { control: 'boolean' },
    empty: { control: 'text' },
  },
  args: { data: daten, columns, loading: false, empty: 'Keine Rechnungen vorhanden.' },
  render: (args) => ({
    components: { ListenTabelle, StatusBadge, PreisBadge },
    setup: () => ({ args }),
    template: `<ListenTabelle v-bind="args">
      <template #betrag-cell="{ row }"><PreisBadge :cent="row.original.betragCent" /></template>
      <template #status-cell="{ row }"><StatusBadge art="rechnung" :status="row.original.status" /></template>
    </ListenTabelle>`,
  }),
} satisfies Meta<typeof ListenTabelle>

export default meta
type Story = StoryObj<typeof meta>

export const Rechnungsliste: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(canvas.getByText('RE-2026-0001')).toBeInTheDocument()
    await expect(canvas.getByText('1.499,00 €')).toBeInTheDocument()
  },
}

export const Laedt: Story = { args: { loading: true } }
export const Leer: Story = { args: { data: [] } }
