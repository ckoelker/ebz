package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Anmeldung Berufsschule — Schritt D: der eingeloggte Firmen-Ansprechpartner meldet Azubis über das
 * Self-Service-Portal an.
 * <ul>
 *   <li>buchungsberechtigtes Mitglied → Anmeldung im Firmenkontext mit Status <b>ANGEFRAGT</b> auf den
 *       Firmen-Debitor (BS-);</li>
 *   <li>der bestehende Rechnungslauf bucht <b>ANGEFRAGT nicht</b> (nur AKTIV) — Lebenszyklus-Kniff;</li>
 *   <li>kein Mitglied der Organisation → 403 (kein Quer-Zugriff in fremde Firmen).</li>
 * </ul>
 * Der Aufrufer ist über den festen Token-{@code sub} an eine Identität gebunden; die Setup-Endpunkte
 * laufen mit {@code rechnung-pflege}, der Portal-Endpunkt prüft ausschließlich die Org-Mitgliedschaft.
 */
@QuarkusTest
@TestSecurity(user = "kc-portal-azubi", roles = "rechnung-pflege")
class PortalAzubiAnmeldungTest {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void ansprechpartner_meldetAzubiAn_statusAngefragt_undLaufBuchtSieNicht() {
        long n = uniq();
        int sjy = 8000 + (int) (n % 900);
        String schuljahr = sjy + "/" + (sjy + 1);
        String apEmail = "portal-ap+" + n + "@firma.de";

        // Aufrufer-Identität an den festen Token-sub binden
        int callerId = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-portal-azubi","email":"%s","anzeigeName":"Anke Ansprech"}""".formatted(apEmail))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().jsonPath().getInt("id");

        // Organisation + Aufrufer als buchungsberechtigter Ausbilder
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Portal Lehr GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Anke Ansprech","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(apEmail))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(callerId));

        // Portal: Azubi anmelden → ANGEFRAGT auf den Firmen-Debitor
        int debitorId = given().contentType(ContentType.JSON)
                .body("""
                        {"organisationId":%d,"azubiEmail":"azubi+%d@firma.de","azubiName":"Anna Azubi",
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgId, n, schuljahr))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                .body("status", equalTo("ANGEFRAGT"))
                .body("teilnehmerName", equalTo("Anna Azubi"))
                .extract().jsonPath().getInt("zahlungspflichtigerDebitorId");

        given().when().get("/rechnung/debitoren/" + debitorId).then().statusCode(200)
                .body("rolle", equalTo("FIRMA"))
                .body("debitorNr", startsWith("BS-"))
                .body("name", startsWith("Portal Lehr GmbH"));

        // Lebenszyklus-Kniff: der Berufsschul-Lauf bucht die ANGEFRAGTe Anmeldung NICHT (nur AKTIV)
        given().contentType(ContentType.JSON)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .body("findAll { it.debitorId == " + debitorId + " }.size()", equalTo(0));
    }

    @Test
    void portalAnmeldung_durchNichtMitglied_istVerboten() {
        long n = uniq();

        given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-portal-azubi","email":"portal-nm+%d@firma.de","anzeigeName":"Anke Ansprech"}"""
                        .formatted(n))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)));

        // Fremde Organisation ohne Mitgliedschaft des Aufrufers
        long orgB = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Fremd GmbH %d","plz":"44135","ort":"Dortmund","land":"DE"}""".formatted(n))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"organisationId":%d,"azubiEmail":"azubi-nm+%d@firma.de","azubiName":"Anna Azubi",
                         "schuljahr":"9100/9101","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgB, n))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(403);
    }
}
