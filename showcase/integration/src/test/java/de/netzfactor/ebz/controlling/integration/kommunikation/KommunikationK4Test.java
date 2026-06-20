package de.netzfactor.ebz.controlling.integration.kommunikation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KonversationService;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.FaqAgentPort;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K4 — Person↔Person-Direktnachrichten (Community) und der <b>autonome</b> FAQ-/Studienberatungs-Bot
 * (Agent-Use-Case 2). Geprüft: ein Peer-Direkt-Chat erscheint mit der anderen Person als Partner und die
 * Person kann antworten; eine KI-Beratung wird vom Bot <b>selbstständig</b> beantwortet (KI-gekennzeichnet,
 * EU-AI-Act Art. 50; ohne LLM-Key über die Fallback-Auskunft); fremde Threads bleiben kontext-skopiert
 * geschützt (403). Eindeutige Schlüssel pro Lauf wegen der persistenten Showcase-DB.
 */
@QuarkusTest
class KommunikationK4Test {

    @Inject
    KonversationService konversationen;

    @Inject
    PartyHoheitService party;

    private Person neuePerson(String praefix, String name) {
        long n = System.nanoTime();
        return party.selbstRegistrieren(praefix + "-" + n, praefix + "-" + n + "@ebz.de", name);
    }

    @Test
    @TestSecurity(user = "k4-carla-sub")
    void direktChat_partnerIstAndererPerson_undAntwortMoeglich() {
        Person carla = party.selbstRegistrieren("k4-carla-sub", "k4-carla@ebz.de", "Carla Community");
        Person tom = neuePerson("k4-tom", "Tom Teilnehmer");

        // Tom schreibt Carla (Carla = Empfängerin), damit ihr Portal einen Peer-Thread (kein EBZ) zeigt.
        String betreff = "Lerngruppe " + System.nanoTime();
        Konversation k = konversationen.eroeffneDirekt(tom.id, carla.id, betreff, "<p>Hallo Carla!</p>");

        given().when().get("/kommunikation/portal/konversationen").then().statusCode(200)
                .body("betreff", hasItem(betreff))
                .body("find { it.betreff == '%s' }.typ".formatted(betreff), org.hamcrest.Matchers.is("DIREKT"))
                .body("find { it.betreff == '%s' }.partner".formatted(betreff), org.hamcrest.Matchers.is("Tom Teilnehmer"))
                .body("find { it.betreff == '%s' }.ungelesen".formatted(betreff), org.hamcrest.Matchers.is(true));

        // Carla antwortet → 2 Nachrichten, beide vom Typ PERSON.
        given().contentType("application/json").body("{\"inhaltHtml\":\"<p>Gern, machen wir!</p>\"}")
                .when().post("/kommunikation/portal/konversationen/" + k.id + "/nachrichten")
                .then().statusCode(200).body("absenderTyp", org.hamcrest.Matchers.is("PERSON"));

        assertEquals(2, konversationen.nachrichten(k.id).size());
    }

    @Test
    @TestSecurity(user = "k4-frager-sub")
    void faqBot_antwortetAutonom_undKiGekennzeichnet() {
        party.selbstRegistrieren("k4-frager-sub", "k4-frager@ebz.de", "Frieda Fragerin");

        // Beratung starten → der Bot antwortet sofort und autonom (ohne LLM-Key: Fallback-Auskunft).
        int konvId = given().contentType("application/json")
                .body("{\"frage\":\"Welche Weiterbildungen bietet das EBZ an?\"}")
                .when().post("/kommunikation/portal/beratung").then().statusCode(200)
                .body("partner", org.hamcrest.Matchers.is(FaqAgentPort.FAQ_BOT_NAME))
                .extract().path("id");

        // Verlauf: Frage (PERSON) + autonome KI-Antwort (AGENT, kiGeneriert).
        given().when().get("/kommunikation/portal/konversationen/" + konvId + "/nachrichten").then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.is(2))
                .body("[0].absenderTyp", org.hamcrest.Matchers.is("PERSON"))
                .body("[1].absenderTyp", org.hamcrest.Matchers.is("AGENT"))
                .body("[1].kiGeneriert", org.hamcrest.Matchers.is(true))
                .body("[1].absender", org.hamcrest.Matchers.is(FaqAgentPort.FAQ_BOT_NAME))
                .body("[1].inhaltHtml", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()));

        // Folgefrage → der Bot reagiert erneut autonom (4 Nachrichten, letzte vom AGENT).
        given().contentType("application/json").body("{\"inhaltHtml\":\"<p>Und gibt es Abendkurse?</p>\"}")
                .when().post("/kommunikation/portal/konversationen/" + konvId + "/nachrichten").then().statusCode(200);

        assertEquals(4, konversationen.nachrichten((long) konvId).size());
        assertEquals(Konversation.TeilnehmerTyp.AGENT, konversationen.letzteNachricht((long) konvId).absenderTyp);
        assertTrue(konversationen.letzteNachricht((long) konvId).kiGeneriert, "Bot-Antwort ist KI-gekennzeichnet");
    }

    @Test
    @TestSecurity(user = "k4-fremd-sub")
    void fremderDirektThreadIstGeschuetzt() {
        party.selbstRegistrieren("k4-fremd-sub", "k4-fremd@ebz.de", "Frodo Fremd");
        Person x = neuePerson("k4-x", "Xenia X");
        Person y = neuePerson("k4-y", "Yannik Y");

        Konversation fremd = konversationen.eroeffneDirekt(x.id, y.id, "Privat " + System.nanoTime(),
                "<p>nur für euch</p>");

        given().when().get("/kommunikation/portal/konversationen/" + fremd.id + "/nachrichten")
                .then().statusCode(403);
    }
}
