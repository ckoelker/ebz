import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import ui from '@nuxt/ui/vite';

// MDM-Cockpit (Formularverwaltung P1.0). Eigener Dev-Port 5174 (Storefront = 5173).
// Proxy für /bildung → Quarkus-bildung-Service: gleicher Origin im Browser (kein CORS),
// passt zur generierten Client-Basis-URL ''. Keycloak (P1.2) läuft bewusst NICHT über Proxy.
// UI: Nuxt UI 4 (Vue-only über @nuxt/ui/vite) — Nuxt-Default-Theme, aber Primärfarbe = EBZ-Navy
// (Scale `navy`, definiert in assets/css/main.css); Light-Theme erzwungen in main.ts.
const INTEGRATION = process.env.INTEGRATION_ORIGIN || 'http://localhost:8090';

export default defineConfig({
  plugins: [
    vue(),
    // EBZ-Branding: Primary = navy (#0b3a6f), Neutral = slate.
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
      // Geteilte CRM-Primitive (eine Quelle für Storybook + mdm) als First-Party-Quelle, nicht als
      // node_modules-Paket — so greifen Nuxt-UI-Auto-Imports + Tailwind-Content-Scan automatisch.
      '@crm-ui': fileURLToPath(new URL('../crm-ui/src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    // Dev-Server darf das Geschwister-Verzeichnis crm-ui ausliefern (außerhalb des mdm-Roots).
    fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] },
    proxy: {
      '/bildung': { target: INTEGRATION, changeOrigin: true },
      '/crm': { target: INTEGRATION, changeOrigin: true },
      '/party': { target: INTEGRATION, changeOrigin: true },
      // Rechnungs-Cockpit: Beleg-Lebenszyklus (Liste/ZUGFeRD/Versand/Korrekturen/Zahlung/Sonderrechnung).
      '/rechnung': { target: INTEGRATION, changeOrigin: true },
      // K2: Admin↔Person-Nachrichten (Threads + Co-Pilot).
      '/kommunikation': { target: INTEGRATION, changeOrigin: true },
      // HubSpot-Outbound-Sync-Cockpit (Auftrags-Queue, Backfill, Recht auf Vergessen).
      '/hubspot': { target: INTEGRATION, changeOrigin: true },
      '/q': { target: INTEGRATION, changeOrigin: true },
      // A15: WebSocket-Anreicherung (ws: true für das Upgrade).
      '/ws': { target: INTEGRATION, changeOrigin: true, ws: true },
    },
  },
});
