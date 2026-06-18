import {
    AuthenticationStrategy,
    ExternalAuthenticationService,
    Injector,
    RequestContext,
    Role,
    TransactionalConnection,
    User,
} from '@vendure/core';
import { DocumentNode } from 'graphql';
import gql from 'graphql-tag';
import { KeycloakStrategyOptions, KeycloakAuthData } from './keycloak-shop.strategy';
import { makeOidcVerifier, VerifiedIdentity } from './oidc-verifier';

const ROLLE_PFLEGE = 'katalog-pflege';
const ROLLE_LESEN = 'katalog-lesen';
// Vom SSO verwaltete Katalog-Rollen (werden bei jedem Login mit Keycloak synchronisiert;
// fremde Rollen am Nutzer bleiben unberührt). `sso-staff` = Altbestand → mit migrieren.
const VERWALTETE_ROLLEN = [ROLLE_PFLEGE, ROLLE_LESEN, 'sso-staff'];

/**
 * Admin-/Staff-SSO über Keycloak-Realm `ebz-staff` (strikt vom Kunden-Realm getrennt).
 * Mappt die **Keycloak-Realm-Rolle/Gruppe** des Nutzers auf eine globale Vendure-Rolle:
 * `katalog-pflege` (Create/Update/Delete) bzw. sonst `katalog-lesen` (nur Read*).
 * Die Zuordnung wird bei **jedem Login** synchronisiert, sodass Änderungen in Keycloak
 * sofort wirken. (Keine Channel-Skopierung — bewusst globale Rollen.)
 */
export class KeycloakAdminAuthStrategy implements AuthenticationStrategy<KeycloakAuthData> {
    readonly name = 'keycloak';
    private externalAuthService: ExternalAuthenticationService;
    private connection: TransactionalConnection;
    private verify: (token: string) => Promise<VerifiedIdentity>;

    constructor(private options: KeycloakStrategyOptions) {}

    init(injector: Injector) {
        this.externalAuthService = injector.get(ExternalAuthenticationService);
        this.connection = injector.get(TransactionalConnection);
        this.verify = makeOidcVerifier(this.options.jwksUri, this.options.issuer);
    }

    defineInputType(): DocumentNode {
        return gql`
            input KeycloakAuthInput {
                token: String!
            }
        `;
    }

    async authenticate(ctx: RequestContext, data: KeycloakAuthData): Promise<User | false | string> {
        let id: VerifiedIdentity;
        try {
            id = await this.verify(data.token);
        } catch (e: any) {
            return 'Keycloak-Token ungültig: ' + (e?.message ?? 'unbekannt');
        }

        // Keycloak-Rolle → globale Vendure-Rolle (Pflege schlägt Lesen).
        const ziel = id.roles.includes(ROLLE_PFLEGE) ? ROLLE_PFLEGE : ROLLE_LESEN;
        const role = await this.connection.getRepository(ctx, Role).findOne({ where: { code: ziel } });
        if (!role) {
            return `Vendure-Rolle '${ziel}' fehlt — bitte Shop initialisieren (POST /shop/init am Integrationsbackend)`;
        }

        const existing = await this.externalAuthService.findAdministratorUser(ctx, this.name, id.sub);
        if (existing) {
            await this.syncRollen(ctx, existing.id, role);
            return existing;
        }

        return this.externalAuthService.createAdministratorAndUser(ctx, {
            strategy: this.name,
            externalIdentifier: id.sub,
            identifier: id.email ?? id.username ?? id.sub,
            emailAddress: id.email,
            firstName: id.firstName ?? '',
            lastName: id.lastName ?? id.username ?? 'Mitarbeiter',
            roles: [role],
        });
    }

    /** Hält die SSO-verwalteten Katalog-Rollen des Nutzers mit Keycloak im Gleichstand. */
    private async syncRollen(ctx: RequestContext, userId: string | number, ziel: Role): Promise<void> {
        const repo = this.connection.getRepository(ctx, User);
        const user = await repo.findOne({ where: { id: userId as any }, relations: { roles: true } });
        if (!user) return;
        const behalten = user.roles.filter(r => !VERWALTETE_ROLLEN.includes(r.code));
        const neu = [...behalten, ziel];
        const vorher = new Set(user.roles.map(r => r.id));
        const nachher = new Set(neu.map(r => r.id));
        const gleich = vorher.size === nachher.size && [...vorher].every(idv => nachher.has(idv));
        if (gleich) return;
        user.roles = neu;
        await repo.save(user, { reload: false });
    }
}
