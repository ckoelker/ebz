import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import ui from '@nuxt/ui/vite'

// Vue-only-Einbindung von Nuxt UI 4 (kein Nuxt): der @nuxt/ui/vite-Plugin liefert Auto-Imports
// der U-Komponenten + Theme. Kunden-/Marketing-Branding = aktuell dasselbe EBZ-Navy wie Shop/MDM
// (Scale `navy` in assets/css/main.css). Eine eigene Marketing-Palette wäre ein Token-Swap dort.
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
      // Geteilter Domain-Core (EUR/Datum/Status) — invariante Logik, eine Quelle für Shop/Portal/Storybook.
      '@crm-ui': fileURLToPath(new URL('../crm-ui/src', import.meta.url)),
      // Geteilte Kunden-Primitive — dieses Storybook ist ihr Schaufenster; Quelle für Shop/Portal.
      '@customer-ui': fileURLToPath(new URL('../customer-ui/src', import.meta.url)),
      // Neutrale UI-Infrastruktur (ListenTabelle/FormFeld/LeerZustand) — von allen geteilt.
      '@ui-base': fileURLToPath(new URL('../ui-base/src', import.meta.url)),
    },
  },
  server: {
    // Storybook-Dev darf das Geschwister-Verzeichnis crm-ui ausliefern (außerhalb des Roots).
    fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] },
  },
})
