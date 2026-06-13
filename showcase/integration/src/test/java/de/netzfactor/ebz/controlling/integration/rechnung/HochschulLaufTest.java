package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * Hochschul-Rechnungslauf (R6): Firmen-Split erzeugt ZWEI getrennte Rechnungen (Firma + Studierende:r,
 * unabhängige Forderungen), Raten zerlegen eine Forderung in N Rechnungen (Restbetrag in die letzte),
 * und der Cross-Field-Validator weist unstimmige Splits ab. Echte controlling-DB → Filter auf die hier
 * angelegten Debitoren.
 */
@QuarkusTest
class HochschulLaufTest {

    private static String semester() {
        return "WS" + (1000 + (int) (System.nanoTime() % 9000));
    }

    private long anlegen(String name, String rolle) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"bereich":"HOCHSCHULE","rolle":"%s","name":"%s %d","plz":"45657"}"""
                        .formatted(rolle, name, System.nanoTime()))
                .when().post("/rechnung/debitoren/anlegen").then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void firmenSplit_erzeugtZweiGetrennteRechnungen() {
        long student = anlegen("Studi", "PRIVAT");
        long firma = anlegen("Dual AG", "FIRMA");
        String sem = semester();
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"HOCHSCHULE","teilnehmerName":"Sven Student","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","semester":"%s","semesterbetragCent":600000,
                         "firmaDebitorId":%d,"firmaAnteilCent":400000}""".formatted(student, sem, firma))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);

        JsonPath jp = given().contentType(ContentType.JSON).body("{\"semester\":\"%s\"}".formatted(sem))
                .when().post("/rechnung/laeufe/hochschule").then().statusCode(200).extract().jsonPath();

        Map<String, ?> firmaR = jp.getMap("find { it.debitorId == " + firma + " }");
        Map<String, ?> studR = jp.getMap("find { it.debitorId == " + student + " }");
        assertEquals(400000, ((Number) firmaR.get("summeCent")).longValue(), "Firmenanteil");
        assertEquals(200000, ((Number) studR.get("summeCent")).longValue(), "Eigenanteil = Rest");
        org.junit.jupiter.api.Assertions.assertTrue(((String) firmaR.get("zeitraumBezeichnung")).contains("Firmenanteil"));
        org.junit.jupiter.api.Assertions.assertTrue(((String) studR.get("zeitraumBezeichnung")).contains("Eigenanteil"));
        assertNotEquals(firmaR.get("id"), studR.get("id"), "zwei getrennte Rechnungen");
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void raten_zerlegenForderung_mitRestInLetzterRate() {
        long student = anlegen("Ratenzahler", "PRIVAT");
        String sem = semester();
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"HOCHSCHULE","teilnehmerName":"Rita Rate","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","semester":"%s","semesterbetragCent":100000,"ratenAnzahl":3}"""
                        .formatted(student, sem))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);

        JsonPath jp = given().contentType(ContentType.JSON).body("{\"semester\":\"%s\"}".formatted(sem))
                .when().post("/rechnung/laeufe/hochschule").then().statusCode(200).extract().jsonPath();

        List<Map<String, ?>> raten = jp.getList("findAll { it.debitorId == " + student + " }");
        assertEquals(3, raten.size(), "drei Raten-Rechnungen");
        long summe = raten.stream().mapToLong(r -> ((Number) r.get("summeCent")).longValue()).sum();
        assertEquals(100000, summe, "Raten summieren sich auf die volle Gebühr");
        long letzte = raten.stream().mapToLong(r -> ((Number) r.get("summeCent")).longValue()).max().getAsLong();
        assertEquals(33334, letzte, "Restbetrag (100000/3) liegt in der letzten Rate");
        org.junit.jupiter.api.Assertions.assertTrue(
                raten.stream().anyMatch(r -> ((String) r.get("zeitraumBezeichnung")).contains("Rate 3/3")));
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void firmaAnteilOhneFirmaDebitor_liefert400() {
        long student = anlegen("Solo", "PRIVAT");
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"HOCHSCHULE","teilnehmerName":"X","zahlungspflichtigerDebitorId":%d,"status":"AKTIV",
                         "semester":"WS2026","semesterbetragCent":600000,"firmaAnteilCent":100000}""".formatted(student))
                .when().post("/rechnung/anmeldungen").then().statusCode(400)
                .body(containsString("firmaDebitorId"));
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void firmaAnteilNichtKleinerAlsSemesterbetrag_liefert400() {
        long student = anlegen("Stud2", "PRIVAT");
        long firma = anlegen("Voll AG", "FIRMA");
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"HOCHSCHULE","teilnehmerName":"Y","zahlungspflichtigerDebitorId":%d,"status":"AKTIV",
                         "semester":"WS2026","semesterbetragCent":600000,"firmaDebitorId":%d,"firmaAnteilCent":600000}"""
                        .formatted(student, firma))
                .when().post("/rechnung/anmeldungen").then().statusCode(400)
                .body(containsString("firmaAnteilCent"));
    }
}
