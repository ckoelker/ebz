import type { Meta, StoryObj } from '@storybook/vue3-vite'
import ComponentUsage from './ComponentUsage.vue'

const meta: Meta<typeof ComponentUsage> = {
  title: 'Übersicht/Komponenten-Verwendung',
  component: ComponentUsage,
}
export default meta

type Story = StoryObj<typeof ComponentUsage>
export const Default: Story = {}
