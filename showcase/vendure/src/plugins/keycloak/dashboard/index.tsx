import { useEffect, useState } from 'react';
import { defineDashboardExtension } from '@vendure/dashboard';
import { UserManager, WebStorageStateStore } from 'oidc-client-ts';

/**
 * Dashboard-Login-Extension: zentraler Mitarbeiter-Login via Keycloak (Realm `ebz-staff`).
 *
 * Das Vendure-Dashboard und das MDM-Cockpit bleiben EIGENSTÄNDIGE Apps, aber ein Mitarbeiter meldet
 * sich an beiden mit demselben zentralen ebz-staff-Account an (SSO über denselben Realm). Der Button
 * erscheint unter dem nativen Login-Formular (afterForm); der native Superadmin-Login bleibt erhalten.
 *
 * Flow: OIDC Authorization-Code+PKCE (Public-Client `vendure-dashboard`) → access_token →
 * admin-api `authenticate(input:{ keycloak:{ token }})` (Backend: KeycloakAdminAuthStrategy, Realm
 * ebz-staff → Vendure-Administrator, Rolle `sso-staff`). Das Session-Token kommt im Response-Header
 * `vendure-auth-token` und wird in denselben localStorage-Key geschrieben, den der Dashboard-API-Client
 * liest (`vendure-session-token`) — danach Reload ins Dashboard.
 */

// Issuer-URL MUSS der vom Server validierten entsprechen (KEYCLOAK_ISSUER_STAFF). Browser erreicht
// Keycloak unter :8088; im Dev läuft das Dashboard auf :5173, der Server (admin-api) auf :3000.
const env = (import.meta as any).env ?? {};
const AUTHORITY: string = env.VITE_STAFF_OIDC_AUTHORITY || 'http://localhost:8088/realms/ebz-staff';
const CLIENT_ID: string = env.VITE_STAFF_OIDC_CLIENT_ID || 'vendure-dashboard';
const ADMIN_API: string = env.DEV ? 'http://localhost:3000/admin-api' : window.location.origin + '/admin-api';
const SESSION_TOKEN_KEY = 'vendure-session-token';

function newUserManager(): UserManager {
    return new UserManager({
        authority: AUTHORITY,
        client_id: CLIENT_ID,
        redirect_uri: window.location.origin + '/dashboard/login',
        response_type: 'code',
        scope: 'openid profile email',
        monitorSession: false,
        userStore: new WebStorageStateStore({ store: window.localStorage }),
    });
}

/** Tauscht den Keycloak-Token gegen eine Vendure-Admin-Session; liefert das Session-Token (Header). */
async function authenticateWithVendure(keycloakToken: string): Promise<string> {
    const res = await fetch(ADMIN_API, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
            query: `mutation ($t: String!) {
                authenticate(input: { keycloak: { token: $t } }) {
                    __typename
                    ... on CurrentUser { id identifier }
                    ... on ErrorResult { errorCode message }
                }
            }`,
            variables: { t: keycloakToken },
        }),
    });
    const sessionToken = res.headers.get('vendure-auth-token');
    const body = await res.json().catch(() => null);
    const result = body?.data?.authenticate;
    if (!result || result.__typename !== 'CurrentUser') {
        throw new Error(result?.message || 'Keycloak-Login am Vendure-Server fehlgeschlagen.');
    }
    if (!sessionToken) {
        throw new Error('Kein Session-Token vom Server erhalten (vendure-auth-token-Header fehlt).');
    }
    return sessionToken;
}

function KeycloakLoginButton() {
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Rückkehr vom Keycloak-Redirect (…/dashboard/login?code=…): Callback abschließen + einloggen.
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        if (!params.get('code')) {
            return;
        }
        setBusy(true);
        (async () => {
            const um = newUserManager();
            try {
                const user = await um.signinRedirectCallback();
                const sessionToken = await authenticateWithVendure(user.access_token);
                localStorage.setItem(SESSION_TOKEN_KEY, sessionToken);
                await um.removeUser();
                window.location.assign(window.location.origin + '/dashboard/');
            } catch (e: any) {
                setError(e?.message ?? String(e));
                setBusy(false);
                // URL säubern, damit ein Reload nicht den (verbrauchten) Code erneut nutzt.
                window.history.replaceState(null, '', window.location.origin + '/dashboard/login');
            }
        })();
    }, []);

    const start = () => {
        setError(null);
        newUserManager().signinRedirect().catch(e => setError(String(e)));
    };

    return (
        <div className="mt-4">
            <div className="relative my-4">
                <div className="absolute inset-0 flex items-center"><span className="w-full border-t" /></div>
                <div className="relative flex justify-center text-xs uppercase">
                    <span className="bg-background px-2 text-muted-foreground">oder</span>
                </div>
            </div>
            <button
                type="button"
                onClick={start}
                disabled={busy}
                className="inline-flex w-full items-center justify-center rounded-md border border-input bg-background px-4 py-2 text-sm font-medium shadow-sm hover:bg-accent hover:text-accent-foreground disabled:opacity-50"
            >
                {busy ? 'Anmeldung läuft…' : 'Mit Mitarbeiter-Account anmelden (Keycloak)'}
            </button>
            {error && <p className="mt-2 text-sm text-destructive">{error}</p>}
        </div>
    );
}

export default defineDashboardExtension({
    login: {
        afterForm: { component: KeycloakLoginButton },
    },
});
