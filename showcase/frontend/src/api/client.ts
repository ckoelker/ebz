import { createClient, fetchExchange, cacheExchange } from '@urql/vue';

// Showcase M5 — Vendure Shop-API-Client.
// Leitplanke §8a-2: Token-Methode = COOKIE über den same-origin Vite-Proxy.
//   → `credentials: 'include'` schickt die Vendure-Session-Cookie bei jedem
//     Request mit; KEIN manuelles vendure-auth-token-Header-Handling nötig.
//   → Der anonyme Warenkorb (activeOrder) hängt an dieser Cookie und übersteht
//     Reloads. Beim Checkout-Login (Keycloak → authenticate) wird derselben
//     Session der Kunde zugeordnet (§8a-3, kein Gast).
//
// Hinweis Cache (§8a-17): urql cached per Dokument. Nach Mutationen, die den
// activeOrder verändern, geben wir den Order stets mit zurück und schreiben
// `additionalTypenames`/refetch dort, wo Listen betroffen sind.
export const shopClient = createClient({
  url: '/shop-api',
  exchanges: [cacheExchange, fetchExchange],
  fetchOptions: () => ({
    credentials: 'include',
  }),
});
