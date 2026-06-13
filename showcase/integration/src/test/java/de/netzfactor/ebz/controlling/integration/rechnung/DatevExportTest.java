package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * DATEV-Übergabe (R4): ein festgeschriebener Berufsschul-Beleg ergibt einen Buchungssatz (Debitor-
 * Personenkonto an steuerfreies Erlöskonto, Soll), der Storno kehrt auf Haben, der EXTF-Buchungsstapel
 * ist abrufbar und {@code POST /datev/uebergabe} übergibt an den aktiven Weg. Echte controlling-DB →
 * Filter auf die hier erzeugten Belegnummern.
 */
@QuarkusTest
class DatevExportTest {

    private static String schuljahr() {
        int y = 4000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void belegWirdZuBuchungssatz_extfUndUebergabe() {
        // Debitor über die R3-Hoheit (zentrale numerische Nummer → Personenkonto)
        var debitor = given().contentType(ContentType.JSON)
                .body("""
                        {"bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"DATEV Test GmbH %d",
                         "plz":"45657","ort":"Recklinghausen","land":"DE"}""".formatted(System.nanoTime()))
                .when().post("/rechnung/debitoren/anlegen").then().statusCode(201)
                .extract().jsonPath();
        long debitorId = debitor.getLong("id");
        String erwartetesKonto = debitor.getString("debitorNr").replaceAll("[^0-9]", "");

        String sj = schuljahr();
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"BERUFSSCHULE","teilnehmerName":"Dirk DATEV","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","schuljahr":"%s","halbjahr":1,"zimmerart":"DOPPEL",
                         "unterrichtBetragCent":150000,"uebernachtungBetragCent":130000}""".formatted(debitorId, sj))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);
        long rid = given().contentType(ContentType.JSON).body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(sj))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class).get(0);
        String nr = given().when().post("/rechnung/rechnungen/" + rid + "/ausstellen").then().statusCode(200)
                .extract().jsonPath().getString("nummer");

        String heute = LocalDate.now().toString();

        // Buchungssatz: Debitor-Personenkonto an steuerfreies Erlöskonto (8120), Soll, 280.000 Cent
        Map<String, ?> satz = given().when()
                .get("/rechnung/datev/buchungssaetze?von=" + heute + "&bis=" + heute + "&bereich=BERUFSSCHULE")
                .then().statusCode(200)
                .extract().jsonPath().getMap("find { it.belegfeld1 == '" + nr + "' }");
        org.junit.jupiter.api.Assertions.assertEquals(erwartetesKonto, satz.get("konto"), "Debitor-Personenkonto");
        org.junit.jupiter.api.Assertions.assertEquals("8120", satz.get("gegenkonto"), "Erlöskonto §4-befreit");
        org.junit.jupiter.api.Assertions.assertEquals("S", satz.get("sollHaben"));
        org.junit.jupiter.api.Assertions.assertEquals(280000, ((Number) satz.get("umsatzCent")).longValue());

        // EXTF-Buchungsstapel abrufbar (text/csv, Format-Kopf, enthält Belegnummer)
        String csv = given().when()
                .get("/rechnung/datev/buchungsstapel?von=" + heute + "&bis=" + heute + "&bereich=BERUFSSCHULE")
                .then().statusCode(200)
                .header("Content-Type", org.hamcrest.Matchers.containsString("text/csv"))
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("\"EXTF\";700;21;\"Buchungsstapel\""), "EXTF-Kopf");
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains(nr), "Belegnummer im Stapel");

        // Storno → Buchungssatz auf Haben
        long stornoId = given().when().post("/rechnung/rechnungen/" + rid + "/storno").then().statusCode(200)
                .extract().jsonPath().getLong("id");
        String stornoNr = given().when().get("/rechnung/rechnungen/" + stornoId).then()
                .extract().jsonPath().getString("nummer");
        Map<String, ?> stornoSatz = given().when()
                .get("/rechnung/datev/buchungssaetze?von=" + heute + "&bis=" + heute + "&bereich=BERUFSSCHULE")
                .then().statusCode(200)
                .extract().jsonPath().getMap("find { it.belegfeld1 == '" + stornoNr + "' }");
        org.junit.jupiter.api.Assertions.assertEquals("H", stornoSatz.get("sollHaben"), "Storno = Haben");

        // Übergabe an den aktiven Weg (Default EXTF-CSV)
        given().when().post("/rechnung/datev/uebergabe?von=" + heute + "&bis=" + heute + "&bereich=BERUFSSCHULE")
                .then().statusCode(200)
                .body("modus", equalTo("EXTF-CSV"))
                .body("anzahlBuchungen", greaterThanOrEqualTo(2))
                .body("artefaktBytes", greaterThanOrEqualTo(1));
    }

    @Test
    void datevOhneRolle_istVerboten() {
        given().when().get("/rechnung/datev/buchungsstapel").then().statusCode(greaterThanOrEqualTo(401));
    }
}
