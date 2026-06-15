// SSO über STANDARD-OIDC (Authorization Code + PKCE) via oidc-client-ts — anbieter-neutral, spricht
// jeden OIDC-Provider. Hier für Keycloak (Realm ebz-staff, Public-Client mdm-cockpit), austauschbar per
// VITE_OIDC_*-Env. Authority = keycloak.localhost:8080 (Browser löst *.localhost→127.0.0.1; derselbe
// Issuer, den der Quarkus-Service validiert). Liste (GET) bleibt anonym; Schreiben braucht die Rolle
// katalog-pflege → der Request-Interceptor hängt das access_token an alle API-Calls.
import { reactive } from 'vue';
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { client } from './gen/client.gen';

const AUTHORITY =
  import.meta.env.VITE_OIDC_AUTHORITY || 'http://keycloak.localhost:8080/realms/ebz-staff';
const CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID || 'mdm-cockpit';

const userManager = new UserManager({
  authority: AUTHORITY,
  client_id: CLIENT_ID,
  redirect_uri: window.location.origin + '/auth/callback',
  post_logout_redirect_uri: window.location.origin + '/',
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: true, // Renew via Refresh-Token (Keycloak Public-Client) – kein Iframe
  monitorSession: false, // keine Check-Session-Iframes (3rd-party-Cookie-frei)
  userStore: new WebStorageStateStore({ store: window.localStorage }),
});

export const auth = reactive({
  bereit: false,
  angemeldet: false,
  benutzer: '' as string,
});

let aktuell: User | null = null;

function uebernehme(u: User | null): void {
  aktuell = u && !u.expired ? u : null;
  auth.angemeldet = aktuell != null;
  auth.benutzer = (aktuell?.profile.preferred_username as string) ?? '';
}

userManager.events.addUserLoaded((u) => uebernehme(u));
userManager.events.addUserUnloaded(() => uebernehme(null));
userManager.events.addAccessTokenExpired(() => {
  userManager.signinSilent().then(uebernehme).catch(() => uebernehme(null));
});

/**
 * Bootstrap vor dem Mounten: verarbeitet den OIDC-Redirect-Callback ({@code /auth/callback}) bzw.
 * lädt eine bestehende Session. Nach dem Callback Rücksprung zur ursprünglichen Route (state.returnTo).
 */
export async function authInit(): Promise<void> {
  try {
    if (window.location.pathname === '/auth/callback') {
      const u = await userManager.signinRedirectCallback();
      uebernehme(u);
      const returnTo = (u.state as { returnTo?: string } | undefined)?.returnTo || '/';
      window.history.replaceState(null, '', returnTo);
    } else {
      uebernehme(await userManager.getUser());
    }
  } catch {
    uebernehme(null);
    if (window.location.pathname === '/auth/callback') {
      window.history.replaceState(null, '', '/');
    }
  } finally {
    auth.bereit = true;
  }
}

export function login(): void {
  userManager.signinRedirect({ state: { returnTo: window.location.pathname } });
}

export function logout(): void {
  uebernehme(null);
  userManager.signoutRedirect();
}

// Token an jeden API-Call hängen (sofern Session da); abgelaufenes access_token vorher headless erneuern.
client.interceptors.request.use(async (request) => {
  if (aktuell && aktuell.expired) {
    try {
      uebernehme(await userManager.signinSilent());
    } catch {
      uebernehme(null);
    }
  }
  if (aktuell?.access_token) {
    request.headers.set('Authorization', `Bearer ${aktuell.access_token}`);
  }
  return request;
});
