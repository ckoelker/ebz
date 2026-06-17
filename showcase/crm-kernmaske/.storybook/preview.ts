import type { Preview } from '@storybook/vue3-vite'
import { setup } from '@storybook/vue3-vite'
import ui from '@nuxt/ui/vue-plugin'
import StoryHost from './StoryHost.vue'
import '../src/assets/css/main.css'

// Nuxt-UI-Vue-Plugin global installieren (Locale, programmatische Overlays/Toasts/Tooltips).
setup((app) => {
  app.use(ui)
})

const preview: Preview = {
  decorators: [
    (story) => ({
      components: { StoryHost, story },
      template: '<StoryHost><story /></StoryHost>',
    }),
  ],
  parameters: {
    layout: 'fullscreen',
    controls: {
      matchers: { color: /(background|color)$/i, date: /Date$/i },
    },
    options: {
      storySort: {
        order: ['Tokens', 'Shell', 'Cockpit', 'Kundenstamm', 'Bausteine', 'Tabs', 'Dialoge'],
      },
    },
  },
}

export default preview
