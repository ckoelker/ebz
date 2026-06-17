package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * CRM-Kernmaske-API: flache Stammdatenpflege Person/Organisation, der N:M-Mitgliedschaften (mit der
 * Haupt-Flag-Invariante „höchstens eine aktiv"), Kontaktpunkte (mit länderabhängiger PLZ-Prüfung),
 * generische Lookup-Reads und globale Suche. Läuft gegen die echte controlling-DB → eindeutige Werte
 * je Lauf via {@code nanoTime}.
 */
@QuarkusTest
class CrmResourceTest {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void lookups_liefernGeseedeteWerte() {
        given().when().get("/crm/lookups/rolle").then().statusCode(200)
                .body("code", hasItem("AZUBI"))
                .body("code", hasItem("GESCHAEFTSFUEHRUNG"));
        given().when().get("/crm/lookups/land").then().statusCode(200)
                .body("code", hasItem("DE"));
        given().when().get("/crm/lookups/quatsch").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void person_anlegen_ableiteAnrede_undKontaktpunkt() {
        long n = uniq();
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Max","nachname":"Mustermann %d","geschlecht":"MAENNLICH","titel":"Dr.",
                         "korrespondenzspracheCode":"de","werbesperre":false,"auskunftssperre":false}"""
                        .formatted(n))
                .when().post("/crm/personen").then().statusCode(201)
                .body("anzeigeName", startsWith("Dr. Max"))
                .body("briefanrede", startsWith("Sehr geehrter Herr Dr."))
                .body("status", equalTo("AKTIV"))
                .extract().jsonPath().getInt("id");

        // E-Mail-Kontaktpunkt anhängen → erscheint in der 360°-E-Mail-Liste
        String mail = "max+" + n + "@example.com";
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"EMAIL","personId":%d,"primaer":true,"email":"%s"}""".formatted(personId, mail))
                .when().post("/crm/kontaktpunkte").then().statusCode(201)
                .body("typ", equalTo("EMAIL"));

        given().when().get("/crm/personen/" + personId).then().statusCode(200)
                .body("emails", hasItem(mail))
                .body("kontaktpunkte.size()", greaterThanOrEqualTo(1));

        // Suche findet die Person
        given().when().get("/crm/suche?q=Mustermann " + n).then().statusCode(200)
                .body("art", hasItem("PERSON"));
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void organisation_mitLookups_undHierarchie() {
        long n = uniq();
        long mutter = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Dach Immobilien AG %d","rechtsform":"AG"}""".formatted(n))
                .when().post("/crm/organisationen").then().statusCode(201)
                .extract().jsonPath().getLong("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Tochter Verwaltung GmbH %d","rechtsform":"GmbH","brancheCode":"HAUSVERWALTUNG",
                         "uebergeordneteId":%d,"ausbildungsbetrieb":true,"ihkKammerCode":"IHK_DORTMUND",
                         "unternehmenstypCodes":["VERWALTER"],"schwerpunktCodes":["WEG_VERWALTUNG","MIET_VERWALTUNG"],
                         "verbandCodes":["DDIV"]}""".formatted(n, mutter))
                .when().post("/crm/organisationen").then().statusCode(201)
                .body("brancheCode", equalTo("HAUSVERWALTUNG"))
                .body("uebergeordneteId", equalTo((int) mutter))
                .body("ausbildungsbetrieb", equalTo(true))
                .body("taetigkeitsschwerpunkte.size()", equalTo(2))
                .body("verbaende", hasItem("DDIV"));
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void mitgliedschaft_hauptzugehoerigkeit_istHoechstensEineAktiv() {
        long n = uniq();
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Petra","nachname":"Profi %d","geschlecht":"WEIBLICH","werbesperre":false,
                         "auskunftssperre":false}""".formatted(n))
                .when().post("/crm/personen").then().statusCode(201).extract().jsonPath().getInt("id");
        long orgA = given().contentType(ContentType.JSON).body("{\"name\":\"Firma A %d\"}".formatted(n))
                .when().post("/crm/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        long orgB = given().contentType(ContentType.JSON).body("{\"name\":\"Firma B %d\"}".formatted(n))
                .when().post("/crm/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");

        // Erste Mitgliedschaft als Hauptzugehörigkeit
        given().contentType(ContentType.JSON)
                .body("""
                        {"rolleCode":"GESCHAEFTSFUEHRUNG","hauptzugehoerigkeit":true,"buchungsberechtigt":true}""")
                .when().post("/crm/personen/" + personId + "/organisationen/" + orgA + "/mitgliedschaften")
                .then().statusCode(201);

        // Zweite Mitgliedschaft ebenfalls als Hauptzugehörigkeit → die erste muss zurückgesetzt werden
        given().contentType(ContentType.JSON)
                .body("""
                        {"rolleCode":"PROKURIST","hauptzugehoerigkeit":true,"buchungsberechtigt":false}""")
                .when().post("/crm/personen/" + personId + "/organisationen/" + orgB + "/mitgliedschaften")
                .then().statusCode(201);

        // Genau eine aktive Hauptzugehörigkeit
        given().when().get("/crm/personen/" + personId).then().statusCode(200)
                .body("mitgliedschaften.findAll { it.hauptzugehoerigkeit == true }.size()", equalTo(1))
                .body("mitgliedschaften.find { it.hauptzugehoerigkeit == true }.organisationId",
                        equalTo((int) orgB));
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void kontaktpunkt_adresse_prueftPlzLaenderabhaengig() {
        long n = uniq();
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Klaus","nachname":"Klein %d","geschlecht":"MAENNLICH","werbesperre":false,
                         "auskunftssperre":false}""".formatted(n))
                .when().post("/crm/personen").then().statusCode(201).extract().jsonPath().getInt("id");

        // Ungültige DE-PLZ → 409
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"ADRESSE","personId":%d,"primaer":true,"strasse":"Hauptstr. 1","plz":"ABC",
                         "ort":"Dortmund","landCode":"DE"}""".formatted(personId))
                .when().post("/crm/kontaktpunkte").then().statusCode(409);

        // Gültige DE-PLZ → 201
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"ADRESSE","personId":%d,"primaer":true,"strasse":"Hauptstr. 1","plz":"44135",
                         "ort":"Dortmund","landCode":"DE"}""".formatted(personId))
                .when().post("/crm/kontaktpunkte").then().statusCode(201)
                .body("plz", equalTo("44135")).body("landCode", equalTo("DE"));
    }

    @Test
    void schreibenOhneRolle_istVerboten() {
        given().contentType(ContentType.JSON)
                .body("{\"vorname\":\"X\",\"nachname\":\"Y\"}")
                .when().post("/crm/personen").then().statusCode(greaterThanOrEqualTo(401));
    }
}
