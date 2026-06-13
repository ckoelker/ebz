// SSO gegen Keycloak (Realm ebz-staff, Public-Client mdm-cockpit, PKCE) — P1.2 RBAC.
// Authority = keycloak.localhost:8080 (Browser löst *.localhost→127.0.0.1; derselbe Issuer, den
// der Quarkus-Service validiert). Liste (GET) bleibt anonym; Schreiben braucht die Rolle
// katalog-pflege → der Request-Interceptor hängt das Bearer-Token an alle API-Calls.
import { reactive } from 'vue';
import Keycloak from 'keycloak-js';
import { client } from './gen/client.gen';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://keycloak.localhost:8080',
  realm: 'ebz-staff',
  clientId: 'mdm-cockpit',
});

export const auth = reactive({
  bereit: false,
  angemeldet: false,
  benutzer: '' as string,
});

export async function authInit(): Promise<void> {
  try {
    const ok = await keycloak.init({ pkceMethod: 'S256', checkLoginIframe: false });
    auth.angemeldet = ok;
    auth.benutzer = (keycloak.tokenParsed?.preferred_username as string) ?? '';
  } catch {
    auth.angemeldet = false;
  } finally {
    auth.bereit = true;
  }
}

export function login(): void {
  keycloak.login({ redirectUri: window.location.origin + '/' });
}

export function logout(): void {
  keycloak.logout({ redirectUri: window.location.origin + '/' });
}

// Token an jeden API-Call hängen (sofern angemeldet); vor Ablauf erneuern.
client.interceptors.request.use(async (request) => {
  if (keycloak.authenticated) {
    try {
      await keycloak.updateToken(30);
    } catch {
      /* abgelaufen → anonym weiter; Schreib-Calls liefern dann 401 → Login */
    }
    if (keycloak.token) request.headers.set('Authorization', `Bearer ${keycloak.token}`);
  }
  return request;
});
