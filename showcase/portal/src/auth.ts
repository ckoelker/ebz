// SSO gegen Keycloak (Realm ebz-customers, Public-Client ebz-portal, PKCE). Das Außenportal ist
// für Firmen-Ansprechpartner & Teilnehmer. Die öffentliche Ausbildungsbetrieb-Anfrage läuft OHNE
// Login; alles Weitere (Azubis anmelden, Vertrag bestätigen) verlangt einen Login. Der Request-
// Interceptor hängt das Bearer-Token an alle API-Calls.
import { reactive } from 'vue';
import Keycloak from 'keycloak-js';
import { client } from './gen/client.gen';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://keycloak.localhost:8080',
  realm: 'ebz-customers',
  clientId: 'ebz-portal',
});

export const auth = reactive({
  bereit: false,
  angemeldet: false,
  benutzer: '' as string,
  email: '' as string,
  name: '' as string,
});

export async function authInit(): Promise<void> {
  try {
    const ok = await keycloak.init({ pkceMethod: 'S256', checkLoginIframe: false });
    auth.angemeldet = ok;
    auth.benutzer = (keycloak.tokenParsed?.preferred_username as string) ?? '';
    auth.email = (keycloak.tokenParsed?.email as string) ?? '';
    auth.name = (keycloak.tokenParsed?.name as string) ?? auth.benutzer;
  } catch {
    auth.angemeldet = false;
  } finally {
    auth.bereit = true;
  }
}

export function login(): void {
  keycloak.login({ redirectUri: window.location.origin + '/azubis' });
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
      /* abgelaufen → anonym weiter; geschützte Calls liefern dann 401 → Login */
    }
    if (keycloak.token) request.headers.set('Authorization', `Bearer ${keycloak.token}`);
  }
  return request;
});
