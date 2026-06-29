import type { Meta, StoryObj } from '@storybook/vue3-vite'
import Tokens from './Tokens.vue'

const meta = {
  title: 'Tokens/Design-Tokens',
  component: Tokens,
  parameters: {
    docs: {
      description: {
        component:
          'Farb-, Radien- und semantische Tokens des EBZ-Kunden-Designs (Navy-Primärmarke, Nuxt-UI-Semantik). '
          + 'Eine eigene Marketing-Palette ist ein Token-Swap in src/assets/css/main.css.',
      },
    },
  },
} satisfies Meta<typeof Tokens>

export default meta
type Story = StoryObj<typeof meta>

export const Galerie: Story = {}
