package de.netzfactor.ebz.controlling.integration.source;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Liefert die rohe Deals-JSON — entweder aus der Fixture (Default, Mock) oder real von HubSpot.
 * Realer Pull nur, wenn {@code ingestion.source=hubspot} UND ein Token gesetzt ist; sonst Fixture
 * (so läuft der Showcase ohne externe Abhängigkeit).
 */
@ApplicationScoped
public class DealSource {

    private static final Logger LOG = Logger.getLogger(DealSource.class);

    @Inject
    HubSpotClient hubSpotClient;

    @ConfigProperty(name = "ingestion.source", defaultValue = "fixture")
    String source;

    @ConfigProperty(name = "ingestion.fixture-path", defaultValue = "fixtures/hubspot-deals.sample.json")
    String fixturePath;

    // Optional: leerer/ungesetzter Wert → empty (SmallRye wertet "" als fehlend).
    @ConfigProperty(name = "hubspot.token")
    Optional<String> token;

    public String fetchDealsJson() {
        boolean real = "hubspot".equalsIgnoreCase(source)
                && token.map(String::trim).filter(t -> !t.isEmpty()).isPresent();
        if (real) {
            LOG.info("Quelle: HubSpot (Real-Modus)");
            return hubSpotClient.fetchAllDeals();
        }
        LOG.infof("Quelle: Fixture-Mock (%s)%s", fixturePath,
                "hubspot".equalsIgnoreCase(source) ? " — kein HUBSPOT_TOKEN gesetzt" : "");
        return readFixture();
    }

    private String readFixture() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fixturePath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture nicht gefunden: " + fixturePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Fixture nicht lesbar: " + e.getMessage(), e);
        }
    }
}
