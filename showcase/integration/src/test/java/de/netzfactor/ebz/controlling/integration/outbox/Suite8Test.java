package de.netzfactor.ebz.controlling.integration.outbox;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Suite8-Provisionierung (Kiosk/Kantine): belegt, dass mit der Vertragsbestätigung ein Suite8-Auftrag
 * entsteht und der Azubi — über denselben Outbox-Mechanismus wie WebUntis — als Suite8-Konto mit einer
 * <b>Bezahlkarte</b> übernommen wird (idempotent, ohne zweite Karte bei erneutem Dispatch).
 */
@QuarkusTest
@TestSecurity(user = "kc-suite8", roles = "rechnung-pflege")
class Suite8Test {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void vertragsbestaetigung_legtSuite8KontoMitBezahlkarteAn_idempotent() {
        long n = uniq();
        int sjy = 6500 + (int) (n % 400);
        String schuljahr = sjy + "/" + (sjy + 1);
        String apEmail = "suite8-ap+" + n + "@firma.de";
        String azubiEmail = "suite8-azubi+" + n + "@firma.de";

        given().when().post("/mock/suite8/verfuegbarkeit?verfuegbar=true").then().statusCode(200);

        int callerId = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-suite8","email":"%s","anzeigeName":"Susi Suite"}""".formatted(apEmail))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201))).extract().jsonPath().getInt("id");
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Suite8 Bau GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Susi Suite","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(apEmail))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(callerId));

        int anmeldungId = given().contentType(ContentType.JSON)
                .body("""
                        {"organisationId":%d,"azubiEmail":"%s","azubiName":"Sven Azubi",
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgId, azubiEmail, schuljahr))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                .extract().jsonPath().getInt("anmeldungId");
        given().contentType(ContentType.JSON)
                .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .when().post("/party/portal/anmeldungen/" + anmeldungId + "/vertrag-bestaetigen").then()
                .statusCode(200).body("status", equalTo("AKTIV"));

        // Es existiert ein SUITE8-Auftrag für die Anmeldung
        given().when().get("/outbox").then().statusCode(200)
                .body("findAll { it.anmeldungId == %d }.zielsystem".formatted(anmeldungId), hasItem("SUITE8"));

        // Zustellung (manueller Trigger synchron; der 5s-Scheduler kann sie auch schon gezogen haben)
        given().when().post("/outbox/dispatch").then().statusCode(200);

        // Suite8 hat ein Konto mit Bezahlkarte (BK-…) für Kiosk/Kantine angelegt
        given().when().get("/mock/suite8/konten").then().statusCode(200)
                .body("email", hasItem(azubiEmail))
                .body("find { it.email == '%s' }.bezahlkartenNr".formatted(azubiEmail), startsWith("BK-"));

        // Idempotenz: erneuter Dispatch legt keine zweite Karte an
        int vorher = given().when().get("/mock/suite8/konten").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.email == '%s' }".formatted(azubiEmail)).size();
        given().when().post("/outbox/dispatch").then().statusCode(200);
        int nachher = given().when().get("/mock/suite8/konten").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.email == '%s' }".formatted(azubiEmail)).size();
        org.junit.jupiter.api.Assertions.assertEquals(vorher, nachher, "kein Doppel-Konto (idempotent)");
    }
}
