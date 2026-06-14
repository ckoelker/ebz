package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

import jakarta.inject.Inject;

/**
 * Anmeldung Berufsschule — Schritt E (EBZ-Bestätigung): eine ANGEFRAGTe Azubi-Anmeldung wird vom EBZ
 * bestätigt ({@code ANGEFRAGT → BESTAETIGT_EBZ}); Azubi <b>und</b> Firma (Besteller) erhalten je eine
 * Bestätigungsmail. Doppelte Bestätigung ist nicht möglich (409). Die Azubi-Dubletten-Prüfung selbst
 * läuft über die HITL-Review-Queue aus Schritt B (PERSON-Fälle).
 */
@QuarkusTest
@TestSecurity(user = "kc-ebz-confirm", roles = "rechnung-pflege")
class AnmeldungBestaetigungTest {

    @Inject
    MockMailbox mailbox;

    private static long uniq() {
        return System.nanoTime();
    }

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    void ebzBestaetigung_setztStatus_undBenachrichtigtAzubiUndFirma() {
        long n = uniq();
        int sjy = 8500 + (int) (n % 400);
        String schuljahr = sjy + "/" + (sjy + 1);
        String apEmail = "ebz-ap+" + n + "@firma.de";
        String azubiEmail = "ebz-azubi+" + n + "@firma.de";

        // Ansprechpartner (Aufrufer-sub) + Firma + buchungsberechtigte Mitgliedschaft
        int callerId = given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-ebz-confirm","email":"%s","anzeigeName":"Ariane Ausbild"}""".formatted(apEmail))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201))).extract().jsonPath().getInt("id");
        long orgId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Bestaet Bau GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"DE%d"}"""
                        .formatted(n, n % 100000000L))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getLong("id");
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","anzeigeName":"Ariane Ausbild","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                        .formatted(apEmail))
                .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                .body("id", equalTo(callerId));

        // Azubi-Anmeldung übers Portal → ANGEFRAGT
        int anmeldungId = given().contentType(ContentType.JSON)
                .body("""
                        {"organisationId":%d,"azubiEmail":"%s","azubiName":"Anton Azubi",
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgId, azubiEmail, schuljahr))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                .body("status", equalTo("ANGEFRAGT"))
                .extract().jsonPath().getInt("anmeldungId");

        // Cockpit-Liste (Schritt I): die ANGEFRAGTe Anmeldung erscheint unter den offenen
        given().when().get("/party/anmeldungen?status=ANGEFRAGT").then().statusCode(200)
                .body("find { it.anmeldungId == " + anmeldungId + " }.status", equalTo("ANGEFRAGT"));

        mailbox.clear(); // nur die Bestätigungsmails zählen

        // EBZ bestätigt → BESTAETIGT_EBZ
        given().contentType(ContentType.JSON)
                .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(200)
                .body("status", equalTo("BESTAETIGT_EBZ"))
                .body("teilnehmerName", equalTo("Anton Azubi"));

        // Azubi-Mail geht an die (eindeutige) Anmeldungs-Adresse; insgesamt zwei Mails (Azubi + Firma).
        // Die Firmen-Adresse ist die primaere des Bestellers (bei wiederverwendetem Test-sub ggf. eine
        // fruehere) — daher hier die Gesamtzahl statt einer konkreten Firmen-Adresse pruefen.
        Assertions.assertEquals(1, mailbox.getMailsSentTo(azubiEmail).size(), "Bestätigung an den Azubi");
        Assertions.assertEquals(2, mailbox.getTotalMessagesSent(), "je eine Mail an Azubi und Firma");

        // Zweite Bestätigung ist nicht möglich (nicht mehr ANGEFRAGT) → 409
        given().contentType(ContentType.JSON)
                .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(409);
    }
}
