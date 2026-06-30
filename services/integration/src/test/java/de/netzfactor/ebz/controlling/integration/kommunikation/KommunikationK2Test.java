package de.netzfactor.ebz.controlling.integration.kommunikation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KonversationService;
import de.netzfactor.ebz.controlling.integration.party.model.Aktivitaet;
import de.netzfactor.ebz.controlling.integration.party.model.Mitarbeiter;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K2 — Admin↔Person-Threads (Cross-Realm): Staff (ebz-staff) eröffnet einen Vorgang, die Person
 * (ebz-customers) sieht ihn im Portal, antwortet, und die Staff-Nachricht ist zusätzlich ins CRM-Log
 * ({@code Aktivitaet}) gespiegelt. Fremde Threads sind kontext-skopiert geschützt (403). Eindeutige
 * Schlüssel pro Lauf wegen der persistenten Showcase-DB.
 */
@QuarkusTest
class KommunikationK2Test {

    @Inject
    KonversationService konversationen;

    @Inject
    PartyHoheitService party;

    private Long neuerMitarbeiter(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Mitarbeiter m = new Mitarbeiter();
            m.keycloakSub = "k2-staff-" + System.nanoTime();
            m.anzeigeName = name;
            m.email = "sb@ebz.de";
            m.persist();
            return m.id;
        });
    }

    @Test
    @TestSecurity(user = "k2-person-sub")
    void staffEroeffnet_personSiehtAntwortet_crmGespiegelt() {
        Person p = party.selbstRegistrieren("k2-person-sub", "k2-person@ebz.de", "Petra Person");
        Long mid = neuerMitarbeiter("Sven Sachbearbeiter");

        String betreff = "Rückfrage zu Ihrer Anmeldung " + System.nanoTime();
        Konversation k = konversationen.eroeffneVorgang(mid, p.id, betreff, KontextTyp.ANMELDUNG, 1L,
                "<p>Guten Tag, bitte ergänzen Sie Ihr Geburtsdatum.</p>");

        // Person sieht den Thread (Partner = Mitarbeitername) und hat ihn ungelesen.
        given().when().get("/kommunikation/portal/konversationen").then().statusCode(200)
                .body("betreff", hasItem(betreff))
                .body("find { it.betreff == '%s' }.partner".formatted(betreff), org.hamcrest.Matchers.is("Sven Sachbearbeiter"))
                .body("find { it.betreff == '%s' }.ungelesen".formatted(betreff), org.hamcrest.Matchers.is(true));

        // Eine Nachricht, vom Mitarbeiter.
        given().when().get("/kommunikation/portal/konversationen/" + k.id + "/nachrichten").then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.is(1))
                .body("[0].absenderTyp", org.hamcrest.Matchers.is("MITARBEITER"));

        // Person antwortet → 2 Nachrichten, zweite vom PERSON.
        given().contentType("application/json").body("{\"inhaltHtml\":\"<p>Mein Geburtsdatum ist 1.1.2000.</p>\"}")
                .when().post("/kommunikation/portal/konversationen/" + k.id + "/nachrichten")
                .then().statusCode(200).body("absenderTyp", org.hamcrest.Matchers.is("PERSON"));

        assertEquals(2, konversationen.nachrichten(k.id).size());
        assertEquals(Konversation.TeilnehmerTyp.PERSON, konversationen.letzteNachricht(k.id).absenderTyp);

        // CRM-Spiegel: zur Person existiert mindestens eine ausgehende Aktivitaet (Staff-Nachricht).
        long aktivitaeten = QuarkusTransaction.requiringNew()
                .call(() -> Aktivitaet.count("person.id = ?1", p.id));
        assertTrue(aktivitaeten >= 1, "Staff-Nachricht ins CRM-Log gespiegelt");
    }

    @Test
    @TestSecurity(user = "k2-fremd-sub")
    void fremderThreadIstGeschuetzt() {
        party.selbstRegistrieren("k2-fremd-sub", "k2-fremd@ebz.de", "Frieda Fremd");
        Person ziel = party.selbstRegistrieren("k2-ziel-sub-" + System.nanoTime(),
                "k2-ziel-" + System.nanoTime() + "@ebz.de", "Zora Ziel");
        Long mid = neuerMitarbeiter("Sven Sachbearbeiter");

        Konversation fremd = konversationen.eroeffneVorgang(mid, ziel.id, "Nicht für Frieda " + System.nanoTime(),
                KontextTyp.KEINER, null, "<p>privat</p>");

        // Frieda darf den fremden Thread nicht lesen.
        given().when().get("/kommunikation/portal/konversationen/" + fremd.id + "/nachrichten")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "k2-staff-rest", roles = "crm-pflege")
    void adminRest_eroeffnetUndListet() {
        Person p = party.selbstRegistrieren("k2-admin-ziel-" + System.nanoTime(),
                "k2-admin-ziel-" + System.nanoTime() + "@ebz.de", "Klaus Kunde");
        QuarkusTransaction.requiringNew().run(() -> {
            if (Mitarbeiter.find("keycloakSub", "k2-staff-rest").firstResult() == null) {
                Mitarbeiter m = new Mitarbeiter();
                m.keycloakSub = "k2-staff-rest";
                m.anzeigeName = "Rita REST";
                m.persist();
            }
        });

        String betreff = "Admin-Vorgang " + System.nanoTime();
        String body = "{\"personId\":%d,\"betreff\":\"%s\",\"inhaltHtml\":\"<p>Hallo</p>\"}"
                .formatted(p.id, betreff);
        int konvId = given().contentType("application/json").body(body)
                .when().post("/kommunikation/admin/vorgaenge").then().statusCode(200)
                .body("betreff", org.hamcrest.Matchers.is(betreff))
                .body("partner", org.hamcrest.Matchers.is("Klaus Kunde"))
                .extract().path("id");

        given().when().get("/kommunikation/admin/konversationen").then().statusCode(200)
                .body("betreff", hasItem(betreff));

        // Co-Pilot (HITL): liefert einen KI-gekennzeichneten Antwortvorschlag (ohne LLM-Key: Fallback-Vorlage).
        given().contentType("application/json")
                .when().post("/kommunikation/admin/konversationen/" + konvId + "/entwurf")
                .then().statusCode(200)
                .body("kiGeneriert", org.hamcrest.Matchers.is(true))
                .body("entwurf", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()));
    }
}
