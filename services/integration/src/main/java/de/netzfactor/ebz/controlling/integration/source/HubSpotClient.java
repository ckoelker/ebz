package de.netzfactor.ebz.controlling.integration.source;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Realer HubSpot-Pull (CRM v3 Deals) über camel-http (L4: Pagination via {@code after},
 * Rate-Limit-Backoff). Aktiv nur, wenn {@code ingestion.source=hubspot} und ein Token gesetzt ist;
 * sonst übernimmt die Fixture ({@link DealSource}). Liefert eine zusammengeführte
 * {@code {"results":[...]}}-Struktur — identisch zum Fixture-Format, damit der weitere Pfad gleich ist.
 */
@ApplicationScoped
public class HubSpotClient {

    private static final Logger LOG = Logger.getLogger(HubSpotClient.class);
    private static final String PROPS =
            "dealname,amount,deal_currency_code,dealstage,pipeline,company,description,hs_lastmodifieddate";

    @Inject
    ProducerTemplate producer;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "hubspot.base-url", defaultValue = "https://api.hubapi.com")
    String baseUrl;

    @ConfigProperty(name = "hubspot.token")
    Optional<String> token;

    @ConfigProperty(name = "hubspot.page-size", defaultValue = "100")
    int pageSize;

    public String fetchAllDeals() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode results = root.putArray("results");
        String after = null;
        int page = 0;

        do {
            String json = getWithBackoff(buildUrl(after));
            try {
                JsonNode node = mapper.readTree(json);
                JsonNode pageResults = node.path("results");
                if (pageResults.isArray()) {
                    pageResults.forEach(results::add);
                }
                after = node.path("paging").path("next").path("after").asText(null);
                page++;
            } catch (Exception e) {
                throw new IllegalStateException("HubSpot-Antwort nicht parsebar: " + e.getMessage(), e);
            }
        } while (after != null && !after.isBlank() && page < 100); // Sicherung gegen Endlos-Paging

        LOG.infof("HubSpot real: %d Deals über %d Seite(n) geladen", results.size(), page);
        return root.toString();
    }

    private String buildUrl(String after) {
        // camel-http: throwExceptionOnFailure=false → HTTP-Status selbst auswerten (Backoff bei 429)
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/crm/v3/objects/deals?limit=").append(pageSize)
                .append("&properties=").append(PROPS)
                .append("&throwExceptionOnFailure=false");
        if (after != null && !after.isBlank()) {
            url.append("&after=").append(after);
        }
        return url.toString();
    }

    /** Einfacher Exponential-Backoff bei HTTP 429/5xx (L4). */
    private String getWithBackoff(String url) {
        long waitMs = 1000;
        for (int attempt = 1; attempt <= 5; attempt++) {
            Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token.orElse(""));
            headers.put("Accept", "application/json");
            headers.put("CamelHttpMethod", "GET");

            org.apache.camel.Exchange ex = producer.request(url, e -> {
                e.getIn().setHeaders(headers);
                e.getIn().setBody(null);
            });
            Integer status = ex.getMessage().getHeader("CamelHttpResponseCode", Integer.class);
            String body = ex.getMessage().getBody(String.class);

            if (status != null && status == 200) {
                return body;
            }
            if (status != null && (status == 429 || status >= 500)) {
                LOG.warnf("HubSpot HTTP %d (Versuch %d/5) → Backoff %dms", status, attempt, waitMs);
                sleep(waitMs);
                waitMs *= 2;
                continue;
            }
            throw new IllegalStateException("HubSpot HTTP " + status + ": " + body);
        }
        throw new IllegalStateException("HubSpot nach 5 Versuchen nicht erreichbar (Rate-Limit/Server).");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
