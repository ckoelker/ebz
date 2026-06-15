import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { shopClient } from '@/api/client';
import { graphql, type DocumentType } from '@/gql';
import { unwrap } from '@/api/result';

// Showcase M5 — Authentifizierung (Leitplanke §8a-1/2/3/15).
//
// Auth über STANDARD-OIDC (Authorization Code + PKCE) via oidc-client-ts — anbieter-neutral statt
// des Keycloak-spezifischen keycloak-js. Provider per VITE_OIDC_*/VITE_KEYCLOAK_* konfigurierbar.
//
// Zwei Token-Welten, bewusst getrennt:
//   • OIDC-access_token → NUR einmalig, um die Vendure-`authenticate`-Mutation zu autorisieren
//                         (Strategy-Name "keycloak", Realm ebz-customers).
//   • Vendure-Session   → danach trägt die same-origin Cookie alle Shop-API-Calls (api/client.ts).
//
// Der Shop ist öffentlich: beim App-Start nur stiller Session-Load (kein Zwang); Login/Registrierung
// erst am Checkout (§8a-3). Single-Logout: erst Vendure-, dann OIDC-Session (§8a-15).

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

const AUTHORITY =
  import.meta.env.VITE_OIDC_AUTHORITY ||
  `${import.meta.env.VITE_KEYCLOAK_URL}/realms/${import.meta.env.VITE_KEYCLOAK_REALM}`;
const CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID || import.meta.env.VITE_KEYCLOAK_CLIENT_ID;

const userManager = new UserManager({
  authority: AUTHORITY,
  client_id: CLIENT_ID,
  redirect_uri: `${location.origin}/auth/callback`,
  post_logout_redirect_uri: `${location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: true, // Renew via Refresh-Token (Public-Client) – kein Iframe
  monitorSession: false,
  userStore: new WebStorageStateStore({ store: localStorage }),
});

export const useAuthStore = defineStore('auth', () => {
  const ready = ref(false);
  const customer = ref<ActiveCustomer | null>(null);
  const isLoggedIn = computed(() => customer.value !== null);
  // Geteiltes Init-Promise: main.ts UND der Router-Guard rufen init() auf — beide müssen DENSELBEN
  // Lauf abwarten (sonst entscheidet der Guard, bevor der OIDC-Callback verarbeitet ist → Loop).
  let initPromise: Promise<void> | null = null;

  /** Vendure-Session aus einem OIDC-access_token herstellen (§8a-1). */
  async function exchangeToken(accessToken: string): Promise<void> {
    const res = await shopClient.mutation(Authenticate, { token: accessToken }).toPromise();
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
   * Bootstrap (idempotent): verarbeitet den OIDC-Redirect-Callback ({@code /auth/callback}) bzw. lädt
   * eine bestehende Session (lokaler OIDC-User → Exchange, sonst bestehende Vendure-Session).
   * Mehrfachaufrufe teilen sich denselben Lauf, sodass der Router-Guard erst NACH der Auth entscheidet.
   */
  function init(): Promise<void> {
    if (!initPromise) initPromise = doInit();
    return initPromise;
  }

  async function doInit(): Promise<void> {
    try {
      if (location.pathname === '/auth/callback') {
        const user = await userManager.signinRedirectCallback();
        const returnTo = (user.state as { returnTo?: string } | undefined)?.returnTo || '/';
        history.replaceState(null, '', returnTo);
        await exchangeToken(user.access_token);
      } else {
        const user = await userManager.getUser();
        if (user && !user.expired) {
          await exchangeToken(user.access_token);
        } else {
          // evtl. besteht schon eine Vendure-Session (Reload ohne neuen OIDC-Login)
          await loadCustomer();
        }
      }
    } catch (e) {
      console.warn('[auth] OIDC-Init/Exchange fehlgeschlagen:', e);
      if (location.pathname === '/auth/callback') {
        history.replaceState(null, '', '/');
      }
    } finally {
      ready.value = true;
    }
  }

  /** Login am Checkout: Redirect zum OIDC-Provider, danach zurück zu `redirectPath`. */
  async function login(redirectPath = '/checkout'): Promise<void> {
    await userManager.signinRedirect({ state: { returnTo: redirectPath } });
  }

  /** Registrierung über die Keycloak-Registrierungsseite (Standard-OIDC {@code prompt=create}, §8a). */
  async function register(redirectPath = '/checkout'): Promise<void> {
    await userManager.signinRedirect({ state: { returnTo: redirectPath }, extraQueryParams: { prompt: 'create' } });
  }

  /** Single-Logout (§8a-15): erst Vendure-Session, dann OIDC-Session. */
  async function logout(): Promise<void> {
    await shopClient.mutation(Logout, {}).toPromise();
    customer.value = null;
    await userManager.removeUser();
    await userManager.signoutRedirect();
  }

  return { ready, customer, isLoggedIn, init, login, register, logout, exchangeToken };
});
