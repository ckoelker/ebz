import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

// Showcase M5 — Routen.
// Katalog/Produkt/Cart sind ÖFFENTLICH (Anforderung: Shop ohne Login einsehbar).
// Der Checkout erzwingt erst beim Betreten die Anmeldung (§8a-3, kein Gast) —
// der Guard dafür wird mit dem Auth-Store verdrahtet (Keycloak), sobald der
// Checkout-Flow steht.
const routes: RouteRecordRaw[] = [
  { path: '/', name: 'catalog', component: () => import('@/views/CatalogView.vue') },
  { path: '/produkt/:slug', name: 'product', component: () => import('@/views/ProductView.vue'), props: true },
  { path: '/warenkorb', name: 'cart', component: () => import('@/views/CartView.vue') },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('@/views/CheckoutView.vue'),
    meta: { requiresAuth: true },
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

// §8a-3: Checkout erzwingt Login. Der Auth-Store ist beim ersten Navigieren
// bereits initialisiert (main.ts wartet auf auth.init()). Ist niemand
// angemeldet, wird der Keycloak-Login mit Rückkehr zum Checkout ausgelöst.
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true;
  const { useAuthStore } = await import('@/stores/auth');
  const auth = useAuthStore();
  // Erst die (idempotente) Auth-Initialisierung abwarten — verarbeitet auch den
  // Keycloak-Redirect-Callback. Danach steht isLoggedIn verlässlich fest.
  await auth.init();
  if (auth.isLoggedIn) return true;
  await auth.login(to.path); // saubere Pfadangabe ohne Callback-Hash
  return false;
});
