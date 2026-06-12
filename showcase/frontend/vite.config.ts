import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Showcase M5 — Frontend-Dev-Server.
// Leitplanke §8a-2/9: alles über denselben Origin laufen lassen (kein CORS),
// damit die Vendure-Session-Cookie greift. Daher Proxy für BEIDE Backend-Pfade:
//   /shop-api  → GraphQL (Katalog, Cart, Checkout)
//   /assets    → Produktbilder (AssetServerPlugin) — sonst kommen keine Bilder
// Keycloak (localhost:8088) läuft bewusst NICHT über den Proxy: der Browser
// redirektet per keycloak-js direkt dorthin (OIDC/PKCE).
const VENDURE = process.env.VENDURE_ORIGIN || 'http://localhost:3000';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/shop-api': { target: VENDURE, changeOrigin: true },
      '/assets': { target: VENDURE, changeOrigin: true },
    },
  },
});
