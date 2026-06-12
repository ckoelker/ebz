import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import Keycloak from 'keycloak-js';
import { shopClient } from '@/api/client';
import { graphql, type DocumentType } from '@/gql';
import { unwrap } from '@/api/result';

// Showcase M5 — Authentifizierung (Leitplanke §8a-1/2/3/15).
//
// Zwei Token-Welten, bewusst getrennt:
//   • Keycloak-Token  → NUR einmalig, um die Vendure-`authenticate`-Mutation zu
//                       autorisieren (Strategy-Name "keycloak").
//   • Vendure-Session → danach trägt die same-origin Cookie alle Shop-API-Calls
//                       (siehe api/client.ts). Der Keycloak-Token wandert NICHT
//                       an jeden Request.
//
// Der Shop ist öffentlich: beim App-Start nur ein lautloses check-sso (kein
// Zwang). Login wird erst am Checkout ausgelöst (§8a-3).

const Authenticate = graphql(`
  mutation Authenticate($token: String!) {
    authenticate(input: { keycloak: { token: $token } }) {
      __typename
      ... on CurrentUser { id identifier }
      ... on ErrorResult { errorCode message }
    }
  }
`);

const ActiveCustomerQuery = graphql(`
  query ActiveCustomer {
    activeCustomer { id firstName lastName emailAddress }
  }
`);

const Logout = graphql(`mutation Logout { logout { success } }`);

export type ActiveCustomer = NonNullable<DocumentType<typeof ActiveCustomerQuery>['activeCustomer']>;

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

export const useAuthStore = defineStore('auth', () => {
  const ready = ref(false);
  const customer = ref<ActiveCustomer | null>(null);
  const isLoggedIn = computed(() => customer.value !== null);
  // Geteiltes Init-Promise: main.ts UND der Router-Guard rufen init() auf — beide
  // müssen DENSELBEN Lauf abwarten (sonst zweite keycloak.init() = Fehler, und der
  // Guard entscheidet, bevor der Keycloak-Callback verarbeitet ist → Redirect-Loop).
  let initPromise: Promise<void> | null = null;

  /** Vendure-Session aus dem aktuellen Keycloak-Token herstellen (§8a-1). */
  async function exchangeToken(): Promise<void> {
    if (!keycloak.authenticated || !keycloak.token) return;
    const res = await shopClient.mutation(Authenticate, { token: keycloak.token }).toPromise();
    if (res.error) throw res.error;
    unwrap(res.data!.authenticate); // ErrorResult → Exception (§8a-7)
    await loadCustomer();
  }

  async function loadCustomer(): Promise<void> {
    const res = await shopClient.query(
      ActiveCustomerQuery, {}, { requestPolicy: 'network-only' },
    ).toPromise();
    customer.value = res.data?.activeCustomer ?? null;
  }

  /**
   * Bootstrap (idempotent): lautloses check-sso bzw. Verarbeitung des Keycloak-
   * Redirect-Callbacks. Mehrfachaufrufe teilen sich denselben Lauf, sodass der
   * Router-Guard zuverlässig erst NACH abgeschlossener Auth entscheidet.
   */
  function init(): Promise<void> {
    if (!initPromise) initPromise = doInit();
    return initPromise;
  }

  async function doInit(): Promise<void> {
    try {
      await keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      });
      if (keycloak.authenticated) {
        await exchangeToken();
      } else {
        // evtl. besteht schon eine Vendure-Session (Reload ohne KC-Login)
        await loadCustomer();
      }
    } catch (e) {
      console.warn('[auth] Keycloak-Init/Exchange fehlgeschlagen:', e);
    } finally {
      ready.value = true;
    }
  }

  /** Login am Checkout: Redirect zu Keycloak, danach zurück zu `redirectPath`. */
  async function login(redirectPath = '/checkout'): Promise<void> {
    await keycloak.login({ redirectUri: `${location.origin}${redirectPath}` });
  }

  /** Registrierung läuft über die Keycloak-Registrierungsseite (§8a, kein Gast). */
  async function register(redirectPath = '/checkout'): Promise<void> {
    await keycloak.register({ redirectUri: `${location.origin}${redirectPath}` });
  }

  /** Single-Logout (§8a-15): erst Vendure-Session, dann Keycloak-Session. */
  async function logout(): Promise<void> {
    await shopClient.mutation(Logout, {}).toPromise();
    customer.value = null;
    await keycloak.logout({ redirectUri: `${location.origin}/` });
  }

  return { ready, customer, isLoggedIn, init, login, register, logout, exchangeToken };
});
