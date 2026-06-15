package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

import jakarta.inject.Inject;

/**
 * Rechnungsversand: ein festgeschriebener Beleg wird als ZUGFeRD-E-Rechnung per E-Mail an den Debitor
 * zugestellt (PDF-Anhang), der Versandstatus am Beleg nachgehalten. Entwürfe und Debitoren ohne Postfach
 * werden abgewiesen (409). Test nutzt die Quarkus-{@link MockMailbox} (kein echter SMTP-Verkehr).
 */
@QuarkusTest
class RechnungVersandTest {

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    private static String eindeutigesSchuljahr() {
        int y = 3000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    private long createDebitor(String email) {
        String mail = email == null ? "" : ",\"email\":\"" + email + "\"";
        String body = """
                {"debitorNr":"BS-DEB-%d","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"Versand Ausbildungs GmbH",
                 "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE"%s}
                """.formatted(System.nanoTime(), mail);
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/debitoren")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private void createBsAnmeldung(long debitorId, String schuljahr) {
        String body = """
                {"typ":"BERUFSSCHULE","teilnehmerName":"Vera Versand","zahlungspflichtigerDebitorId":%d,"status":"AKTIV",
                 "schuljahr":"%s","halbjahr":2,"zimmerart":"KEINE","unterrichtBetragCent":150000}
                """.formatted(debitorId, schuljahr);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/anmeldungen").then().statusCode(201);
    }

    /** Erzeugt für den Debitor eine Sammelrechnung (Entwurf) und gibt deren Id zurück. */
    private long entwurfFuer(long debitorId, String schuljahr) {
        createBsAnmeldung(debitorId, schuljahr);
        JsonPath lauf = given().contentType(ContentType.JSON)
                .body("""
                        {"schuljahr":"%s","halbjahr":2}""".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200).extract().jsonPath();
        int idx = lauf.getList("debitorId").indexOf((int) debitorId);
        Assertions.assertTrue(idx >= 0, "Sammelrechnung für Debitor erwartet");
        return lauf.getList("id", Long.class).get(idx);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void festgeschriebenerBeleg_wirdAlsERechnungVersendet_undStatusGesetzt() {
        String email = "versand+" + System.nanoTime() + "@firma.de";
        long debitor = createDebitor(email);
        long rechnungId = entwurfFuer(debitor, eindeutigesSchuljahr());

        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen").then().statusCode(200)
                .body("status", equalTo("AUSGESTELLT"))
                .body("versandStatus", equalTo("NICHT_VERSENDET"));

        mailbox.clear();

        // Versand → Status VERSENDET, Adresse + Zeitpunkt vermerkt
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/versenden").then().statusCode(200)
                .body("versandStatus", equalTo("VERSENDET"))
                .body("versendetAn", equalTo(email))
                .body("versendetAm", notNullValue());

        // genau eine Mail an den Debitor mit dem ZUGFeRD-PDF im Anhang
        List<Mail> mails = mailbox.getMailsSentTo(email);
        Assertions.assertEquals(1, mails.size(), "eine Rechnungsmail an den Debitor");
        Assertions.assertEquals(1, mails.get(0).getAttachments().size(), "ZUGFeRD-PDF als Anhang");
        Assertions.assertTrue(mails.get(0).getAttachments().get(0).getName().startsWith("beleg-"),
                "Anhang heißt beleg-<Nummer>.pdf");

        // Versand ist am Beleg persistiert
        given().when().get("/rechnung/rechnungen/" + rechnungId).then().statusCode(200)
                .body("versandStatus", equalTo("VERSENDET"))
                .body("versendetAn", equalTo(email));

        // Re-Send erlaubt → zweite Mail
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/versenden").then().statusCode(200)
                .body("versandStatus", equalTo("VERSENDET"));
        Assertions.assertEquals(2, mailbox.getMailsSentTo(email).size(), "Re-Send erzeugt eine weitere Mail");
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void entwurfVersenden_istVerboten_409() {
        long debitor = createDebitor("entwurf+" + System.nanoTime() + "@firma.de");
        long rechnungId = entwurfFuer(debitor, eindeutigesSchuljahr()); // bleibt ENTWURF

        given().when().post("/rechnung/rechnungen/" + rechnungId + "/versenden").then().statusCode(409);
        Assertions.assertEquals(0, mailbox.getTotalMessagesSent(), "Entwurf löst keinen Versand aus");
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void debitorOhneEmail_kannNichtVersendetWerden_409() {
        long debitor = createDebitor(null); // keine E-Mail
        long rechnungId = entwurfFuer(debitor, eindeutigesSchuljahr());
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen").then().statusCode(200);

        mailbox.clear();
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/versenden").then().statusCode(409);
        Assertions.assertEquals(0, mailbox.getTotalMessagesSent(), "ohne Postfach kein Versand");
    }

    @Test
    void versendenOhneRolle_istVerboten() {
        given().when().post("/rechnung/rechnungen/424242/versenden").then()
                .statusCode(greaterThanOrEqualTo(401));
    }
}
