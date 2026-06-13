package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Quellen-agnostische Naht Commerce → Billing (R7): eine externe (Vendure-)Bestellung wird zum
 * Rechnungs-Entwurf, idempotent je {@code quelle|externeId}, Kunde über die Debitoren-Hoheit (R3)
 * aufgelöst; nach Festschreibung liefert der Beleg ein valides ZUGFeRD mit Regelsteuer (19 %).
 */
@QuarkusTest
class BestellungBillingTest {

    private static String body(String externeId, long n) {
        return """
                {"quelle":"VENDURE","externeId":"%s","zahlungsart":"RECHNUNG",
                 "debitor":{"bereich":"SHOP","rolle":"FIRMA","name":"Shop Kunde %d","plz":"45657",
                            "ort":"Recklinghausen","land":"DE"},
                 "positionen":[
                   {"beschreibung":"Fachbuch Controlling","betragCent":4990,"steuerfall":"STANDARD","steuersatz":19},
                   {"beschreibung":"Seminarunterlagen","betragCent":2500,"steuerfall":"STANDARD","steuersatz":19}]}"""
                .formatted(externeId, n);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void externeBestellung_wirdEntwurf_idempotent_undAusstellbarMitUst() {
        long n = System.nanoTime();
        String oid = "VENDURE-" + n;

        JsonPath jp = given().contentType(ContentType.JSON).body(body(oid, n))
                .when().post("/rechnung/quellen/bestellung").then().statusCode(201)
                .extract().jsonPath();
        long rid = jp.getLong("id");
        long debitorId = jp.getLong("debitorId");
        org.junit.jupiter.api.Assertions.assertEquals("ENTWURF", jp.getString("status"));
        org.junit.jupiter.api.Assertions.assertEquals("SHOP", jp.getString("bereich"));
        org.junit.jupiter.api.Assertions.assertEquals(7490, jp.getLong("summeCent"));
        org.junit.jupiter.api.Assertions.assertEquals(2, jp.getList("positionen").size());

        // Kunde über die Debitoren-Hoheit angelegt (zentrale Nummer SH-…)
        given().when().get("/rechnung/debitoren/" + debitorId).then().statusCode(200)
                .body("debitorNr", startsWith("SH-"));

        // Idempotent: erneuter Push derselben Bestellung → 200, gleiche Rechnung
        given().contentType(ContentType.JSON).body(body(oid, n))
                .when().post("/rechnung/quellen/bestellung").then().statusCode(200)
                .body("id", equalTo((int) rid));

        // Festschreibung → valides ZUGFeRD mit Regelsteuer
        given().when().post("/rechnung/rechnungen/" + rid + "/ausstellen").then().statusCode(200)
                .body("nummer", startsWith("RE-SH-"));
        byte[] pdf = given().when().get("/rechnung/rechnungen/" + rid + "/zugferd").then()
                .statusCode(200).contentType("application/pdf").extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(pdf.length > 3000, "ZUGFeRD-PDF nicht leer");
    }

    @Test
    void ohneRolle_istVerboten() {
        given().contentType(ContentType.JSON).body(body("VENDURE-x", 1))
                .when().post("/rechnung/quellen/bestellung").then().statusCode(greaterThanOrEqualTo(401));
    }
}
