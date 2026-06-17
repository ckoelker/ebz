import type { StorybookConfig } from '@storybook/vue3-vite'

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(ts|tsx)'],
  addons: [],
  framework: {
    name: '@storybook/vue3-vite',
    options: {},
  },
  // vite.config.ts (inkl. @nuxt/ui/vite-Plugin) wird vom Framework automatisch gemerged.
}

export default config
