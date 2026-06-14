package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Anmeldung Berufsschule — Schritt A: <i>Firma + Ansprechpartner erfassen</i> als öffentliche
 * Self-Service-Anfrage, plus Firmen-Dubletten (Kandidaten + HITL-Merge).
 * <ul>
 *   <li><b>Lead:</b> der öffentliche, unauthentifizierte Endpunkt legt eine <i>provisorische</i>
 *       Organisation ({@code ANGEFRAGT}) + buchungsberechtigten Ansprechpartner an — ohne Login.</li>
 *   <li><b>Bot-Schutz:</b> gefüllter Honeypot wird abgelehnt; Rate-Limit je Client-IP greift.</li>
 *   <li><b>Org-Dubletten:</b> gleiche USt-Id ⇒ Kandidat; HITL-Merge führt auf einen Golden-Record.</li>
 * </ul>
 * Läuft gegen die echte controlling-DB → eindeutige Schlüssel je Lauf via {@code nanoTime}.
 */
@QuarkusTest
class AnmeldungBerufsschuleTest {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void anfrage_legtProvisorischeFirmaUndAnsprechpartnerAn_ohneLogin() {
        long n = uniq();
        String email = "ansprech+" + n + "@ausbildung.de";

        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Lehrbau GmbH %d","strasse":"Werkstr. 1","plz":"45657","ort":"Recklinghausen",
                         "land":"DE","ustId":"DE%d","ansprechpartnerEmail":"%s","ansprechpartnerName":"Berta Bilden"}"""
                        .formatted(n, n % 100000000L, email))
                .when().post("/party/anfragen/ausbildungsbetrieb").then()
                .statusCode(201)
                .body("organisationStatus", equalTo("ANGEFRAGT"))
                .body("ansprechpartner.status", equalTo("PROVISORISCH"))
                .body("ansprechpartner.keycloakSub", equalTo(null)) // kein Login bei der Anfrage
                .body("ansprechpartner.emails", hasItem(email.toLowerCase()))
                .body("ansprechpartner.mitgliedschaften[0].rolle", equalTo("AUSBILDER"))
                .body("ansprechpartner.mitgliedschaften[0].buchungsberechtigt", equalTo(true));
    }

    @Test
    void anfrage_mitGefuelltemHoneypot_wirdAbgelehnt() {
        long n = uniq();
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Bot Firma %d","plz":"45657","ort":"Recklinghausen","land":"DE",
                         "ansprechpartnerEmail":"bot+%d@spam.de","ansprechpartnerName":"Bot",
                         "website":"http://spam.example"}""".formatted(n, n))
                .when().post("/party/anfragen/ausbildungsbetrieb").then()
                .statusCode(400);
    }

    @Test
    void anfrage_ueberRateLimit_wirdGedrosselt() {
        long n = uniq();
        String ip = "203.0.113." + (n % 200); // eigene Test-IP → isolierter Limiter-Schlüssel
        // 5 Anfragen sind erlaubt, die 6. wird gedrosselt (429).
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).header("X-Forwarded-For", ip)
                    .body("""
                            {"name":"Massen GmbH %d-%d","plz":"45657","ort":"RE","land":"DE",
                             "ansprechpartnerEmail":"m+%d-%d@x.de","ansprechpartnerName":"M"}"""
                            .formatted(n, i, n, i))
                    .when().post("/party/anfragen/ausbildungsbetrieb").then().statusCode(201);
        }
        given().contentType(ContentType.JSON).header("X-Forwarded-For", ip)
                .body("""
                        {"name":"Massen GmbH %d-x","plz":"45657","ort":"RE","land":"DE",
                         "ansprechpartnerEmail":"m+%d-x@x.de","ansprechpartnerName":"M"}""".formatted(n, n))
                .when().post("/party/anfragen/ausbildungsbetrieb").then().statusCode(429);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void gleicheUstId_ergibtFirmenDublette_dieGemergtWird() {
        long n = uniq();
        String ust = "DE" + (n % 100000000L);

        long ziel = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Doppel Bau GmbH %d","plz":"45657","ort":"Recklinghausen","land":"DE","ustId":"%s"}"""
                        .formatted(n, ust))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        long quell = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Doppel-Bau GesmbH %d","plz":"45657","ort":"Recklinghausen","land":"DE","ustId":"%s"}"""
                        .formatted(n, ust))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");

        // gleiche USt-Id ⇒ matchSchluessel gleich ⇒ Dubletten-Kandidat
        given().when().get("/party/organisationen/" + ziel + "/kandidaten").then().statusCode(200)
                .body("id", hasItem((int) quell));

        // HITL-Merge: Quelle → Ziel
        given().contentType(ContentType.JSON)
                .body("{\"quellId\":%d,\"zielId\":%d}".formatted(quell, ziel))
                .when().post("/party/organisationen/merge").then().statusCode(200)
                .body("id", equalTo((int) ziel));

        // Unterlegene Firma ist ZUSAMMENGEFUEHRT und zeigt auf den Golden-Record
        given().when().get("/party/organisationen/" + quell).then().statusCode(200)
                .body("status", equalTo("ZUSAMMENGEFUEHRT"))
                .body("goldenOrganisationId", equalTo((int) ziel));

        // Kandidaten des Ziels enthalten die zusammengeführte Firma nicht mehr
        given().when().get("/party/organisationen/" + ziel + "/kandidaten").then().statusCode(200)
                .body("id", not(hasItem((int) quell)));
    }

    @Test
    void anfrageEndpunkt_istOeffentlich_keinLoginNoetig() {
        long n = uniq();
        // Ohne @TestSecurity: der Lead-Endpunkt ist absichtlich öffentlich (kein 401).
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Offen GmbH %d","plz":"45657","ort":"RE","land":"DE",
                         "ansprechpartnerEmail":"offen+%d@x.de","ansprechpartnerName":"O"}""".formatted(n, n))
                .when().post("/party/anfragen/ausbildungsbetrieb").then()
                .statusCode(greaterThanOrEqualTo(200))
                .statusCode(equalTo(201));
    }
}
