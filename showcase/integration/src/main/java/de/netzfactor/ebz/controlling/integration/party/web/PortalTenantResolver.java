package de.netzfactor.ebz.controlling.integration.party.web;

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Wählt den OIDC-Tenant pro Request: die Außenportal-Endpunkte (Realm {@code ebz-customers}) gegen
 * den Tenant {@code customers}, alles Übrige (Staff/RBAC, Realm {@code ebz-staff}) gegen den
 * Default-Tenant.
 *
 * <p>Pfad-basiert, weil das {@code @Tenant}-Annotationsverfahren bei <b>proaktiver</b>
 * Authentifizierung (Quarkus-Default) nicht greift — der Tenant wird vor der JAX-RS-Schicht
 * aufgelöst. Kundenseitig sind das die {@link RechnungPortalResource}/{@link PortalResource}
 * ({@code /party/portal/**}) sowie in {@link PartyResource} der Self-Login {@code /party/personen/login}
 * und die Firmensicht {@code /party/firmensicht/**}.
 */
@ApplicationScoped
public class PortalTenantResolver implements TenantResolver {

    @Override
    public String resolve(RoutingContext context) {
        String path = context.normalizedPath();
        if (path.startsWith("/party/portal")
                || path.startsWith("/lms/portal")
                || path.equals("/party/personen/login")
                || path.startsWith("/party/firmensicht")) {
            return "customers";
        }
        return null; // Default-Tenant (ebz-staff)
    }
}
