// EBZ Akademie — Nuxt-SSR-Storefront (Produktkatalog P2-Gerüst).
// SSR an (SEO/Speaking-URLs), @nuxt/ui (4) als UI-Lib, EBZ-Navy, nur Deutsch.
// Alle Shop-API-Aufrufe laufen serverseitig über Nuxt-Server-Routen (server/api/*),
// damit die Vendure-URL serverseitig bleibt (kein CORS, keine URL-Dualität im Browser).
import { fileURLToPath } from 'node:url';

export default defineNuxtConfig({
  compatibilityDate: '2025-06-18',
  ssr: true,
  modules: ['@nuxt/ui'],
  css: ['~/assets/css/main.css'],
  // Geteilter Domain-Core (Geld/Datum/Status) + Kunden-Primitive — eine Quelle für mdm/portal/storefront.
  alias: {
    '@crm-ui': fileURLToPath(new URL('../../packages/crm-ui/src', import.meta.url)),
    '@customer-ui': fileURLToPath(new URL('../../packages/customer-ui/src', import.meta.url)),
    '@ui-base': fileURLToPath(new URL('../../packages/ui-base/src', import.meta.url)),
  },
  // Build/Dev-Server darf das Geschwister-Verzeichnis crm-ui (außerhalb des storefront-Roots) lesen.
  vite: { server: { fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] } } },
  // Shop ist LIGHT-only. preference/fallback 'light' (nicht OS-`system`).
  // Eigener storageKey: entwertet alte `nuxt-color-mode`-Einträge (frühere Builds speicherten
  // teils `system` → folgte dunklem OS und kippte den Shop nach dark trotz Light-Default).
  // Mit frischem Key starten bestehende UND neue Besucher deterministisch auf light.
  colorMode: {
    preference: 'light',
    fallback: 'light',
    storageKey: 'ebz-shop-color-mode',
  },
  runtimeConfig: {
    // Serverseitig: Container-URL (http://server:3000), Dev-Default localhost.
    shopApiUrl: process.env.SHOP_API_URL || 'http://localhost:3000/shop-api',
    // Integration-Backend (server-only) für die E-Learning-Einschreibung (P7c).
    integrationUrl: process.env.INTEGRATION_URL || 'http://localhost:8090',
    // Service-Account (client_credentials) im Staff-Realm für den Einschreibungs-Trigger.
    kcStaffTokenUrl: process.env.KC_STAFF_TOKEN_URL || 'http://localhost:8088/realms/ebz-staff/protocol/openid-connect/token',
    kcServiceClientId: process.env.KC_SERVICE_CLIENT_ID || 'storefront-service',
    kcServiceClientSecret: process.env.KC_SERVICE_CLIENT_SECRET || '',
    public: {
      // Browser-seitiges Kunden-SSO (Keycloak ebz-customers); localhost:8088 = korrekter Token-Issuer.
      kcUrl: process.env.NUXT_PUBLIC_KC_URL || 'http://localhost:8088',
      kcRealm: process.env.NUXT_PUBLIC_KC_REALM || 'ebz-customers',
      kcClientId: process.env.NUXT_PUBLIC_KC_CLIENT_ID || 'shop-frontend',
      // Kunden-Self-Service-Portal (eigene SPA, geteilte Keycloak-SSO ebz-customers) — „Mein Konto".
      portalUrl: process.env.NUXT_PUBLIC_PORTAL_URL || 'http://localhost:5175',
    },
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
