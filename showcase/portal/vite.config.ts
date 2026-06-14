import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// EBZ Außenportal — Dev-Port 5175 (Shop = 5173, MDM = 5174). Proxy /party → Quarkus-integration:
// gleicher Origin im Browser (kein CORS), passt zur generierten Client-Basis ''. Keycloak läuft
// bewusst NICHT über den Proxy (direkt gegen keycloak.localhost:8080).
const INTEGRATION = process.env.INTEGRATION_ORIGIN || 'http://localhost:8090';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5175,
    proxy: {
      '/party': { target: INTEGRATION, changeOrigin: true },
      '/q': { target: INTEGRATION, changeOrigin: true },
    },
  },
});
