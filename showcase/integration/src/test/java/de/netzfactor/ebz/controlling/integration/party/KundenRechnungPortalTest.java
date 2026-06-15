package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Kunden-Rechnungsabruf im Außenportal: der eingeloggte Kunde sieht — kontext-skopiert (privat vs. je
 * buchungsberechtigter Firma) — seine festgeschriebenen Belege und lädt deren ZUGFeRD-PDF. Fremde Belege
 * und Kontexte ohne Buchungsberechtigung sind ausgeschlossen (403); unauthentifiziert 401.
 *
 * <p>Wie die übrigen E2E-Party-Tests trägt <i>eine</i> Test-Identität ({@code sub kc-portal}) zugleich die
 * Kundenrolle (Selbst-Login) und {@code rechnung-pflege}, damit die internen Aufbau-Schritte (Bestätigen,
 * Lauf, Ausstellen) im selben Test laufen.
 */
@QuarkusTest
@TestSecurity(user = "kc-portal", roles = "rechnung-pflege")
class KundenRechnungPortalTest {

    private static long uniq() {
        return System.nanoTime();
    }

    /** Baut über den vollen Pfad eine festgeschriebene Firmen-Rechnung und gibt {orgId, rechnungId} zurück. */
    private long[] festgeschriebeneFirmenrechnung(String sub, String schuljahr) {
        long n = uniq();
        String apEmail = "portal-ap+" + n + "@firma.de";
        int callerId = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"%s","email":"%s","anzeigeName":"Petra Portal"}""".formatted(sub, apEmail))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201))).extract().jsonPath().getInt("id");
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Portal Bau GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Petra Portal","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(apEmail))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(callerId));

        int anmeldungId = given().contentType(ContentType.JSON)
                .body("""
                        {"organisationId":%d,"azubiEmail":"portal-azubi+%d@firma.de","azubiName":"Pia Azubi",
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgId, n, schuljahr))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                .extract().jsonPath().getInt("anmeldungId");
        given().contentType(ContentType.JSON)
                .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .when().post("/party/portal/anmeldungen/" + anmeldungId + "/vertrag-bestaetigen").then().statusCode(200)
                .body("status", equalTo("AKTIV"));

        // Rechnungslauf (eindeutiges Schuljahr → genau unsere Anmeldung) → Entwurf → Ausstellen
        JsonPath lauf = given().contentType(ContentType.JSON)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1)).extract().jsonPath();
        long rechnungId = neuesterEntwurf(lauf);
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen").then().statusCode(200)
                .body("status", equalTo("AUSGESTELLT"));
        return new long[] { orgId, rechnungId };
    }

    private static String schuljahr(int basis) {
        int y = basis + (int) (System.nanoTime() % 80);
        return y + "/" + (y + 1);
    }

    /**
     * Der frisch erzeugte Entwurf <i>dieses</i> Laufs (höchste id mit Status {@code ENTWURF}). Der Lauf ist
     * idempotent und listet alle AKTIV-Anmeldungen des Schuljahrs — auf der persistenten Showcase-DB können
     * darunter bereits ausgestellte Belege früherer Läufe gleichen Schuljahrs (anderer Debitor) sein; {@code
     * get(0)} träfe sonst zufällig einen davon. Unser neuer Beleg trägt stets die höchste id.
     */
    private static long neuesterEntwurf(JsonPath lauf) {
        List<Integer> ids = lauf.getList("findAll { it.status == 'ENTWURF' }.id", Integer.class);
        return ids.stream().mapToLong(Integer::longValue).max().orElseThrow();
    }

    @Test
    void kunde_siehtFirmenrechnungImKontext_undLaedtPdf() {
        long[] ids = festgeschriebeneFirmenrechnung("kc-portal", schuljahr(7100));
        long orgId = ids[0];
        long rechnungId = ids[1];

        // Kontext-Auswahl: PRIVAT + die buchungsberechtigte Firma
        given().when().get("/party/portal/rechnungs-kontexte").then().statusCode(200)
                .body("art", hasItem("PRIVAT"))
                .body("find { it.organisationId == " + orgId + " }.art", equalTo("FIRMA"));

        // Firmenkontext: die festgeschriebene Rechnung erscheint, mit Nummer
        given().when().get("/party/portal/rechnungen?organisationId=" + orgId).then().statusCode(200)
                .body("id", hasItem((int) rechnungId))
                .body("find { it.id == " + rechnungId + " }.status", equalTo("AUSGESTELLT"))
                .body("find { it.id == " + rechnungId + " }.nummer", notNullValue());

        // Privatkontext (Selbstzahler) zeigt die Firmen-Rechnung NICHT
        given().when().get("/party/portal/rechnungen").then().statusCode(200)
                .body("id", not(hasItem((int) rechnungId)));

        // PDF-Download der eigenen Rechnung
        given().when().get("/party/portal/rechnungen/" + rechnungId + "/zugferd").then().statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    void fremdeRechnung_undFremderKontext_sindVerboten() {
        long n = uniq();
        String sjB = schuljahr(7300);

        // Eigene Identität als Kunde etablieren (sub kc-portal), ohne Mitgliedschaft in orgB
        given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-portal","email":"portal-self+%d@firma.de","anzeigeName":"Petra Portal"}"""
                        .formatted(n))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)));

        // Fremde Firma orgB mit eigenem buchungsberechtigtem Besteller + Azubi → festgeschriebene Rechnung
        long orgB = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Fremd Portal GmbH %d","plz":"44135","ort":"Dortmund","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, (n + 7) % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        int p2 = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"p2portal+%d@fremd.de","anzeigeName":"Paul Partner","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(n))
                .when().post("/party/organisationen/" + orgB + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");
        int azubiB = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"azubiBportal+%d@fremd.de","anzeigeName":"Berta Azubi","rolle":"AZUBI","buchungsberechtigt":false}"""
                        .formatted(n))
                .when().post("/party/organisationen/" + orgB + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"teilnehmerPersonId":%d,"bestellerPersonId":%d,"kontextOrganisationId":%d,
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(azubiB, p2, orgB, sjB))
                .when().post("/party/buchungen/berufsschule").then().statusCode(201);
        JsonPath lauf = given().contentType(ContentType.JSON)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(sjB))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1)).extract().jsonPath();
        long rechnungIdB = neuesterEntwurf(lauf);
        given().when().post("/rechnung/rechnungen/" + rechnungIdB + "/ausstellen").then().statusCode(200);

        // kc-portal ist NICHT Mitglied von orgB → Kontext verboten
        given().when().get("/party/portal/rechnungen?organisationId=" + orgB).then().statusCode(403);
        // ... und der fremde Beleg ist nicht abrufbar
        given().when().get("/party/portal/rechnungen/" + rechnungIdB + "/zugferd").then().statusCode(403);
    }
}
