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
})
