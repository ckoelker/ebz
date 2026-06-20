package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Manueller Zahlungseingang: ein festgeschriebener Forderungs-Beleg geht {@code AUSGESTELLT → BEZAHLT}
 * (Datum/Betrag/Referenz vermerkt; Defaults heute/Belegsumme). Entwürfe und ein bereits bezahlter Beleg
 * werden abgewiesen (409). Offene Posten/Mahnwesen/Lastschrift bleiben bewusst bei DATEV — hier nur der
 * schlanke „bezahlt"-Vermerk fürs Beleg-Cockpit.
 */
@QuarkusTest
class RechnungZahlungseingangTest {

    private static String eindeutigesSchuljahr() {
        int y = 3000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    private long createDebitor() {
        String body = """
                {"debitorNr":"ZE-DEB-%d","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"Zahlungs Ausbildungs GmbH",
                 "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE","email":"ze+%d@firma.de"}
                """.formatted(System.nanoTime(), System.nanoTime());
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/debitoren")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    /** Erzeugt für den Debitor einen Sammel-Entwurf (Unterricht 150000 ct) und gibt dessen Id zurück. */
    private long entwurfFuer(long debitorId, String schuljahr) {
        given().contentType(ContentType.JSON).body("""
                {"typ":"BERUFSSCHULE","teilnehmerName":"Zora Zahlung","zahlungspflichtigerDebitorId":%d,"status":"AKTIV",
                 "schuljahr":"%s","halbjahr":2,"zimmerart":"KEINE","unterrichtBetragCent":150000}
                """.formatted(debitorId, schuljahr))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);
        JsonPath lauf = given().contentType(ContentType.JSON)
                .body("""
                        {"schuljahr":"%s","halbjahr":2}""".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200).extract().jsonPath();
        int idx = lauf.getList("debitorId").indexOf((int) debitorId);
        Assertions.assertTrue(idx >= 0, "Sammelrechnung für Debitor erwartet");
        return lauf.getList("id", Long.class).get(idx);
    }

    private long ausgestellterBeleg() {
        long rechnungId = entwurfFuer(createDebitor(), eindeutigesSchuljahr());
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen")
                .then().statusCode(200).body("status", equalTo("AUSGESTELLT"));
        return rechnungId;
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void ausgestellterBeleg_wirdAlsBezahltVerbucht() {
        long id = ausgestellterBeleg();

        given().contentType(ContentType.JSON)
                .body("""
                        {"bezahltAm":"2026-06-20","zahlbetragCent":150000,"zahlungsReferenz":"Überweisung KA-4711"}""")
                .when().post("/rechnung/rechnungen/" + id + "/bezahlen")
                .then().statusCode(200)
                .body("status", equalTo("BEZAHLT"))
                .body("bezahltAm", equalTo("2026-06-20"))
                .body("zahlbetragCent", equalTo(150000))
                .body("zahlungsReferenz", equalTo("Überweisung KA-4711"));

        // am Beleg persistiert
        given().when().get("/rechnung/rechnungen/" + id).then().statusCode(200)
                .body("status", equalTo("BEZAHLT"))
                .body("bezahltAm", notNullValue());
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void bezahlen_mitLeeremBody_nutztDefaults_heuteUndBelegsumme() {
        long id = ausgestellterBeleg();

        given().contentType(ContentType.JSON).body("{}")
                .when().post("/rechnung/rechnungen/" + id + "/bezahlen")
                .then().statusCode(200)
                .body("status", equalTo("BEZAHLT"))
                .body("bezahltAm", notNullValue())
                .body("zahlbetragCent", equalTo(150000)); // Default = Belegsumme
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void entwurfBezahlen_istVerboten_409() {
        long id = entwurfFuer(createDebitor(), eindeutigesSchuljahr()); // bleibt ENTWURF
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/rechnung/rechnungen/" + id + "/bezahlen").then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void bereitsBezahlt_nochmalBezahlen_istVerboten_409() {
        long id = ausgestellterBeleg();
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/rechnung/rechnungen/" + id + "/bezahlen").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/rechnung/rechnungen/" + id + "/bezahlen").then().statusCode(409);
    }

    @Test
    void bezahlenOhneRolle_istVerboten() {
        given().when().post("/rechnung/rechnungen/424242/bezahlen").then()
                .statusCode(greaterThanOrEqualTo(401));
    }
}
