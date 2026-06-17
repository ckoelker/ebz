import type { Meta, StoryObj } from '@storybook/vue3-vite'
import TokenGallery from './TokenGallery.vue'

const meta: Meta<typeof TokenGallery> = {
  title: 'Tokens/Design-Tokens',
  component: TokenGallery,
}
export default meta

type Story = StoryObj<typeof TokenGallery>

export const Default: Story = {}
