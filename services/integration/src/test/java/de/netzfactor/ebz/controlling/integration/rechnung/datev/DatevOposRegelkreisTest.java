package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * D3: Der OPOS-Regelkreis setzt einen ausgestellten Beleg automatisch auf {@code BEZAHLT}, sobald DATEV
 * ihn als ausgeglichen meldet. Die {@link OposQuelle} wird per {@code @Mock} ersetzt (kein DATEVconnect),
 * der Test legt einen Beleg über die echte controlling-DB an und treibt {@code verarbeiteFaellige()}
 * selbst (Dispatcher im Test faktisch aus).
 */
@QuarkusTest
class DatevOposRegelkreisTest {

    /** Test-Quelle: liefert genau die vom Test eingestellten Posten (ersetzt {@link OposQuelleMock}). */
    @Mock
    @ApplicationScoped
    public static class OposQuelleTestMock implements OposQuelle {
        final List<Posten> posten = new ArrayList<>();

        @Override
        public List<Posten> ausgeglichenePosten(int max) {
            return posten.stream().limit(max).toList();
        }
    }

    @Inject
    OposRegelkreisService regelkreis;

    @Inject
    OposQuelleTestMock quelle;

    private static String schuljahr() {
        int y = 5000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void oposAusgeglichen_setztRechnungAutomatischBezahlt() {
        // Beleg erzeugen (Debitor → Anmeldung → Lauf → Ausstellen)
        JsonPath debitor = given().contentType(ContentType.JSON)
                .body("""
                        {"bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"OPOS Test GmbH %d",
                         "plz":"45657","ort":"Recklinghausen","land":"DE"}""".formatted(System.nanoTime()))
                .when().post("/rechnung/debitoren/anlegen").then().statusCode(201).extract().jsonPath();
        long debitorId = debitor.getLong("id");

        String sj = schuljahr();
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"BERUFSSCHULE","teilnehmerName":"Olaf OPOS","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","schuljahr":"%s","halbjahr":1,"zimmerart":"DOPPEL",
                         "unterrichtBetragCent":150000,"uebernachtungBetragCent":130000}""".formatted(debitorId, sj))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);
        JsonPath lauf = given().contentType(ContentType.JSON)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(sj))
                .when().post("/rechnung/laeufe").then().statusCode(200).extract().jsonPath();
        long rid = lauf.getList("id", Long.class).get(lauf.getList("debitorId").indexOf((int) debitorId));
        String nr = given().when().post("/rechnung/rechnungen/" + rid + "/ausstellen").then().statusCode(200)
                .extract().jsonPath().getString("nummer");

        // DATEV meldet den Beleg als ausgeglichen → Regelkreis verbucht
        quelle.posten.add(new OposQuelle.Posten(nr, LocalDate.now(), 280000L, "OPOS-" + nr));
        int verbucht = regelkreis.verarbeiteFaellige(50);
        assertTrue(verbucht >= 1, "mindestens ein Posten verbucht");

        // Beleg ist jetzt automatisch BEZAHLT
        given().when().get("/rechnung/rechnungen/" + rid).then().statusCode(200)
                .body("status", equalTo("BEZAHLT"));

        // Idempotent: erneuter Lauf verbucht denselben (nun bezahlten) Beleg nicht noch einmal
        assertTrue(regelkreis.verarbeiteFaellige(50) == 0, "kein erneutes Verbuchen");
        quelle.posten.clear();
    }
}
