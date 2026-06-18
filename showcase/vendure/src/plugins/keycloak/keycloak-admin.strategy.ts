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

const STAFF_ROLE_CODE = 'sso-staff';

/**
 * Admin-/Staff-SSO über Keycloak-Realm `ebz-staff`.
 * Strikt vom Kunden-Realm getrennt (eigener Issuer/JWKS). Mappt den Nutzer
 * (find-or-create) auf einen Vendure-Administrator mit der read-only
 * Showcase-Rolle `sso-staff` (wird vom Seed angelegt).
 *
 * Sicherheitshinweis (Produktion): hier sollten Keycloak-Rollen/Gruppen-Claims
 * auf konkrete Vendure-Rollen gemappt werden — NICHT pauschal eine feste Rolle.
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

        const existing = await this.externalAuthService.findAdministratorUser(ctx, this.name, id.sub);
        if (existing) return existing;

        // Rolle per Repository lesen (kein Permission-Check im anonymen authenticate-Kontext).
        const role = await this.connection.getRepository(ctx, Role).findOne({ where: { code: STAFF_ROLE_CODE } });
        if (!role) {
            return `Staff-Rolle '${STAFF_ROLE_CODE}' fehlt — bitte Shop initialisieren (POST /shop/init am Integrationsbackend)`;
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
}
