package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Hält den DATEV-Cloud-Zugriffstoken für den Buchungsdatenservice. DATEV unterstützt nur den
 * Authorization-Code-Flow; ein <b>rotierender Refresh-Token</b> wird per {@code refresh_token}-Grant
 * gegen kurzlebige (opaque) Access-Tokens getauscht — bei jedem Refresh liefert DATEV einen <i>neuen</i>
 * Refresh-Token, den dieser Service als laufenden Stand behält (deshalb taugt der statische
 * {@code quarkus-oidc-client}-Config-Token nicht). Der Access-Token wird bis kurz vor {@code expires_in}
 * gecacht.
 * <p>
 * <b>Showcase-Grenze:</b> Der Stand liegt im Speicher (Seed aus {@code datev.cloud.refresh-token}); ein
 * Neustart liest den Bootstrap-Wert erneut. Für Dauerbetrieb wäre der rotierte Token zu persistieren
 * (DB/Secret) — siehe {@code DATEV-Sandbox-Onboarding.md}.
 */
@ApplicationScoped
public class DatevTokenService {

    private static final Logger LOG = Logger.getLogger(DatevTokenService.class);

    @Inject
    @RestClient
    DatevCloudApi.Token tokenApi;

    @Inject
    DatevKonten konten;

    private volatile String refreshToken;
    private volatile String accessToken;
    private volatile Instant ablauf = Instant.EPOCH;

    @PostConstruct
    void seed() {
        refreshToken = konten.cloud().refreshToken();
    }

    /** Liefert einen gültigen Access-Token (refresht bei Bedarf, mit 60 s Sicherheitsabstand). */
    public synchronized String accessToken() {
        if (accessToken == null || Instant.now().isAfter(ablauf.minusSeconds(60))) {
            refresh();
        }
        return accessToken;
    }

    private void refresh() {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("Kein DATEV-Refresh-Token konfiguriert (datev.cloud.refresh-token / "
                    + "DATEV_REFRESH_TOKEN). Bootstrap: tests/e2e/datev-token-bootstrap.mjs.");
        }
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                (konten.cloud().clientId() + ":" + konten.cloud().clientSecret()).getBytes(StandardCharsets.UTF_8));
        DatevCloudApi.TokenResponse t = tokenApi.refresh(basic, "refresh_token", refreshToken);
        accessToken = t.accessToken();
        if (t.refreshToken() != null && !t.refreshToken().isBlank()) {
            refreshToken = t.refreshToken(); // DATEV rotiert → neuen Stand behalten
        }
        long gueltig = t.expiresIn() > 0 ? t.expiresIn() : 600;
        ablauf = Instant.now().plusSeconds(gueltig);
        LOG.debug("DATEV-Access-Token erneuert (gültig " + gueltig + "s, Refresh rotiert="
                + (t.refreshToken() != null) + ").");
    }
}
