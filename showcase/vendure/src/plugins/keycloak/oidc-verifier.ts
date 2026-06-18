import { createRemoteJWKSet, jwtVerify } from 'jose';

export interface VerifiedIdentity {
    sub: string;
    email?: string;
    firstName?: string;
    lastName?: string;
    username?: string;
    /** Keycloak-Realm-Rollen (realm_access.roles) + ggf. Gruppen (groups, ohne führenden „/"). */
    roles: string[];
}

/**
 * Baut einen Verifier, der ein Keycloak-Access-Token gegen die JWKS des Realms
 * prüft. Der `issuer` (öffentliche URL im Token-Claim) wird validiert; die JWKS
 * werden über `jwksUri` geladen — die darf eine interne Container-URL sein
 * (Split-Horizon: Issuer = localhost:8088, JWKS = http://keycloak:8080/...).
 */
export function makeOidcVerifier(jwksUri: string, issuer: string) {
    const JWKS = createRemoteJWKSet(new URL(jwksUri));
    return async function verify(token: string): Promise<VerifiedIdentity> {
        const { payload } = await jwtVerify(token, JWKS, { issuer });
        const realmAccess = payload.realm_access as { roles?: unknown } | undefined;
        const realmRoles = Array.isArray(realmAccess?.roles) ? realmAccess!.roles.map(String) : [];
        const groups = Array.isArray(payload.groups) ? payload.groups.map((g) => String(g).replace(/^\//, '')) : [];
        return {
            sub: String(payload.sub),
            email: typeof payload.email === 'string' ? payload.email : undefined,
            firstName: typeof payload.given_name === 'string' ? payload.given_name : undefined,
            lastName: typeof payload.family_name === 'string' ? payload.family_name : undefined,
            username: typeof payload.preferred_username === 'string' ? payload.preferred_username : undefined,
            roles: [...new Set([...realmRoles, ...groups])],
        };
    };
}
