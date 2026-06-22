package de.netzfactor.ebz.controlling.integration.hubspot.web;

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.netzfactor.ebz.controlling.integration.hubspot.service.HubSpotWebhookVerarbeitung;

/**
 * <b>Consent-Rückkanal-Endpunkt (HubSpot→MDM).</b> Öffentlich erreichbar (HubSpot ruft serverseitig, kein
 * Keycloak), aber <b>signatur-verifiziert</b> (X-HubSpot-Signature-v3) mit Replay-Schutz. Die Signatur-URI
 * ist die <b>öffentliche</b> Prod-URL aus {@code hubspot.webhook.public-base-url} (nicht die proxy-interne).
 * Per Default aus ({@code hubspot.webhook.enabled=false}); HubSpot-Webhook-Subscriptions + App-Secret werden
 * in der Private App konfiguriert.
 */
@Path(HubSpotWebhookResource.PFAD)
public class HubSpotWebhookResource {

    static final String PFAD = "/hubspot/webhook";

    private static final Logger LOG = Logger.getLogger(HubSpotWebhookResource.class);
    private static final long ZUKUNFTS_TOLERANZ_MS = 60_000;

    @Inject
    HubSpotWebhookVerarbeitung verarbeitung;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "hubspot.webhook.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "hubspot.webhook.app-secret")
    Optional<String> appSecret;

    @ConfigProperty(name = "hubspot.webhook.public-base-url")
    Optional<String> publicBaseUrl;

    @ConfigProperty(name = "hubspot.webhook.toleranz-sekunden", defaultValue = "300")
    long toleranzSekunden;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response empfange(String body,
                             @HeaderParam("X-HubSpot-Signature-v3") String signatur,
                             @HeaderParam("X-HubSpot-Request-Timestamp") String timestamp) {
        if (!enabled) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String secret = appSecret.orElse("");
        if (secret.isBlank()) {
            LOG.error("HubSpot-Webhook aktiviert, aber kein app-secret konfiguriert");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        if (!zeitstempelGueltig(timestamp)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("fehler", "Zeitstempel ungültig/abgelaufen")).build();
        }
        String uri = publicBaseUrl.orElse("") + PFAD;
        if (!HubSpotSignatur.gueltig(secret, "POST", uri, body, timestamp, signatur)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("fehler", "Signatur ungültig")).build();
        }
        try {
            int n = verarbeitung.verarbeite(mapper.readTree(body));
            return Response.ok(Map.of("verarbeitet", n)).build();
        } catch (Exception ex) {
            LOG.warnf("HubSpot-Webhook-Payload nicht verarbeitbar: %s", ex.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    private boolean zeitstempelGueltig(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        try {
            long alterMs = System.currentTimeMillis() - Long.parseLong(timestamp.trim());
            return alterMs >= -ZUKUNFTS_TOLERANZ_MS && alterMs <= toleranzSekunden * 1000;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }
}
