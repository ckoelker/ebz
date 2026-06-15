// SSO über STANDARD-OIDC (Authorization Code + PKCE) via oidc-client-ts — anbieter-neutral, spricht
// jeden OIDC-Provider. Hier konfiguriert für Keycloak (Realm ebz-customers, Public-Client ebz-portal),
// austauschbar per VITE_OIDC_*-Env. Authority = keycloak.localhost:8080 (Browser löst *.localhost→
// 127.0.0.1; derselbe Issuer, den der Quarkus-Service validiert). Der Request-Interceptor hängt das
// access_token an alle API-Calls; Renew läuft headless über den Refresh-Token (kein Iframe).
import { reactive } from 'vue';
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { client } from './gen/client.gen';

const AUTHORITY =
  import.meta.env.VITE_OIDC_AUTHORITY || 'http://keycloak.localhost:8080/realms/ebz-customers';
const CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID || 'ebz-portal';

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
  email: '' as string,
  name: '' as string,
});

let aktuell: User | null = null;

function uebernehme(u: User | null): void {
  aktuell = u && !u.expired ? u : null;
  auth.angemeldet = aktuell != null;
  const p = aktuell?.profile;
  auth.benutzer = (p?.preferred_username as string) ?? '';
  auth.email = (p?.email as string) ?? '';
  auth.name = (p?.name as string) ?? auth.benutzer;
}

userManager.events.addUserLoaded((u) => uebernehme(u));
userManager.events.addUserUnloaded(() => uebernehme(null));
userManager.events.addAccessTokenExpired(() => {
  userManager.signinSilent().then(uebernehme).catch(() => uebernehme(null));
});

/**
 * Bootstrap vor dem Mounten: verarbeitet den OIDC-Redirect-Callback ({@code /auth/callback}) bzw.
 * lädt eine bestehende Session. Nach dem Callback wird zur ursprünglich angeforderten Route
 * zurückgesprungen (über {@code state.returnTo}), damit kein Login-Bounce zurückbleibt.
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
