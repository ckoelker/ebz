package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.kommunikation.service.ZustellService;
import de.netzfactor.ebz.controlling.integration.party.service.KeycloakLoginProvisionierung;

/**
 * Anmeldung Berufsschule — Schritt C (Login-Provisionierung + Einladungsmail):
 * <ul>
 *   <li>die Einladung sendet eine Mail an den Ansprechpartner (gegen die MockMailbox geprüft);</li>
 *   <li>bei aktiver Provisionierung (hier gemockt) meldet der Endpunkt die Keycloak-User-Id;</li>
 *   <li>der erste Login mit derselben E-Mail claimt die provisorische Person → AKTIV.</li>
 * </ul>
 * Im Test ist die echte Keycloak-Provisionierung aus ({@code %test.anmeldung.provisionierung.enabled=false})
 * → kein laufendes Keycloak nötig; die KC-User-Anlage selbst wird gegen das Dev-Keycloak verifiziert.
 */
@QuarkusTest
@TestSecurity(user = "sb", roles = "rechnung-pflege")
class EinladungTest {

    @Inject
    MockMailbox mailbox;

    @Inject
    ZustellService zustellService;

    private static long uniq() {
        return System.nanoTime();
    }

    /** Eindeutige Test-IP je Lauf, damit der öffentliche Lead-Rate-Limit (5/min/IP) Tests nicht koppelt. */
    private static String testIp(long n) {
        return "198.51." + (int) ((n / 251) % 251) + "." + (int) (n % 251);
    }

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    private static int anfrageMitAnsprechpartner(long n, String email) {
        return given().contentType(ContentType.JSON)
                .header("X-Forwarded-For", testIp(n)) // eigene Test-IP → isolierter Rate-Limit-Schluessel
                .body("""
                        {"name":"Einlad Bau GmbH %d","plz":"45657","ort":"RE","land":"DE",
                         "ansprechpartnerEmail":"%s","ansprechpartnerName":"Anke Ansprech"}"""
                        .formatted(n, email))
                .when().post("/party/anfragen/ausbildungsbetrieb").then().statusCode(201)
                .extract().jsonPath().getInt("ansprechpartner.id");
    }

    @Test
    void einladung_sendetEinladungsmail_ohneAktiveProvisionierung() {
        long n = uniq();
        String email = "ap+" + n + "@lehrbetrieb.de";
        int personId = anfrageMitAnsprechpartner(n, email);

        given().contentType(ContentType.JSON)
                .when().post("/party/personen/" + personId + "/einladung").then().statusCode(200)
                .body("email", equalTo(email))
                .body("eingeladen", equalTo(true))
                .body("provisioniert", equalTo(false)); // KC im Test deaktiviert

        zustellService.verarbeiteFaellige(100); // Einladungsmail läuft über die Spine-Outbox
        Assertions.assertEquals(1, mailbox.getMailsSentTo(email).size(), "genau eine Einladungsmail");
    }

    /** Aufgezeichnete Provisionierung — Subklasse der Impl, damit QuarkusMock den CDI-Proxy ersetzen kann. */
    public static class FakeProvisionierung extends KeycloakLoginProvisionierung {
        @Override
        public Ergebnis anlegen(String email, String anzeigeName) {
            return new Ergebnis("kc-user-xyz", true, true);
        }
    }

    @Test
    void einladung_meldetKeycloakUserId_wennProvisioniert() {
        QuarkusMock.installMockForType(new FakeProvisionierung(), KeycloakLoginProvisionierung.class);

        long n = uniq();
        String email = "ap2+" + n + "@lehrbetrieb.de";
        int personId = anfrageMitAnsprechpartner(n, email);

        given().contentType(ContentType.JSON)
                .when().post("/party/personen/" + personId + "/einladung").then().statusCode(200)
                .body("provisioniert", equalTo(true))
                .body("keycloakUserId", equalTo("kc-user-xyz"));

        zustellService.verarbeiteFaellige(100);
        Assertions.assertEquals(1, mailbox.getMailsSentTo(email).size(), "Einladungsmail trotz Provisionierung");
    }

    @Test
    void claim_nachEinladung_aktiviertProvisorischePerson() {
        long n = uniq();
        String email = "ap3+" + n + "@lehrbetrieb.de";
        int personId = anfrageMitAnsprechpartner(n, email);

        given().contentType(ContentType.JSON)
                .when().post("/party/personen/" + personId + "/einladung").then().statusCode(200);

        // Erster Login mit derselben E-Mail (sub im Body, eindeutig je Lauf) → claimt die provisorische Person
        given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-claim-%d","email":"%s","anzeigeName":"Anke Ansprech"}""".formatted(n, email))
                .when().post("/party/personen/selbstregistrieren").then().statusCode(200)
                .body("id", equalTo(personId))
                .body("status", equalTo("AKTIV"))
                .body("keycloakSub", equalTo("kc-claim-" + n));
    }
}
