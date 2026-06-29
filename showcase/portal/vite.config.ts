import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import ui from '@nuxt/ui/vite';

// EBZ Außenportal — Dev-Port 5175 (Shop = 5173, MDM = 5174). Proxy /party + /lms + /kommunikation + /q
// → Quarkus-integration: gleicher Origin im Browser (kein CORS), passt zur generierten Client-Basis ''.
// Keycloak läuft bewusst NICHT über den Proxy (direkt gegen keycloak.localhost:8080).
// UI: Nuxt UI 4 (Vue-only über @nuxt/ui/vite) — Nuxt-Default-Theme, Primärfarbe = EBZ-Navy (Scale `navy`
// in assets/css/main.css); Light-Theme erzwungen in main.ts. Gleicher Stack wie das MDM-Cockpit.
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
      // Geteilte CRM-Primitive/Domain-Logik (eine Quelle für mdm + portal) als First-Party-Quelle.
      '@crm-ui': fileURLToPath(new URL('../crm-ui/src', import.meta.url)),
      // Gebrandete Kunden-Primitive (StatusBadge/PreisBadge; Schaufenster: kunden-kernmaske).
      '@customer-ui': fileURLToPath(new URL('../customer-ui/src', import.meta.url)),
      // Neutrale UI-Infrastruktur (ListenTabelle/FormFeld/LeerZustand) — von allen geteilt.
      '@ui-base': fileURLToPath(new URL('../ui-base/src', import.meta.url)),
    },
  },
  server: {
    port: 5175,
    // Dev-Server darf das Geschwister-Verzeichnis crm-ui ausliefern (außerhalb des portal-Roots).
    fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] },
    proxy: {
      '/party': { target: INTEGRATION, changeOrigin: true },
      '/lms': { target: INTEGRATION, changeOrigin: true },
      '/kommunikation': { target: INTEGRATION, changeOrigin: true },
      // Thread-Chat-WebSocket (K2): interaktiver „neue Nachricht"-Notifier.
      '/ws': { target: INTEGRATION, ws: true, changeOrigin: true },
      '/q': { target: INTEGRATION, changeOrigin: true },
    },
  },
});
