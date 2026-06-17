import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import ui from '@nuxt/ui/vite';

// MDM-Cockpit (Formularverwaltung P1.0). Eigener Dev-Port 5174 (Storefront = 5173).
// Proxy für /bildung → Quarkus-bildung-Service: gleicher Origin im Browser (kein CORS),
// passt zur generierten Client-Basis-URL ''. Keycloak (P1.2) läuft bewusst NICHT über Proxy.
// UI: Nuxt UI 4 (Vue-only über @nuxt/ui/vite) — gleiche Basis wie die CRM-Kernmaske;
// EBZ-Branding = Nuxt-Default-Theme mit Primärfarbe auf EBZ-Navy (Scale in assets/css/main.css).
const INTEGRATION = process.env.INTEGRATION_ORIGIN || 'http://localhost:8090';

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
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/bildung': { target: INTEGRATION, changeOrigin: true },
      '/party': { target: INTEGRATION, changeOrigin: true },
      '/q': { target: INTEGRATION, changeOrigin: true },
    },
  },
});
