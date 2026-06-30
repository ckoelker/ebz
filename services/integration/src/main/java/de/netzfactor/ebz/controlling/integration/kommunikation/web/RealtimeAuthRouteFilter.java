package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

/**
 * Härtung der Echtzeit-Transporte: Browser-{@code EventSource} (SSE) und {@code WebSocket} können
 * <b>keinen</b> {@code Authorization}-Header setzen — sie übergeben das Keycloak-Access-Token deshalb als
 * {@code ?access_token=…}-Query-Parameter. Dieser <b>früh laufende</b> Vert.x-Route-Filter hebt es vor der
 * OIDC-Authentifizierung in den Standard-{@code Authorization: Bearer}-Header, sodass der ganz normale
 * Bearer-Token-Flow (inkl. Tenant-Auflösung über den Issuer, s. {@code PortalTenantResolver}) greift — der
 * Kanal bleibt also voll authentifiziert, ohne ein eigenes Auth-Verfahren zu erfinden.
 *
 * <p>Bewusst eng begrenzt: nur die beiden Realtime-Pfade (Portal-SSE-Feed + Thread-WS) und nur, wenn
 * <i>kein</i> Header vorhanden ist (REST-Clients mit Header bleiben unberührt). Showcase-Trade-off: Token in
 * der URL kann in Logs/History landen → in Produktion kurzlebige Access-Tokens + TLS (bzw. Cookie/
 * Subprotocol); der Mechanismus bleibt gleich.
 */
public class RealtimeAuthRouteFilter {

    /** Höher als die Quarkus-HTTP-Authentifizierung → der Header steht, bevor OIDC den Request prüft. */
    @RouteFilter(400)
    void promoteAccessToken(RoutingContext ctx) {
        String path = ctx.normalizedPath();
        boolean realtime = path.equals("/kommunikation/portal/stream")
                || path.startsWith("/ws/kommunikation/");
        if (realtime && ctx.request().getHeader("Authorization") == null) {
            String token = ctx.request().getParam("access_token");
            if (token != null && !token.isBlank()) {
                ctx.request().headers().set("Authorization", "Bearer " + token);
            }
        }
        ctx.next();
    }
}
