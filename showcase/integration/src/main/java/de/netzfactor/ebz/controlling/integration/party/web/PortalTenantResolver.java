package de.netzfactor.ebz.controlling.integration.party.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String resolve(RoutingContext context) {
        String path = context.normalizedPath();
        if (path.startsWith("/party/portal")
                || path.startsWith("/kommunikation/portal")
                || path.startsWith("/lms/portal")
                || path.equals("/party/personen/login")
                || path.startsWith("/party/firmensicht")) {
            return "customers";
        }
        // Thread-WebSocket ist <b>Cross-Realm</b> (Staff UND Kunde in einer Konversation) → der Tenant
        // kann nicht am Pfad hängen, sondern wird am Issuer des Tokens entschieden (das der
        // RealtimeAuthRouteFilter zuvor aus ?access_token in den Authorization-Header gehoben hat).
        if (path.startsWith("/ws/kommunikation/")) {
            return tenantAusIssuer(context);
        }
        return null; // Default-Tenant (ebz-staff)
    }

    /** {@code customers}, wenn der Bearer-Token aus dem Realm {@code ebz-customers} stammt; sonst Default. */
    private static String tenantAusIssuer(RoutingContext context) {
        String auth = context.request().getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        String iss = issuerOf(auth.substring(7));
        return iss != null && iss.endsWith("ebz-customers") ? "customers" : null;
    }

    /** Liest den {@code iss}-Claim aus dem JWT-Payload (ohne Signaturprüfung — die macht danach OIDC). */
    private static String issuerOf(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readTree(new String(payload, StandardCharsets.UTF_8)).path("iss").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
