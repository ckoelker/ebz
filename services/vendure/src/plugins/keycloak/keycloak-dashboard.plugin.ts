import { PluginCommonModule, VendurePlugin } from '@vendure/core';

/**
 * Trägt die Dashboard-Login-Extension bei (zentraler Keycloak-Mitarbeiter-Login, Realm `ebz-staff`).
 * Reines Trägerplugin ohne Backend-Logik — die eigentliche Admin-Auth läuft über die in
 * {@link KeycloakAdminAuthStrategy} verdrahtete `adminAuthenticationStrategy`. Der `dashboard`-Pfad
 * wird vom `vendureDashboardPlugin` (Vite) eingesammelt und in den Dashboard-Build aufgenommen.
 */
@VendurePlugin({
    imports: [PluginCommonModule],
    dashboard: './dashboard/index.tsx',
})
export class KeycloakDashboardPlugin {}
