import {
    AuthenticationStrategy,
    ExternalAuthenticationService,
    Injector,
    RequestContext,
    User,
} from '@vendure/core';
import { DocumentNode } from 'graphql';
import gql from 'graphql-tag';
import { makeOidcVerifier, VerifiedIdentity } from './oidc-verifier';

export interface KeycloakAuthData {
    token: string;
}

export interface KeycloakStrategyOptions {
    jwksUri: string;
    issuer: string;
}

/**
 * Shop-SSO über Keycloak-Realm `ebz-customers`.
 * Validiert das Access-Token und mappt den Nutzer (find-or-create) auf einen
 * Vendure-Customer. Strikt vom Staff-Realm getrennt (eigener Issuer/JWKS).
 */
export class KeycloakShopAuthStrategy implements AuthenticationStrategy<KeycloakAuthData> {
    readonly name = 'keycloak';
    private externalAuthService: ExternalAuthenticationService;
    private verify: (token: string) => Promise<VerifiedIdentity>;

    constructor(private options: KeycloakStrategyOptions) {}

    init(injector: Injector) {
        this.externalAuthService = injector.get(ExternalAuthenticationService);
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
        if (!id.email) return 'Token enthält keinen E-Mail-Claim';

        const existing = await this.externalAuthService.findCustomerUser(ctx, this.name, id.sub);
        if (existing) return existing;

        return this.externalAuthService.createCustomerAndUser(ctx, {
            strategy: this.name,
            externalIdentifier: id.sub,
            emailAddress: id.email,
            firstName: id.firstName ?? '',
            lastName: id.lastName ?? id.username ?? 'Kunde',
            verified: true,
        });
    }
}
