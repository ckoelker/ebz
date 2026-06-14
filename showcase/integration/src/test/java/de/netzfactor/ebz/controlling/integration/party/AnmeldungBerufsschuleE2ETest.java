package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import de.netzfactor.ebz.controlling.integration.party.service.KeycloakLoginProvisionierung;

/**
 * <b>End-to-End-Durchstich „Anmeldung Berufsschule" als Living-Documentation-Quelle.</b>
 * <p>
 * Jede Testmethode spielt ein vollständiges Prozess-Szenario in Reihenfolge durch und trägt über den
 * W3C-{@code baggage}-Header die Fall-Korrelation ({@code prozess.fall}) — daraus stempeln die im
 * Service-Code gesetzten {@code Prozessspur}-Business-Spans ihre Case-Id. Der {@code SpanLogExporter}
 * schreibt sie nach {@code target/prozess-log/spans.jsonl}; der Generator ({@code showcase/prozessdoku/})
 * macht daraus BPMN. Mehrere Szenarien → Gateways. (Stage 2: Happy-Path; Varianten folgen.)
 * <p>
 * Der rest-assured-Client fährt das Backend in-process; Front-/Dritt-/Mock-Schritte (Portal-SPA,
 * Keycloak, Mail) sind über die Span-Attribute korrekt einem System/Akteur zugeordnet, auch wenn der
 * Test den jeweiligen REST-Endpunkt direkt aufruft.
 */
@QuarkusTest
@TestSecurity(user = "kc-e2e-happy", roles = "rechnung-pflege")
class AnmeldungBerufsschuleE2ETest {

    private static long uniq() {
        return System.nanoTime();
    }

    /** Eindeutige Test-IP je Lauf (öffentlicher Lead-Rate-Limit, 5/min/IP). */
    private static String testIp(long n) {
        return "198.51." + (int) ((n / 251) % 251) + "." + (int) (n % 251);
    }

    /** rest-assured-Spezifikation mit Fall-Korrelation über den W3C-baggage-Header. */
    private static RequestSpecification gegeben(String fall, long n) {
        return given()
                .contentType(ContentType.JSON)
                .header("baggage", "prozess.fall=" + fall)
                .header("X-Forwarded-For", testIp(n));
    }

    /** Aufgezeichnete Provisionierung — Subklasse der Impl, damit QuarkusMock den CDI-Proxy ersetzen kann. */
    public static class FakeProvisionierung extends KeycloakLoginProvisionierung {
        @Override
        public Ergebnis anlegen(String email, String anzeigeName) {
            return new Ergebnis("kc-user-e2e", true, true);
        }
    }

    @Test
    void happyPath_neueFirma_neuerAzubi() {
        QuarkusMock.installMockForType(new FakeProvisionierung(), KeycloakLoginProvisionierung.class);

        String fall = "happy-path";
        long n = uniq();
        int sjy = 7000 + (int) (n % 900);
        String schuljahr = sjy + "/" + (sjy + 1);
        String apEmail = "e2e-ap+" + n + "@lehrbetrieb.de";
        String azubiEmail = "e2e-azubi+" + n + "@lehrbetrieb.de";

        // 1) Firma stellt öffentliche Ausbildungsbetrieb-Anfrage (Portal, anonym) → ANGEFRAGT
        var anfrage = gegeben(fall, n)
                .body("""
                        {"name":"E2E Lehrbau GmbH %d","strasse":"Werkstr. 1","plz":"45657","ort":"Recklinghausen",
                         "land":"DE","ustId":"DE%d","ansprechpartnerEmail":"%s","ansprechpartnerName":"Berta Bilden"}"""
                        .formatted(n, n % 100000000L, apEmail))
                .when().post("/party/anfragen/ausbildungsbetrieb").then()
                .statusCode(201)
                .body("organisationStatus", equalTo("ANGEFRAGT"))
                .extract().jsonPath();
        long orgId = anfrage.getLong("organisationId");
        int apId = anfrage.getInt("ansprechpartner.id");

        // 2) EBZ prüft im Cockpit und bestätigt die Firma als Neuanlage (keine Dublette) → AKTIV
        gegeben(fall, n)
                .body("""
                        {"art":"FIRMA","kandidatId":%d,"entscheidung":"NEUANLAGE_BESTAETIGT"}""".formatted(orgId))
                .when().post("/party/reviews/entscheidung").then().statusCode(200);

        // 3) EBZ lädt den Ansprechpartner ein → Keycloak-Login provisioniert + Einladungsmail
        gegeben(fall, n)
                .when().post("/party/personen/" + apId + "/einladung").then().statusCode(200)
                .body("provisioniert", equalTo(true));

        // 4) Ansprechpartner meldet sich erstmals an → claimt die provisorische Person (Login gebunden)
        gegeben(fall, n)
                .body("""
                        {"keycloakSub":"kc-e2e-happy","email":"%s","anzeigeName":"Berta Bilden"}""".formatted(apEmail))
                .when().post("/party/personen/selbstregistrieren").then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .body("id", equalTo(apId));

        // 5) Ansprechpartner meldet einen Azubi an (Portal) → ANGEFRAGT
        int anmeldungId = gegeben(fall, n)
                .body("""
                        {"organisationId":%d,"azubiEmail":"%s","azubiName":"Anton Azubi",
                         "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                        .formatted(orgId, azubiEmail, schuljahr))
                .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                .body("status", equalTo("ANGEFRAGT"))
                .extract().jsonPath().getInt("anmeldungId");

        // 6) EBZ bestätigt die Anmeldung → BESTAETIGT_EBZ (+ Mails an Azubi & Firma)
        gegeben(fall, n)
                .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(200)
                .body("status", equalTo("BESTAETIGT_EBZ"));

        // 7) Firma bestätigt den Ausbildungsvertrag (Portal) → AKTIV
        gegeben(fall, n)
                .when().post("/party/portal/anmeldungen/" + anmeldungId + "/vertrag-bestaetigen").then()
                .statusCode(200)
                .body("status", equalTo("AKTIV"));

        // 8) Rechnungslauf bucht die nun aktive Anmeldung
        gegeben(fall, n)
                .body("{\"schuljahr\":\"%s\",\"halbjahr\":1}".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }
}
