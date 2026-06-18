// EBZ Akademie — Nuxt-SSR-Storefront (Produktkatalog P2-Gerüst).
// SSR an (SEO/Speaking-URLs), @nuxt/ui (4) als UI-Lib, EBZ-Navy, nur Deutsch.
// Alle Shop-API-Aufrufe laufen serverseitig über Nuxt-Server-Routen (server/api/*),
// damit die Vendure-URL serverseitig bleibt (kein CORS, keine URL-Dualität im Browser).
export default defineNuxtConfig({
  compatibilityDate: '2025-06-18',
  ssr: true,
  modules: ['@nuxt/ui'],
  css: ['~/assets/css/main.css'],
  runtimeConfig: {
    // Serverseitig: Container-URL (http://server:3000), Dev-Default localhost.
    shopApiUrl: process.env.SHOP_API_URL || 'http://localhost:3000/shop-api',
  },
  app: {
    head: {
      htmlAttrs: { lang: 'de' },
      titleTemplate: (t) => (t ? `${t} · EBZ Akademie` : 'EBZ Akademie — Weiterbildung'),
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      ],
    },
  },
})
