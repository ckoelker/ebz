package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * <b>End-to-End-Durchstich „Rechnungsstellung (Cockpit & E-Rechnung)" als Living-Documentation-Quelle.</b>
 * <p>
 * Spielt den vollständigen Cockpit-Beleglebenszyklus durch: Sonderrechnung anlegen → Position erfassen →
 * ausstellen (Nummer festschreiben) → E-Rechnung (ZUGFeRD) versenden → Zahlungseingang verbuchen →
 * DATEV-Buchungsstapel übergeben. Jeder Request trägt die Fall-Korrelation über den W3C-{@code baggage}-
 * Header ({@code prozess.fall}); die im Service-Code gesetzten {@code Prozessspur}-Spans (Verfahren
 * {@code RECHNUNGSSTELLUNG}) werden so vom {@code SpanLogExporter} nach {@code spans.jsonl} geschrieben →
 * BPMN ({@code showcase/prozessdoku/}). Analog zu {@code AnmeldungBerufsschuleE2ETest}.
 */
@QuarkusTest
class RechnungsstellungE2ETest {

    /** rest-assured-Spezifikation mit Fall-Korrelation über den W3C-baggage-Header. */
    private static RequestSpecification gegeben(String fall) {
        return given().contentType(ContentType.JSON).header("baggage", "prozess.fall=" + fall);
    }

    @Test
    @TestSecurity(user = "sb-e2e", roles = "rechnung-pflege")
    void cockpit_sonderrechnung_bis_datev() {
        String fall = "sonderrechnung-cockpit";
        long n = System.nanoTime();
        String email = "rechnung-e2e+" + n + "@firma.de";

        // Setup OHNE Baggage (Spans landen als „unbekannt" → vom Generator ignoriert): Debitor anlegen.
        long debitor = given().contentType(ContentType.JSON)
                .body("""
                        {"debitorNr":"RST-%d","bereich":"AKADEMIE","rolle":"FIRMA","name":"E2E Rechnung GmbH",
                         "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE","email":"%s"}"""
                        .formatted(n, email))
                .when().post("/rechnung/debitoren").then().statusCode(201)
                .extract().jsonPath().getLong("id");

        // 1) Sonderrechnung anlegen → leerer ENTWURF ohne Nummer
        long id = gegeben(fall)
                .body("""
                        {"debitorId":%d,"zeitraumBezeichnung":"Einmalleistung Beratung 06/2026"}""".formatted(debitor))
                .when().post("/rechnung/rechnungen").then().statusCode(201)
                .body("status", equalTo("ENTWURF"))
                .extract().jsonPath().getLong("id");

        // 2) Position erfassen (im Entwurf)
        gegeben(fall)
                .body("""
                        {"beschreibung":"Tagessatz Beratung","menge":2,"einzelbetragCent":90000,
                         "steuerfall":"STANDARD","steuersatz":19,"leistungsart":"SONSTIGE"}""")
                .when().post("/rechnung/rechnungen/" + id + "/positionen").then().statusCode(200)
                .body("summeCent", equalTo(180000));

        // 3) ausstellen → festgeschrieben mit lückenloser Nummer
        gegeben(fall).when().post("/rechnung/rechnungen/" + id + "/ausstellen").then().statusCode(200)
                .body("status", equalTo("AUSGESTELLT")).body("nummer", notNullValue());

        // 4) E-Rechnung (ZUGFeRD) an den Debitor versenden → VERSENDET
        gegeben(fall).when().post("/rechnung/rechnungen/" + id + "/versenden").then().statusCode(200)
                .body("versandStatus", equalTo("VERSENDET")).body("versendetAn", equalTo(email));

        // 5) Zahlungseingang verbuchen → BEZAHLT
        gegeben(fall).body("{}").when().post("/rechnung/rechnungen/" + id + "/bezahlen").then().statusCode(200)
                .body("status", equalTo("BEZAHLT"));

        // 6) DATEV-Buchungsstapel des Zeitraums übergeben (enthält den festgeschriebenen Beleg)
        gegeben(fall).when().post("/rechnung/datev/uebergabe").then().statusCode(200)
                .body("anzahlBuchungen", notNullValue());
    }
}
