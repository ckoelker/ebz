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
  // Best-practice: jede Story bekommt automatisch eine Docs-Seite (Autodocs) aus Args/ArgTypes.
  tags: ['autodocs'],
  // Farbschema-Umschalter in der Toolbar (Default: Light). Nuxt UI / Tailwind v4 schalten Dark über die
  // `.dark`-Klasse auf <html> — wir setzen sie explizit, damit nicht das System-Schema gewinnt.
  globalTypes: {
    theme: {
      description: 'Farbschema',
      defaultValue: 'light',
      toolbar: {
        title: 'Theme',
        icon: 'sun',
        items: [
          { value: 'light', title: 'Light', icon: 'sun' },
          { value: 'dark', title: 'Dark', icon: 'moon' },
        ],
        dynamicTitle: true,
      },
    },
  },
  decorators: [
    (story, context) => ({
      components: { StoryHost, story },
      setup() {
        document.documentElement.classList.toggle('dark', context.globals.theme === 'dark')
      },
      template: '<StoryHost><story /></StoryHost>',
    }),
  ],
  parameters: {
    layout: 'fullscreen',
    controls: {
      matchers: { color: /(background|color)$/i, date: /Date$/i },
    },
    // a11y-Addon: Verstöße erscheinen im Panel. `test: 'todo'` = sichtbar, aber (noch) nicht
    // build-/test-blockierend; auf 'error' stellen, sobald die Stories axe-sauber sind.
    a11y: { test: 'todo' },
    options: {
      storySort: {
        order: ['Willkommen', 'Tokens', 'UI', 'Listen & Masken'],
      },
    },
  },
}

export default preview
