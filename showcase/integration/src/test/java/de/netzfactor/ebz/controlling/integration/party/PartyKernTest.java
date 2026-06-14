package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
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
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void buchungImFirmenkontext_landetBeimFirmenDebitor_undImRechnungslauf() {
        long n = uniq();
        int sjy = 5000 + (int) (n % 900);
        String schuljahr = sjy + "/" + (sjy + 1);

        // Organisation + buchungsberechtigter Ausbilder (Besteller) + Azubi (Teilnehmer)
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Bau Lehr GmbH %d","plz":"45657","ort":"Recklinghausen","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201)
                .extract().jsonPath().getLong("id");
        int bestellerId = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"ausbilder+%d@firma.de","anzeigeName":"Bernd Ausbilder","rolle":"AUSBILDER",
                         "buchungsberechtigt":true}""".formatted(n))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");
        int azubiId = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"azubi+%d@firma.de","anzeigeName":"Anna Azubi","rolle":"AZUBI",
                         "buchungsberechtigt":false}""".formatted(n))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");

        // Ausbilder bucht im Firmenkontext für den Azubi → zahlungspflichtiger Debitor wird projiziert
        int debitorId = given().contentType(ContentType.JSON)
                .body("""
                        {"teilnehmerPersonId":%d,"bestellerPersonId":%d,"kontextOrganisationId":%d,
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"DOPPEL",
                         "unterrichtBetragCent":150000,"uebernachtungBetragCent":130000}"""
                        .formatted(azubiId, bestellerId, orgId, schuljahr))
                .when().post("/party/buchungen/berufsschule").then().statusCode(201)
                .body("teilnehmerName", equalTo("Anna Azubi"))
                .body("teilnehmerPersonId", equalTo(azubiId))
                .extract().jsonPath().getInt("zahlungspflichtigerDebitorId");

        // Der projizierte Debitor ist der FIRMEN-Debitor der Organisation (BS-Nummer)
        given().when().get("/rechnung/debitoren/" + debitorId).then().statusCode(200)
                .body("rolle", equalTo("FIRMA"))
                .body("debitorNr", startsWith("BS-"))
                .body("name", startsWith("Bau Lehr GmbH"));

        // Bestehender Rechnungslauf (R1) verarbeitet die Buchung → Rechnung an genau diesen Debitor
        given().contentType(ContentType.JSON)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .body("findAll { it.debitorId == " + debitorId + " }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @TestSecurity(user = "kc-token-login")
    void login_nimmtSubAusToken_undBuendeltMehrereAdressenAufEineIdentitaet() {
        long n = uniq();
        // Login mit erster Adresse: sub kommt NICHT aus dem Body, sondern aus dem Token (Principal)
        int id1 = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"tom+a+%d@example.com","anzeigeName":"Tom Token"}""".formatted(n))
                .when().post("/party/personen/login").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("keycloakSub", equalTo("kc-token-login"))
                .body("status", equalTo("AKTIV"))
                .extract().jsonPath().getInt("id");

        // Folge-Login mit ZWEITER Adresse, gleiches Token → dieselbe Identität (sub-geführt)
        int id2 = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"tom+b+%d@example.com","anzeigeName":"Tom Token"}""".formatted(n))
                .when().post("/party/personen/login").then()
                .statusCode(200) // sub bereits bekannt → keine neue Person
                .body("keycloakSub", equalTo("kc-token-login"))
                .extract().jsonPath().getInt("id");

        org.junit.jupiter.api.Assertions.assertEquals(id1, id2, "ein Token-sub = eine Identität");
    }

    @Test
    void loginOhneAuth_istVerboten() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"x@example.com\",\"anzeigeName\":\"X\"}")
                .when().post("/party/personen/login").then().statusCode(greaterThanOrEqualTo(401));
    }

    @Test
    @TestSecurity(user = "kc-portal-user", roles = "rechnung-pflege")
    void firmenportal_siehtNurFirmenkontext_nichtPrivatbuchungen() {
        long n = uniq();
        int sjy = 6000 + (int) (n % 900);
        String schuljahr = sjy + "/" + (sjy + 1);
        String email = "portal+" + n + "@firma.de";

        // Aufrufer-Identität an den Token-sub gebunden (Admin-Provisionierung mit sub = Principal)
        int callerId = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-portal-user","email":"%s","anzeigeName":"Petra Portal"}""".formatted(email))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().jsonPath().getInt("id");

        // Firma A mit Aufrufer als buchungsberechtigtem Mitglied; Firma B ohne Mitgliedschaft
        long orgA = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Portal A GmbH %d","plz":"45657","ort":"Recklinghausen","land":"DE"}""".formatted(n))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Petra Portal","rolle":"SEMINAR_BUCHER","buchungsberechtigt":true}"""
                        .formatted(email))
                .when().post("/party/organisationen/" + orgA + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(callerId));
        long orgB = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Portal B GmbH %d","plz":"44135","ort":"Dortmund","land":"DE"}""".formatted(n))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");

        // Aufrufer bucht für sich im Firmenkontext A UND privat
        int aOrg = given().contentType(ContentType.JSON)
                .body("""
                        {"teilnehmerPersonId":%d,"bestellerPersonId":%d,"kontextOrganisationId":%d,
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(callerId, callerId, orgA, schuljahr))
                .when().post("/party/buchungen/berufsschule").then().statusCode(201)
                .extract().jsonPath().getInt("anmeldungId");
        int aPriv = given().contentType(ContentType.JSON)
                .body("""
                        {"teilnehmerPersonId":%d,"bestellerPersonId":%d,
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":90000}"""
                        .formatted(callerId, callerId, schuljahr))
                .when().post("/party/buchungen/berufsschule").then().statusCode(201)
                .extract().jsonPath().getInt("anmeldungId");

        // Firmenportal A: sieht NUR die Firmenkontext-Buchung, NICHT die Privatbuchung
        given().when().get("/party/firmensicht/" + orgA).then().statusCode(200)
                .body("anmeldungId", hasItem(aOrg))
                .body("anmeldungId", not(hasItem(aPriv)));

        // Firmenportal B: Aufrufer ist kein Mitglied → 403 (keine Quersicht in fremde Firmen)
        given().when().get("/party/firmensicht/" + orgB).then().statusCode(403);

        // 360°-Sicht (intern): sieht BEIDE Buchungen der Identität
        given().when().get("/party/personen/" + callerId + "/buchungen").then().statusCode(200)
                .body("anmeldungId", hasItem(aOrg))
                .body("anmeldungId", hasItem(aPriv));
    }

    @Test
    void schreibenOhneRolle_istVerboten() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"X\"}")
                .when().post("/party/organisationen").then().statusCode(greaterThanOrEqualTo(401));
    }
}
