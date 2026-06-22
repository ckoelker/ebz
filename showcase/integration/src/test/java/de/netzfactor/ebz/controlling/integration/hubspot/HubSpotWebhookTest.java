package de.netzfactor.ebz.controlling.integration.hubspot;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.web.HubSpotSignatur;
import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * H3 — Consent-Rückkanal (HubSpot→MDM). Prüft Signatur-Verifikation, Wirkung (Unsubscribe → Widerruf),
 * Echo-Unterdrückung (kein Outbound-Auftrag) und Idempotenz über die eventId.
 */
@QuarkusTest
@TestProfile(HubSpotWebhookTest.WebhookAn.class)
class HubSpotWebhookTest {

    static final String SECRET = "test-secret-123";
    static final String BASE_URL = "https://hooks.ebz.test";
    static final String PFAD = "/hubspot/webhook";

    public static class WebhookAn implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "hubspot.webhook.enabled", "true",
                    "hubspot.webhook.app-secret", SECRET,
                    "hubspot.webhook.public-base-url", BASE_URL);
        }
    }

    @BeforeEach
    void reset() {
        QuarkusTransaction.requiringNew().run(() -> {
            HubSpotSyncAuftrag.deleteAll();
            ExterneId.delete("quelle", Quelle.HUBSPOT);
        });
    }

    @Test
    void gueltige_signatur_unsubscribe_widerruft_und_loest_kein_outbound_aus() {
        long hsId = eindeutigeId();
        Long pid = personMitMapping(hsId, "rueckkanal@example.test");
        String body = optOutEvent(eindeutigeId(), hsId);
        String ts = String.valueOf(System.currentTimeMillis());
        String sig = HubSpotSignatur.berechne(SECRET, "POST", BASE_URL + PFAD, body, ts);

        given().header("X-HubSpot-Signature-v3", sig)
                .header("X-HubSpot-Request-Timestamp", ts)
                .contentType("application/json")
                .body(body)
                .when().post(PFAD)
                .then().statusCode(200).body("verarbeitet", is(1));

        assertEquals(Einwilligung.Status.WIDERRUFEN, einwilligungStatus(pid));
        // Echo-Unterdrückung: der Rückkanal reiht keinen Outbound-Auftrag ein.
        assertEquals(0, offeneAuftraege());
    }

    @Test
    void ungueltige_signatur_wird_abgewiesen_ohne_wirkung() {
        long hsId = eindeutigeId();
        Long pid = personMitMapping(hsId, "boese@example.test");
        String body = optOutEvent(eindeutigeId(), hsId);
        String ts = String.valueOf(System.currentTimeMillis());

        given().header("X-HubSpot-Signature-v3", "falsche-signatur")
                .header("X-HubSpot-Request-Timestamp", ts)
                .contentType("application/json")
                .body(body)
                .when().post(PFAD)
                .then().statusCode(401);

        assertEquals(Einwilligung.Status.ERTEILT, einwilligungStatus(pid));
    }

    @Test
    void doppeltes_event_ist_idempotent() {
        long hsId = eindeutigeId();
        Long pid = personMitMapping(hsId, "doppelt@example.test");
        long eventId = eindeutigeId();
        String body = optOutEvent(eventId, hsId);
        String ts = String.valueOf(System.currentTimeMillis());
        String sig = HubSpotSignatur.berechne(SECRET, "POST", BASE_URL + PFAD, body, ts);

        // Erster Aufruf: wirksam.
        given().header("X-HubSpot-Signature-v3", sig).header("X-HubSpot-Request-Timestamp", ts)
                .contentType("application/json").body(body)
                .when().post(PFAD).then().statusCode(200).body("verarbeitet", is(1));
        // Zweiter Aufruf mit gleicher eventId: idempotent (nicht erneut verarbeitet).
        given().header("X-HubSpot-Signature-v3", sig).header("X-HubSpot-Request-Timestamp", ts)
                .contentType("application/json").body(body)
                .when().post(PFAD).then().statusCode(200).body("verarbeitet", is(0));

        assertEquals(Einwilligung.Status.WIDERRUFEN, einwilligungStatus(pid));
    }

    // ───────────────────────── Helfer ─────────────────────────

    private static String optOutEvent(long eventId, long objectId) {
        return "[{\"eventId\":" + eventId + ",\"subscriptionType\":\"contact.propertyChange\","
                + "\"objectId\":" + objectId + ",\"propertyName\":\"hs_email_optout\","
                + "\"propertyValue\":\"true\",\"occurredAt\":" + System.currentTimeMillis() + "}]";
    }

    private static long eindeutigeId() {
        return Math.abs(System.nanoTime() % 1_000_000_000L) + 1;
    }

    private Einwilligung.Status einwilligungStatus(Long personId) {
        return QuarkusTransaction.requiringNew().call(() ->
                ((Einwilligung) Einwilligung.find("person.id", personId).firstResult()).status);
    }

    private long offeneAuftraege() {
        return QuarkusTransaction.requiringNew().call(() -> HubSpotSyncAuftrag.count());
    }

    /** Person + erteilte Einwilligung + HubSpot-Mapping mit der gegebenen HubSpot-Contact-ID. */
    private Long personMitMapping(long hubspotId, String email) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Person p = new Person();
            p.vorname = "Test";
            p.nachname = "Person";
            p.status = Person.Status.AKTIV;
            p.matchSchluessel = "hs-test-" + System.nanoTime();
            p.persist();
            Kontaktpunkt k = new Kontaktpunkt();
            k.typ = Kontaktpunkt.Typ.EMAIL;
            k.email = email;
            k.primaer = true;
            k.person = p;
            k.persist();
            Einwilligung e = new Einwilligung();
            e.person = p;
            e.kanal = Einwilligung.Kanal.EMAIL;
            e.zweck = Einwilligung.Zweck.NEWSLETTER;
            e.status = Einwilligung.Status.ERTEILT;
            e.persist();
            ExterneId map = new ExterneId();
            map.quelle = Quelle.HUBSPOT;
            map.externeId = String.valueOf(hubspotId);
            map.person = p;
            map.persist();
            return p.id;
        });
    }
}
