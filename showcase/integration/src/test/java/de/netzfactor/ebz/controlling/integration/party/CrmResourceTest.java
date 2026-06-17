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
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void aktivitaet_anlegen_erscheintInKontakthistorie() {
        long n = uniq();
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Hans","nachname":"Historie %d","geschlecht":"MAENNLICH","werbesperre":false,
                         "auskunftssperre":false}""".formatted(n))
                .when().post("/crm/personen").then().statusCode(201).extract().jsonPath().getInt("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"typCode":"TELEFONAT","richtung":"EINGEHEND","betreff":"Rückruf %d","personId":%d,
                         "dauerMinuten":5}""".formatted(n, personId))
                .when().post("/crm/aktivitaeten").then().statusCode(201)
                .body("typ", equalTo("Telefonat"))
                .body("person", startsWith("Hans"));

        given().when().get("/crm/personen/" + personId + "/aktivitaeten").then().statusCode(200)
                .body("betreff", hasItem("Rückruf " + n));
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void einwilligung_optInLebenszyklus_ausstehend_erteilt_widerrufen() {
        long n = uniq();
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Olga","nachname":"OptIn %d","geschlecht":"WEIBLICH","werbesperre":false,
                         "auskunftssperre":false}""".formatted(n))
                .when().post("/crm/personen").then().statusCode(201).extract().jsonPath().getInt("id");

        int einwId = given().contentType(ContentType.JSON)
                .body("""
                        {"personId":%d,"kanal":"EMAIL","zweck":"NEWSLETTER","quelleCode":"WEBSITE"}""".formatted(personId))
                .when().post("/crm/einwilligungen").then().statusCode(201)
                .body("status", equalTo("AUSSTEHEND"))
                .body("kanal", equalTo("EMAIL"))
                .body("zweck", equalTo("NEWSLETTER"))
                .extract().jsonPath().getInt("id");

        // Double-Opt-In bestätigen → ERTEILT
        given().contentType(ContentType.JSON)
                .when().post("/crm/einwilligungen/" + einwId + "/erteilen").then().statusCode(200)
                .body("status", equalTo("ERTEILT"));

        // Widerruf
        given().contentType(ContentType.JSON)
                .when().post("/crm/einwilligungen/" + einwId + "/widerrufen").then().statusCode(200)
                .body("status", equalTo("WIDERRUFEN"));

        given().when().get("/crm/personen/" + personId + "/einwilligungen").then().statusCode(200)
                .body("status", hasItem("WIDERRUFEN"));
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void weiterbildung_stundenkonto_ampelSchaltetBeiErfuellung() {
        long n = uniq();
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Walter","nachname":"Weiterbildung %d","geschlecht":"MAENNLICH","werbesperre":false,
                         "auskunftssperre":false}""".formatted(n))
                .when().post("/crm/personen").then().statusCode(201).extract().jsonPath().getInt("id");

        // Leeres Konto im laufenden Zeitraum → nicht erfüllt
        given().when().get("/crm/personen/" + personId + "/weiterbildung").then().statusCode(200)
                .body("soll", equalTo(20))
                .body("erfuellt", equalTo(false));

        String heute = java.time.LocalDate.now().toString();
        given().contentType(ContentType.JSON)
                .body("""
                        {"personId":%d,"titel":"Maklerrecht-Update","anbieter":"EBZ","stunden":12,"datum":"%s","extern":false}"""
                        .formatted(personId, heute))
                .when().post("/crm/weiterbildung").then().statusCode(201)
                .body("stunden", equalTo(12));
        given().contentType(ContentType.JSON)
                .body("""
                        {"personId":%d,"titel":"WEG-Verwaltung","anbieter":"Verband","stunden":8,"datum":"%s","extern":true}"""
                        .formatted(personId, heute))
                .when().post("/crm/weiterbildung").then().statusCode(201);

        // 12 + 8 = 20 → erfüllt, Ampel grün
        given().when().get("/crm/personen/" + personId + "/weiterbildung").then().statusCode(200)
                .body("summe", equalTo(20.0f))
                .body("erfuellt", equalTo(true))
                .body("ampel", equalTo("GRUEN"))
                .body("nachweise.size()", equalTo(2));
    }

    @Test
    @TestSecurity(user = "sb", roles = "crm-pflege")
    void dublettenPruefung_findetGleichnamigePerson_undFirmaPerUstId() {
        long n = uniq();
        String nachname = "Doppelgaenger " + n;
        int personId = given().contentType(ContentType.JSON)
                .body("""
                        {"vorname":"Dirk","nachname":"%s","geschlecht":"MAENNLICH","werbesperre":false,
                         "auskunftssperre":false}""".formatted(nachname))
                .when().post("/crm/personen").then().statusCode(201).extract().jsonPath().getInt("id");

        // Gleicher Name → Bestandstreffer als Dubletten-Vorschlag
        given().contentType(ContentType.JSON)
                .body("""
                        {"art":"PERSON","vorname":"Dirk","nachname":"%s"}""".formatted(nachname))
                .when().post("/crm/dubletten-pruefung").then().statusCode(200)
                .body("id", hasItem(personId))
                .body("art", hasItem("PERSON"));

        // Unbekannter Name → kein Treffer
        given().contentType(ContentType.JSON)
                .body("""
                        {"art":"PERSON","vorname":"Niemand","nachname":"Gibtsnicht %d"}""".formatted(n))
                .when().post("/crm/dubletten-pruefung").then().statusCode(200)
                .body("size()", equalTo(0));

        // Firma: gleiche USt-IdNr. trotz abweichendem Namen → Treffer
        String ust = "DE" + (n % 1_000_000_000L);
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Erst Immobilien GmbH %d","ustId":"%s"}""".formatted(n, ust))
                .when().post("/crm/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"art":"ORGANISATION","name":"Voellig anderer Name %d","ustId":"%s"}""".formatted(n, ust))
                .when().post("/crm/dubletten-pruefung").then().statusCode(200)
                .body("id", hasItem((int) orgId))
                .body("art", hasItem("ORGANISATION"));
    }

    @Test
    void schreibenOhneRolle_istVerboten() {
        given().contentType(ContentType.JSON)
                .body("{\"vorname\":\"X\",\"nachname\":\"Y\"}")
                .when().post("/crm/personen").then().statusCode(greaterThanOrEqualTo(401));
    }
}
