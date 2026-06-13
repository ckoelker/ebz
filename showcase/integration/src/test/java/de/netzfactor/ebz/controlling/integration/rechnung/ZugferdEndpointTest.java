package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Endpoint-Beweis (R2, §4b): {@code GET /rechnung/rechnungen/{id}/zugferd} liefert für einen
 * ausgestellten Beleg ein valides ZUGFeRD-PDF (application/pdf, %PDF-Magic), für einen Entwurf 409,
 * und ohne Rolle 403. Baut den Beleg über die R1-Endpunkte auf (Test-DB = echte controlling-DB →
 * eindeutige Schlüssel je Lauf).
 */
@QuarkusTest
class ZugferdEndpointTest {

    private static String schuljahr() {
        int y = 3000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void ausgestellteRechnung_liefertValidesZugferdPdf() {
        long debitor = given().contentType(ContentType.JSON)
                .body("""
                        {"debitorNr":"BS-ZF-%d","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"ZUGFeRD Test GmbH",
                         "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE"}"""
                        .formatted(System.nanoTime()))
                .when().post("/rechnung/debitoren").then().statusCode(201)
                .extract().jsonPath().getLong("id");

        String sj = schuljahr();
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"BERUFSSCHULE","teilnehmerName":"Anna Azubi","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","schuljahr":"%s","halbjahr":1,"zimmerart":"DOPPEL",
                         "unterrichtBetragCent":150000,"uebernachtungBetragCent":130000}""".formatted(debitor, sj))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);

        long rid = given().contentType(ContentType.JSON)
                .body("""
                        {"schuljahr":"%s","halbjahr":1}""".formatted(sj))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class).get(0);

        // Entwurf → noch kein ZUGFeRD (409)
        given().when().get("/rechnung/rechnungen/" + rid + "/zugferd").then().statusCode(409);

        given().when().post("/rechnung/rechnungen/" + rid + "/ausstellen").then().statusCode(200);

        // Ausgestellt → valides PDF (application/pdf, %PDF-Magic), Validator-Tor bestanden
        byte[] pdf = given().when().get("/rechnung/rechnungen/" + rid + "/zugferd").then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", startsWith("attachment; filename=\"beleg-RE-BS-"))
                .extract().asByteArray();
        pruefePdf(pdf);

        // Storno → Korrekturbeleg (Typ 381) liefert ebenfalls ein valides ZUGFeRD-PDF
        long stornoId = given().when().post("/rechnung/rechnungen/" + rid + "/storno").then()
                .statusCode(200).extract().jsonPath().getLong("id");
        byte[] stornoPdf = given().when().get("/rechnung/rechnungen/" + stornoId + "/zugferd").then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", startsWith("attachment; filename=\"beleg-ST-BS-"))
                .extract().asByteArray();
        pruefePdf(stornoPdf);
    }

    private static void pruefePdf(byte[] pdf) {
        org.junit.jupiter.api.Assertions.assertTrue(pdf.length > 3000, "PDF nicht leer");
        String magic = new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1);
        org.junit.jupiter.api.Assertions.assertEquals("%PDF-", magic, "PDF-Magic erwartet");
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void zugferdOhneRolle_istVerboten() {
        given().when().get("/rechnung/rechnungen/424242/zugferd").then().statusCode(403);
    }
}
