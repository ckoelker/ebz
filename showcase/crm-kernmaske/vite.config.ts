import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import ui from '@nuxt/ui/vite'

// Vue-only-Einbindung von Nuxt UI 4 (kein Nuxt): der @nuxt/ui/vite-Plugin liefert
// Auto-Imports der U-Komponenten + Theme. EBZ-Branding = Nuxt-Default-Theme, nur
// die Primärfarbe auf das EBZ-Navy (Scale `navy`, definiert in assets/css/main.css).
export default defineConfig({
  plugins: [
    vue(),
    ui({
      ui: {
        colors: {
          primary: 'navy',
          neutral: 'slate',
        },
      },
    }),
  ],
  resolve: {
    alias: {
      // Geteilte CRM-Primitive (eine Quelle für Storybook + mdm) als First-Party-Quelle.
      '@crm-ui': fileURLToPath(new URL('../crm-ui/src', import.meta.url)),
    },
  },
  server: {
    // Storybook-Dev darf das Geschwister-Verzeichnis crm-ui ausliefern (außerhalb des Roots).
    fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] },
  },
})
