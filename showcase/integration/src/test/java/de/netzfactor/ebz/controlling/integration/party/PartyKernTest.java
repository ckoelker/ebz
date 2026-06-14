package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Party-Kern: <i>eine Identität, n Bestellkontexte</i>. Beweist den Kern der Leitfrage —
 * <ul>
 *   <li><b>Kollision/Claim:</b> Firma legt einen Azubi per E-Mail vor an (provisorische Person); loggt
 *       sich derselbe Mensch später privat mit <i>derselben</i> E-Mail ein, ist es <i>dieselbe</i>
 *       Person-ID, jetzt AKTIV mit Login (kein Doppel-Account).</li>
 *   <li><b>Kontext→Debitor:</b> die Identität hat den Kontext PRIVAT und „im Auftrag von …"; der
 *       Abrechnungs-Debitor folgt dem gewählten Kontext (privat vs. Firma) und Bereich.</li>
 *   <li><b>Merge:</b> eine über eine Zweit-E-Mail entstandene Dublette wird auf eine Identität geführt.</li>
 * </ul>
 * Läuft gegen die echte controlling-DB → eindeutige E-Mails je Lauf via {@code nanoTime}.
 */
@QuarkusTest
class PartyKernTest {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void firmenVorAnlage_dann_privaterLogin_ergibtEineIdentitaet_mitNKontexten() {
        long n = uniq();
        String email = "max.muster+" + n + "@example.com";

        // Organisation (Hauptkunde) anlegen
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Muster Immobilien GmbH %d","plz":"45657","ort":"Recklinghausen",
                         "land":"DE","ustId":"DE%d"}""".formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201)
                .extract().jsonPath().getLong("id");

        // Firma legt den Azubi per E-Mail vor an → provisorische Person + Mitgliedschaft AZUBI
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Max Muster","rolle":"AZUBI","buchungsberechtigt":false}"""
                        .formatted(email))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .body("status", equalTo("PROVISORISCH"))
                .body("keycloakSub", equalTo(null))
                .body("emails", contains(email.toLowerCase()))
                .extract().jsonPath().getInt("id");

        // Derselbe Mensch registriert sich später privat mit DERSELBEN E-Mail → dieselbe Person, geclaimt
        given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-%d","email":"%s","anzeigeName":"Max Muster"}""".formatted(n, email))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(200) // 200 = vorhandene Person geclaimt, NICHT 201 (kein Doppel-Account)
                .body("id", equalTo(personId))
                .body("status", equalTo("AKTIV"))
                .body("keycloakSub", equalTo("kc-" + n));

        // Eine zweite buchungsberechtigte Mitgliedschaft (Nebenbeschäftigung: Aufsichtsrat bei Firma B)
        long orgB = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Nebenan AG %d","plz":"44135","ort":"Dortmund","land":"DE"}""".formatted(n))
                .when().post("/party/organisationen").then().statusCode(201)
                .extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Max Muster","rolle":"AUFSICHTSRAT","buchungsberechtigt":true}"""
                        .formatted(email))
                .when().post("/party/organisationen/" + orgB + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(personId)); // weiterhin dieselbe Identität

        // n Bestellkontexte: PRIVAT + Firma B (buchungsberechtigt). Firma A (AZUBI, nicht berechtigt) NICHT.
        given().when().get("/party/personen/" + personId + "/kontexte").then().statusCode(200)
                .body("art", hasItem("PRIVAT"))
                .body("art", hasItem("FIRMA"))
                .body("findAll { it.art == 'FIRMA' }.organisationId", contains((int) orgB));

        // Kontext→Debitor: privat (Akademie-Seminar) ergibt einen PRIVAT-Debitor mit AK-Nummer
        given().when().get("/party/personen/" + personId + "/debitor?bereich=AKADEMIE").then().statusCode(200)
                .body("rolle", equalTo("PRIVAT"))
                .body("debitorNr", startsWith("AK-"))
                .body("name", equalTo("Max Muster"));

        // Kontext→Debitor: im Auftrag von Firma B (Hochschule) ergibt einen FIRMA-Debitor mit HS-Nummer
        given().when().get("/party/personen/" + personId + "/debitor?organisationId=" + orgB + "&bereich=HOCHSCHULE")
                .then().statusCode(200)
                .body("rolle", equalTo("FIRMA"))
                .body("debitorNr", startsWith("HS-"))
                .body("name", startsWith("Nebenan AG"));

        // Nicht buchungsberechtigter Kontext (Firma A, AZUBI) → 409
        given().when().get("/party/personen/" + personId + "/debitor?organisationId=" + orgId + "&bereich=BERUFSSCHULE")
                .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void zweitAdresse_ergibtDublette_dieGemergtWird() {
        long n = uniq();
        // Person 1 über private E-Mail
        int p1 = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-a-%d","email":"erika.musterfrau+%d@privat.de","anzeigeName":"Erika Musterfrau"}"""
                        .formatted(n, n))
                .when().post("/party/personen/selbstregistrieren").then().statusCode(201)
                .extract().jsonPath().getInt("id");

        // Person 2: gleiche Person, aber über eine ANDERE (dienstliche) E-Mail → Dublette gleichen Namens
        int p2 = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-b-%d","email":"erika.musterfrau+%d@firma.de","anzeigeName":"Erika Musterfrau"}"""
                        .formatted(n, n))
                .when().post("/party/personen/selbstregistrieren").then().statusCode(201)
                .extract().jsonPath().getInt("id");

        // Kandidaten (gleicher Namensschlüssel) enthält die Dublette
        given().when().get("/party/personen/" + p1 + "/kandidaten").then().statusCode(200)
                .body("id", hasItem(p2));

        // Merge p2 → p1: beide E-Mails hängen danach an einer Identität
        given().contentType(ContentType.JSON)
                .body("{\"quellId\":%d,\"zielId\":%d}".formatted(p2, p1))
                .when().post("/party/personen/merge").then().statusCode(200)
                .body("id", equalTo(p1))
                .body("emails.size()", greaterThanOrEqualTo(2));

        // Die unterlegene Person zeigt nun als ZUSAMMENGEFUEHRT auf den Golden-Record
        given().when().get("/party/personen/" + p2).then().statusCode(200)
                .body("status", equalTo("ZUSAMMENGEFUEHRT"))
                .body("goldenPersonId", equalTo(p1));
    }

    @Test
    void schreibenOhneRolle_istVerboten() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"X\"}")
                .when().post("/party/organisationen").then().statusCode(greaterThanOrEqualTo(401));
    }
}
