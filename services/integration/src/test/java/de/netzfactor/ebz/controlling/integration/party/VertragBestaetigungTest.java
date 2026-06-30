package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Anmeldung Berufsschule — Schritt F (Vertragsbestätigung der Firma): der End-to-End-Durchstich
 * {@code ANGEFRAGT → BESTAETIGT_EBZ → AKTIV} und die Gegenprobe zu Schritt D — sobald die Firma den
 * Vertrag bestätigt, <b>bucht</b> der bestehende Rechnungslauf die Anmeldung. Plus Schutz:
 * Vertragsbestätigung nur aus {@code BESTAETIGT_EBZ} (sonst 409) und nur durch ein buchungsberechtigtes
 * Mitglied (sonst 403).
 */
@QuarkusTest
@TestSecurity(user = "kc-vertrag", roles = "rechnung-pflege")
class VertragBestaetigungTest {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void firma_bestaetigtVertrag_anmeldungAktiv_undLaufBuchtSie() {
        long n = uniq();
        int sjy = 8900 + (int) (n % 90);
        String schuljahr = sjy + "/" + (sjy + 1);
        String apEmail = "vertrag-ap+" + n + "@firma.de";

        int callerId = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-vertrag","email":"%s","anzeigeName":"Vera Vertrag"}""".formatted(apEmail))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201))).extract().jsonPath().getInt("id");
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Vertrag Bau GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Vera Vertrag","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(apEmail))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(callerId));

        // ANGEFRAGT → BESTAETIGT_EBZ → AKTIV
        int anmeldungId = given().contentType(ContentType.JSON)
                .body("""
                        {"organisationId":%d,"azubiEmail":"vertrag-azubi+%d@firma.de","azubiName":"Vince Azubi",
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgId, n, schuljahr))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                .body("status", equalTo("ANGEFRAGT"))
                .extract().jsonPath().getInt("anmeldungId");
        given().contentType(ContentType.JSON)
                .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(200)
                .body("status", equalTo("BESTAETIGT_EBZ"));
        given().contentType(ContentType.JSON)
                .when().post("/party/portal/anmeldungen/" + anmeldungId + "/vertrag-bestaetigen").then().statusCode(200)
                .body("status", equalTo("AKTIV"))
                .body("teilnehmerName", equalTo("Vince Azubi"));

        // Gegenprobe zu D: jetzt (AKTIV) bucht der Berufsschul-Lauf die Anmeldung
        given().contentType(ContentType.JSON)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        // Zweite Vertragsbestätigung (nicht mehr BESTAETIGT_EBZ) → 409
        given().contentType(ContentType.JSON)
                .when().post("/party/portal/anmeldungen/" + anmeldungId + "/vertrag-bestaetigen").then().statusCode(409);
    }

    @Test
    void vertragsbestaetigung_durchNichtMitglied_istVerboten() {
        long n = uniq();
        String schuljahr = (9200 + (int) (n % 90)) + "/" + (9201 + (int) (n % 90));

        // Aufrufer existiert (sub kc-vertrag), aber ist KEIN Mitglied von orgB
        given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-vertrag","email":"vertrag-nm+%d@firma.de","anzeigeName":"Vera Vertrag"}"""
                        .formatted(n))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)));

        // Fremde Firma orgB mit eigenem buchungsberechtigtem Besteller P2 + Azubi (interne Anlage)
        long orgB = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Fremd Vertrag GmbH %d","plz":"44135","ort":"Dortmund","land":"DE"}""".formatted(n))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        int p2 = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"p2+%d@fremd.de","anzeigeName":"Paul Partner","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(n))
                .when().post("/party/organisationen/" + orgB + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");
        int azubiB = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"azubiB+%d@fremd.de","anzeigeName":"Berta Azubi","rolle":"AZUBI","buchungsberechtigt":false}"""
                        .formatted(n))
                .when().post("/party/organisationen/" + orgB + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");
        int anmeldungB = given().contentType(ContentType.JSON)
                .body("""
                        {"teilnehmerPersonId":%d,"bestellerPersonId":%d,"kontextOrganisationId":%d,
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(azubiB, p2, orgB, schuljahr))
                .when().post("/party/buchungen/berufsschule").then().statusCode(201)
                .extract().jsonPath().getInt("anmeldungId");

        // Aufrufer (kein Mitglied von orgB) versucht die Vertragsbestätigung → 403
        given().contentType(ContentType.JSON)
                .when().post("/party/portal/anmeldungen/" + anmeldungB + "/vertrag-bestaetigen").then().statusCode(403);
    }
}
