package de.netzfactor.ebz.controlling.integration.hubspot.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;

/**
 * <b>Consent-Rückkanal (HubSpot→MDM).</b> Verarbeitet signatur-verifizierte HubSpot-Webhook-Events und
 * spiegelt sie in den maßgeblichen MDM-Kern zurück:
 * <ul>
 *   <li>Unsubscribe/Opt-out ({@code contact.propertyChange} auf {@code hs_email_optout}) → die EMAIL/
 *       NEWSLETTER-Einwilligung der gemappten Person wird {@code WIDERRUFEN} (Nachweis: Quelle HubSpot).</li>
 *   <li>{@code contact.deletion} → das {@code ExterneId}-Mapping wird entfernt.</li>
 *   <li>{@code contact.merge} → das Mapping wird auf die überlebende HubSpot-ID umgebogen.</li>
 * </ul>
 * <b>Echo-Unterdrückung:</b> der Rückkanal aktualisiert <i>nur</i> den MDM-Zustand und reiht <b>keinen</b>
 * Outbound-Auftrag ein → kein Ping-Pong. <b>Idempotenz</b> über die HubSpot-{@code eventId} (Dedupe-Set).
 */
@ApplicationScoped
public class HubSpotWebhookVerarbeitung {

    private static final Logger LOG = Logger.getLogger(HubSpotWebhookVerarbeitung.class);

    /** Bereits verarbeitete Event-IDs (Showcase: In-Memory-Dedupe; in Prod eine kleine Tabelle). */
    private final Set<Long> verarbeiteteEvents = ConcurrentHashMap.newKeySet();

    /** Verarbeitet ein HubSpot-Event-Array; liefert die Anzahl wirksam verarbeiteter Events. */
    public int verarbeite(JsonNode events) {
        if (events == null || !events.isArray()) {
            return 0;
        }
        int wirksam = 0;
        for (JsonNode e : events) {
            long eventId = e.path("eventId").asLong();
            if (eventId != 0 && !verarbeiteteEvents.add(eventId)) {
                continue; // schon verarbeitet → idempotent
            }
            if (verarbeiteEines(e)) {
                wirksam++;
            }
        }
        return wirksam;
    }

    private boolean verarbeiteEines(JsonNode e) {
        String typ = e.path("subscriptionType").asText("");
        long objectId = e.path("objectId").asLong();
        long occurredAt = e.path("occurredAt").asLong();
        return switch (typ) {
            case "contact.propertyChange" -> {
                String prop = e.path("propertyName").asText("");
                String wert = e.path("propertyValue").asText("");
                boolean optOut = "hs_email_optout".equals(prop) && istWahr(wert);
                yield optOut && widerrufe(objectId, occurredAt);
            }
            case "contact.deletion" -> mappingEntfernen(objectId);
            case "contact.merge" -> mappingUmbiegen(e);
            default -> false;
        };
    }

    /** Setzt erteilte EMAIL/NEWSLETTER-Einwilligungen der gemappten Person auf WIDERRUFEN (kein Outbound!). */
    @Transactional
    public boolean widerrufe(long hubspotContactId, long occurredAtMillis) {
        ExterneId map = kontaktMapping(hubspotContactId);
        if (map == null || map.person == null) {
            LOG.warnf("Webhook-Unsubscribe für unbekannte HubSpot-Contact-ID %d ignoriert", hubspotContactId);
            return false;
        }
        LocalDateTime stand = occurredAtMillis > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(occurredAtMillis), ZoneId.of("Europe/Berlin"))
                : LocalDateTime.now();
        long geaendert = Einwilligung.update(
                "status = ?1, widerrufenAm = ?2 where person.id = ?3 and kanal = ?4 and zweck = ?5 and status = ?6",
                Einwilligung.Status.WIDERRUFEN, stand, map.person.id,
                Einwilligung.Kanal.EMAIL, Einwilligung.Zweck.NEWSLETTER, Einwilligung.Status.ERTEILT);
        LOG.infof("Webhook-Unsubscribe: %d Einwilligung(en) der Person %d widerrufen (HubSpot %d)",
                geaendert, map.person.id, hubspotContactId);
        return true;
    }

    @Transactional
    public boolean mappingEntfernen(long hubspotContactId) {
        ExterneId map = kontaktMapping(hubspotContactId);
        if (map == null) {
            return false;
        }
        map.delete();
        return true;
    }

    @Transactional
    public boolean mappingUmbiegen(JsonNode e) {
        long primary = e.path("primaryObjectId").asLong();
        JsonNode merged = e.path("mergedObjectIds");
        if (primary == 0 || !merged.isArray()) {
            return false;
        }
        boolean wirksam = false;
        for (JsonNode m : merged) {
            ExterneId map = kontaktMapping(m.asLong());
            if (map != null) {
                map.externeId = String.valueOf(primary);
                wirksam = true;
            }
        }
        return wirksam;
    }

    private static ExterneId kontaktMapping(long hubspotContactId) {
        return ExterneId.find("quelle = ?1 and externeId = ?2",
                Quelle.HUBSPOT, String.valueOf(hubspotContactId)).firstResult();
    }

    private static boolean istWahr(String wert) {
        return "true".equalsIgnoreCase(wert) || "1".equals(wert) || "yes".equalsIgnoreCase(wert);
    }
}
