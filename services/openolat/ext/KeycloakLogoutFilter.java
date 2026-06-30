package org.olat.ebz.logout;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * EBZ-Extension: macht aus dem OpenOLAT-Abmelden einen ECHTEN Single-Logout (RP-initiated OIDC).
 *
 * <p>OpenOLATs Keycloak-Provider implementiert KEINEN OIDC-Logout (Quellcode-belegt) und es gibt keine
 * Logout-Redirect-Property — der „Abmelden"-Button beendet nur die OpenOLAT-Session und landet auf
 * {@code /dmz/?logout=true}. Die Keycloak-SSO-Sessions (EBZ + gebrokerter Kunden-IdP) leben weiter, ein
 * erneuter Login liefe still durch → kein frisches IdP-Fenster.
 *
 * <p>Dieser Filter haengt sich VOR den OpenOLAT-Dispatcher auf {@code /dmz/*}: erkennt er den
 * Logout-Landeplatz (Parameter {@code logout}), leitet er einmalig zum Keycloak-{@code end_session}-Endpoint
 * um. Keycloak beendet die EBZ-Session und kaskadiert (per IdP-{@code logoutUrl}) zum externen Kunden-KC,
 * und kehrt via {@code post_logout_redirect_uri} nach {@code /dmz/} zurueck. Dort greift der Filter NICHT
 * mehr (kein {@code logout}-Parameter), OpenOLAT leitet (oauth.*.root=true) frisch zum Keycloak-Login →
 * der Nutzer sieht wieder das IdP-Fenster. Kein Loop, kein Core-Fork; registriert per web.xml-Overlay
 * (metadata-complete=true ⇒ keine Annotation-Registrierung moeglich).
 */
public class KeycloakLogoutFilter implements Filter {

    private String endpoint;   // browser-erreichbare Keycloak-Basis (z. B. http://keycloak.localhost:8080)
    private String realm;      // EBZ-Kundenrealm (ebz-customers)
    private String clientId;   // OpenOLAT-Client in diesem Realm

    @Override
    public void init(FilterConfig cfg) {
        endpoint = firstNonBlank(cfg.getInitParameter("kcEndpoint"), System.getenv("KC_ENDPOINT"),
                "http://keycloak.localhost:8080");
        realm = firstNonBlank(cfg.getInitParameter("kcRealm"), "ebz-customers");
        clientId = firstNonBlank(cfg.getInitParameter("clientId"), "openolat");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) req;
        HttpServletResponse w = (HttpServletResponse) resp;

        if (r.getParameter("logout") != null) {
            int port = r.getServerPort();
            String base = r.getScheme() + "://" + r.getServerName()
                    + (port == 80 || port == 443 ? "" : ":" + port);
            String postLogout = base + "/dmz/";   // ohne logout-Parameter → Filter greift dort nicht erneut
            String url = endpoint + "/realms/" + realm + "/protocol/openid-connect/logout"
                    + "?client_id=" + enc(clientId)
                    + "&post_logout_redirect_uri=" + enc(postLogout);
            w.sendRedirect(url);
            return;
        }
        chain.doFilter(req, resp);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
