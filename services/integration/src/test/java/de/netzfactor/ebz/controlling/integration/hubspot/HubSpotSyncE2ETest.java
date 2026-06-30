package de.netzfactor.ebz.controlling.integration.hubspot;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.web.HubSpotSignatur;
import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * <b>End-to-End-Durchstich „Marketing-Sync (HubSpot)" als Living-Documentation-Quelle.</b>
 * <p>
 * Fährt den Outbound-Sync gegen die {@code HubSpotMockSenke} (Default ohne echtes HubSpot) und den
 * signatur-verifizierten Consent-Rückkanal: Sync vormerken → Kontakt/Firma übertragen → (Kunde widerruft
 * in HubSpot →) Rückkanal-Widerruf → Einwilligung spiegeln → Recht auf Vergessen. Jeder Request trägt die
 * Fall-Korrelation über den {@code baggage}-Header; die {@code Prozessspur}-Spans (Verfahren
 * {@code HUBSPOT_SYNC}) gehen über den {@code SpanLogExporter} nach {@code spans.jsonl} → BPMN.
 * <p>
 * Der Webhook ist nur mit eingeschaltetem {@code hubspot.webhook.*} aktiv → eigenes {@link WebhookAn}-Profil
 * (analog {@code HubSpotWebhookTest}).
 */
@QuarkusTest
@TestProfile(WebhookAnProfile.class)
class HubSpotSyncE2ETest {

    // Gemeinsames Profil mit HubSpotWebhookTest → ein geteilter Quarkus-Boot (siehe WebhookAnProfile).
    static final String SECRET = WebhookAnProfile.SECRET;
    static final String BASE_URL = WebhookAnProfile.BASE_URL;
    static final String PFAD = "/hubspot/webhook";

    @BeforeEach
    void reset() {
        QuarkusTransaction.requiringNew().run(() -> {
            HubSpotSyncAuftrag.deleteAll();
            ExterneId.delete("quelle", Quelle.HUBSPOT);
        });
    }

    private static RequestSpecification gegeben(String fall) {
        return given().contentType(ContentType.JSON).header("baggage", "prozess.fall=" + fall);
    }

    @Test
    @TestSecurity(user = "sync-e2e", roles = "katalog-pflege")
    void outbound_und_rueckkanal() {
        String fall = "marketing-sync";
        long n = System.nanoTime();
        long hsId = Math.abs(n % 1_000_000_000L) + 1;

        // Setup OHNE Baggage: marketingfähige Person (Opt-in) + aktive Organisation.
        long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            Organisation o = new Organisation();
            o.name = "E2E Sync GmbH " + n;
            o.status = Organisation.Status.AKTIV;
            o.persist();
            Person p = new Person();
            p.vorname = "Sven";
            p.nachname = "Synchron";
            p.status = Person.Status.AKTIV;
            p.matchSchluessel = "hs-e2e-" + n;
            p.persist();
            Kontaktpunkt k = new Kontaktpunkt();
            k.typ = Kontaktpunkt.Typ.EMAIL;
            k.email = "sync-e2e+" + n + "@example.test";
            k.primaer = true;
            k.person = p;
            k.persist();
            Einwilligung e = new Einwilligung();
            e.person = p;
            e.kanal = Einwilligung.Kanal.EMAIL;
            e.zweck = Einwilligung.Zweck.NEWSLETTER;
            e.status = Einwilligung.Status.ERTEILT;
            e.persist();
            return new long[] { p.id, o.id };
        });
        long pid = ids[0];
        long oid = ids[1];

        // 1) Kontakt + Firma zum Sync vormerken (Outbox)
        gegeben(fall).when().post("/hubspot/sync/contacts/" + pid).then().statusCode(200);
        gegeben(fall).when().post("/hubspot/sync/companies/" + oid).then().statusCode(200);

        // 2) Dispatcher übertragen → Kontakt + Firma in HubSpot, Mapping (ExterneId) gesetzt.
        gegeben(fall).when().post("/hubspot/sync/run").then().statusCode(200);
        // Die Mock-Senke vergibt synthetische IDs ("mock-contact-1"); der Webhook adressiert per numerischer
        // HubSpot-Contact-ID → das Mapping auf die (reale, numerische) hsId normalisieren.
        long hubspotContactId = hsId;
        QuarkusTransaction.requiringNew().run(() -> ExterneId.update(
                "externeId = ?1 where quelle = ?2 and person.id = ?3",
                String.valueOf(hsId), Quelle.HUBSPOT, pid));

        // 3) Consent-Rückkanal: Kunde trägt in HubSpot einen Unsubscribe ein (signatur-verifiziert) → Widerruf
        String body = "[{\"eventId\":" + hsId + ",\"subscriptionType\":\"contact.propertyChange\","
                + "\"objectId\":" + hubspotContactId + ",\"propertyName\":\"hs_email_optout\","
                + "\"propertyValue\":\"true\",\"occurredAt\":" + System.currentTimeMillis() + "}]";
        String ts = String.valueOf(System.currentTimeMillis());
        String sig = HubSpotSignatur.berechne(SECRET, "POST", BASE_URL + PFAD, body, ts);
        given().header("X-HubSpot-Signature-v3", sig)
                .header("X-HubSpot-Request-Timestamp", ts)
                .header("baggage", "prozess.fall=" + fall)
                .contentType("application/json").body(body)
                .when().post(PFAD).then().statusCode(200).body("verarbeitet", is(1));

        // 4) Den (lokal maßgeblichen) Widerruf zurück nach HubSpot spiegeln (Marketing aus)
        gegeben(fall).when().post("/hubspot/sync/consent/" + pid).then().statusCode(200);
        gegeben(fall).when().post("/hubspot/sync/run").then().statusCode(200);

        // 5) Recht auf Vergessen (Art. 17): Person wird im MDM anonymisiert (Lösch-Lebenszyklus) → das
        // Consent-Gate urteilt ERASE; der Dispatcher löscht permanent in HubSpot + entfernt das Mapping.
        QuarkusTransaction.requiringNew().run(() ->
                Person.update("loeschStatus = ?1 where id = ?2", Person.LoeschStatus.ANONYMISIERT, pid));
        gegeben(fall).when().post("/hubspot/sync/erasure/" + pid).then().statusCode(200);
        gegeben(fall).when().post("/hubspot/sync/run").then().statusCode(200);
    }
}
