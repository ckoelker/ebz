package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Freie Sonderrechnung (Ad-hoc-Beleg außerhalb der Läufe): anlegen → leerer Entwurf ohne Nummer →
 * Position ergänzen → ausstellen (festgeschrieben, Nummer). Unbekannter Debitor → 404, fehlende
 * {@code debitorId} → 400, ohne Rolle → 401/403.
 */
@QuarkusTest
class RechnungSonderrechnungTest {

    private long createDebitor() {
        String body = """
                {"debitorNr":"SR-DEB-%d","bereich":"AKADEMIE","rolle":"FIRMA","name":"Sonder Akademie GmbH",
                 "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE","email":"sr+%d@firma.de"}
                """.formatted(System.nanoTime(), System.nanoTime());
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/debitoren")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void sonderrechnung_anlegen_bestuecken_ausstellen() {
        long debitor = createDebitor();

        // anlegen → leerer ENTWURF ohne Nummer; Bereich default = Debitor-Bereich (AKADEMIE)
        long id = given().contentType(ContentType.JSON)
                .body("""
                        {"debitorId":%d,"zeitraumBezeichnung":"Einmalleistung Beratung 06/2026"}""".formatted(debitor))
                .when().post("/rechnung/rechnungen")
                .then().statusCode(201)
                .body("status", equalTo("ENTWURF"))
                .body("nummer", nullValue())
                .body("bereich", equalTo("AKADEMIE"))
                .body("summeCent", equalTo(0))
                .extract().jsonPath().getLong("id");

        // bestücken (manuelle Position)
        given().contentType(ContentType.JSON)
                .body("""
                        {"beschreibung":"Tagessatz Beratung","menge":2,"einzelbetragCent":90000,
                         "steuerfall":"STANDARD","steuersatz":19,"leistungsart":"SONSTIGE"}""")
                .when().post("/rechnung/rechnungen/" + id + "/positionen")
                .then().statusCode(200)
                .body("summeCent", equalTo(180000));

        // ausstellen → festgeschrieben mit Nummer
        given().when().post("/rechnung/rechnungen/" + id + "/ausstellen")
                .then().statusCode(200)
                .body("status", equalTo("AUSGESTELLT"))
                .body("nummer", notNullValue());
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void unbekannterDebitor_404() {
        given().contentType(ContentType.JSON).body("""
                {"debitorId":987654321}""")
                .when().post("/rechnung/rechnungen").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void ohneDebitor_400() {
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/rechnung/rechnungen").then().statusCode(400);
    }

    @Test
    void ohneRolle_verboten() {
        given().contentType(ContentType.JSON).body("""
                {"debitorId":1}""")
                .when().post("/rechnung/rechnungen").then().statusCode(greaterThanOrEqualTo(401));
    }
}
