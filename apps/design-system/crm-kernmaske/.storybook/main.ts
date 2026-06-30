import type { StorybookConfig } from '@storybook/vue3-vite'

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(ts|tsx)'],
  // a11y: Barrierefreiheits-Checks je Story (axe-core) — Panel + optional Test-Gate (siehe preview.ts).
  addons: ['@storybook/addon-a11y'],
  framework: {
    name: '@storybook/vue3-vite',
    options: {},
  },
  // vite.config.ts (inkl. @nuxt/ui/vite-Plugin) wird vom Framework automatisch gemerged.
}

export default config
