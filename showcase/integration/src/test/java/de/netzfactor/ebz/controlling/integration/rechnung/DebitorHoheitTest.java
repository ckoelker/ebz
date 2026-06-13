package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

/**
 * Debitoren-Hoheit (R3): zentrale idempotente Vergabe, Altbestand-Import mit Alias-Auflösung,
 * Dublettenkandidaten und Merge (Forderungen umhängen + Altnummern konservieren). Läuft gegen die
 * echte controlling-DB → run-eindeutige Namen/Nummern, damit der Dublettenschlüssel je Lauf isoliert.
 */
@QuarkusTest
class DebitorHoheitTest {

    private static String uniq() {
        return Long.toString(System.nanoTime());
    }

    private static ValidatableResponse anlegen(String name, String plz) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"%s","strasse":"Hauptstr. 1",
                         "plz":"%s","ort":"Recklinghausen","land":"DE"}""".formatted(name, plz))
                .when().post("/rechnung/debitoren/anlegen").then();
    }

    private static long createLegacy(String debitorNr, String name, String plz) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"debitorNr":"%s","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"%s",
                         "strasse":"Hauptstr. 1","plz":"%s","ort":"Recklinghausen","land":"DE"}"""
                        .formatted(debitorNr, name, plz))
                .when().post("/rechnung/debitoren").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void anlegen_istIdempotent_undVergibtZentraleNummer() {
        String name = "Idem GmbH " + uniq();
        long id1 = anlegen(name, "45657").statusCode(201).extract().jsonPath().getLong("id");
        long id2 = anlegen(name, "45657").statusCode(200).extract().jsonPath().getLong("id");
        assertEquals(id1, id2, "gleiche Identität → kein zweiter Debitor");
        given().when().get("/rechnung/debitoren/" + id1).then().statusCode(200)
                .body("debitorNr", startsWith("BS-"))
                .body("status", equalTo("AKTIV"));
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void importBestand_dedupliziert_aufEinenGoldenRecord() {
        String name = "Dublette GmbH " + uniq();
        String nrA = "ALT-" + uniq();
        String nrB = "ALT-" + uniq();
        long a = importBestand("DATEV-A", nrA, name).extract().jsonPath().getLong("id");
        long b = importBestand("DATEV-B", nrB, name).extract().jsonPath().getLong("id");
        assertEquals(a, b, "zwei Alt-Debitoren gleicher Identität → ein Golden-Record");

        // Beide Altnummern lösen auf denselben Golden-Record auf
        given().when().get("/rechnung/debitoren/aufloesen?quelle=DATEV-A&externeNr=" + nrA).then()
                .statusCode(200).body("id", equalTo((int) a));
        given().when().get("/rechnung/debitoren/aufloesen?quelle=DATEV-B&externeNr=" + nrB).then()
                .statusCode(200).body("id", equalTo((int) a));
        given().when().get("/rechnung/debitoren/aufloesen?quelle=DATEV-A&externeNr=GIBTESNICHT").then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void merge_haengtForderungenUm_undKonserviertAltnummer() {
        String name = "Merge GmbH " + uniq();
        long quell = createLegacy("BS-Q-" + uniq(), name, "45657");
        long ziel = createLegacy("BS-Z-" + uniq(), name, "45657");
        String quellNr = given().when().get("/rechnung/debitoren/" + quell).then().statusCode(200)
                .extract().jsonPath().getString("debitorNr");

        // Eine Forderung an der Dublette: Anmeldung + Lauf → Rechnung-Entwurf für quell
        String sj = (5000 + (int) (System.nanoTime() % 900)) + "/" + (5001 + (int) (System.nanoTime() % 900));
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"BERUFSSCHULE","teilnehmerName":"Merge Azubi","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE",
                         "unterrichtBetragCent":150000}""".formatted(quell, sj))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);
        long rid = given().contentType(ContentType.JSON).body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(sj))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class).get(0);

        // Kandidaten: quell sieht ziel als Dublette
        given().when().get("/rechnung/debitoren/" + quell + "/kandidaten").then().statusCode(200)
                .body("id", hasItem((int) ziel));

        // Merge quell → ziel
        given().contentType(ContentType.JSON).body("{\"quellId\":%d,\"zielId\":%d}".formatted(quell, ziel))
                .when().post("/rechnung/debitoren/merge").then().statusCode(200).body("id", equalTo((int) ziel));

        // Forderung jetzt unter ziel
        given().when().get("/rechnung/rechnungen/" + rid).then().statusCode(200)
                .body("debitorId", equalTo((int) ziel));
        // Dublette ist zusammengeführt und zeigt auf ziel
        given().when().get("/rechnung/debitoren/" + quell).then().statusCode(200)
                .body("status", equalTo("ZUSAMMENGEFUEHRT")).body("goldenDebitorId", equalTo((int) ziel));
        // Alte Nummer der Dublette bleibt als Alias auf ziel auflösbar
        given().when().get("/rechnung/debitoren/aufloesen?quelle=MERGE&externeNr=" + quellNr).then()
                .statusCode(200).body("id", equalTo((int) ziel));
        // Nach dem Merge ist quell kein aktiver Kandidat mehr
        given().when().get("/rechnung/debitoren/" + ziel + "/kandidaten").then().statusCode(200)
                .body("size()", equalTo(0));
    }

    private static ValidatableResponse importBestand(String quelle, String externeNr, String name) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"quelle":"%s","externeNr":"%s","debitor":{"bereich":"BERUFSSCHULE","rolle":"FIRMA",
                         "name":"%s","strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE"}}"""
                        .formatted(quelle, externeNr, name))
                .when().post("/rechnung/debitoren/import").then().statusCode(200);
    }
}
