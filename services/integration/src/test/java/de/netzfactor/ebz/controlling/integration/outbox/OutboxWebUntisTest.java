package de.netzfactor.ebz.controlling.integration.outbox;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Outbox-Drittsystem-Provisionierung am Beispiel <b>WebUntis</b>: belegt, dass die Vertragsbestätigung
 * von der Erreichbarkeit des Drittsystems entkoppelt ist und der Versand garantiert nachgezogen wird.
 * <ol>
 *   <li>Vertragsbestätigung legt einen WebUntis-Auftrag an (atomar mit {@code AKTIV}).</li>
 *   <li>WebUntis down → Dispatcher schlägt fehl, Auftrag bleibt {@code OFFEN} (Retry); nach
 *       {@code MAX_VERSUCHE} → {@code FEHLGESCHLAGEN} (Dead-Letter, HITL).</li>
 *   <li>WebUntis wieder da + manueller Neuversuch → {@code ERLEDIGT}, Azubi in WebUntis.</li>
 *   <li>Erneuter Dispatch ist idempotent (kein Dublett, kein Re-Export).</li>
 * </ol>
 */
@QuarkusTest
@TestProfile(OutboxWebUntisTest.SchedulerAus.class)
@TestSecurity(user = "kc-outbox", roles = "rechnung-pflege")
class OutboxWebUntisTest {

    /**
     * Deterministisches Versuchs-Counting (Dead-Letter): der Hintergrund-Dispatcher darf hier NICHT
     * nebenläufig dazwischengreifen — dieser Test steuert die Zustellung gezielt über {@code /outbox/dispatch}.
     */
    public static class SchedulerAus implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled", "false");
        }
    }

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void webUntisDowntime_retryDannDeadLetter_dannManuellerNeuversuchErfolgreich() {
        long n = uniq();
        int sjy = 6000 + (int) (n % 900);
        String schuljahr = sjy + "/" + (sjy + 1);
        String apEmail = "outbox-ap+" + n + "@firma.de";
        String azubiEmail = "outbox-azubi+" + n + "@firma.de";

        try {
            // Ausgangslage: WebUntis verfügbar (defensiv, da der Mock-Bean JVM-weit geteilt wird)
            given().when().post("/mock/webuntis/verfuegbarkeit?verfuegbar=true").then().statusCode(200);

            // ── Anmeldung bis AKTIV bringen (legt den Outbox-Auftrag an) ──
            int callerId = given().contentType(ContentType.JSON)
                    .body("""
                            {"keycloakSub":"kc-outbox","email":"%s","anzeigeName":"Olaf Outbox"}""".formatted(apEmail))
                    .when().post("/party/personen/selbstregistrieren").then()
                    .statusCode(anyOf(equalTo(200), equalTo(201))).extract().jsonPath().getInt("id");
            long orgId = given().contentType(ContentType.JSON)
                    .body("""
                            {"name":"Outbox Bau GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"DE%d"}"""
                            .formatted(n, n % 100000000L))
                    .when().post("/party/organisationen").then().statusCode(201)
                    .extract().jsonPath().getLong("id");
            given().contentType(ContentType.JSON)
                    .body("""
                            {"email":"%s","anzeigeName":"Olaf Outbox","rolle":"AUSBILDER","buchungsberechtigt":true}"""
                            .formatted(apEmail))
                    .when().post("/party/organisationen/" + orgId + "/teilnehmer").then().statusCode(201)
                    .body("id", equalTo(callerId));

            int anmeldungId = given().contentType(ContentType.JSON)
                    .body("""
                            {"organisationId":%d,"azubiEmail":"%s","azubiName":"Olga Azubi",
                             "schuljahr":"%s","halbjahr":1,"zimmerart":"KEINE","unterrichtBetragCent":150000}"""
                            .formatted(orgId, azubiEmail, schuljahr))
                    .when().post("/party/portal/azubi-anmeldung").then().statusCode(201)
                    .extract().jsonPath().getInt("anmeldungId");
            given().contentType(ContentType.JSON)
                    .when().post("/party/anmeldungen/" + anmeldungId + "/bestaetigung").then().statusCode(200);
            given().contentType(ContentType.JSON)
                    .when().post("/party/portal/anmeldungen/" + anmeldungId + "/vertrag-bestaetigen").then()
                    .statusCode(200).body("status", equalTo("AKTIV"));

            // WebUntis-Outbox-Auftrag existiert (OFFEN) für unsere Anmeldung (neben dem Suite8-Auftrag)
            int auftragId = given().when().get("/outbox?status=OFFEN").then().statusCode(200)
                    .extract().jsonPath()
                    .getInt("find { it.anmeldungId == %d && it.zielsystem == 'WEBUNTIS' }.id".formatted(anmeldungId));
            org.junit.jupiter.api.Assertions.assertTrue(auftragId > 0, "WebUntis-Outbox-Auftrag sollte angelegt sein");

            // ── WebUntis fällt aus ──
            given().when().post("/mock/webuntis/verfuegbarkeit?verfuegbar=false").then().statusCode(200);

            // Wiederholte Zustellversuche scheitern; der Auftrag bleibt OFFEN (Backoff) und wandert nach
            // MAX_VERSUCHE in den Dead-Letter. neu-versuch macht ihn vor jedem Lauf wieder fällig.
            for (int i = 0; i < 5; i++) {
                given().when().post("/outbox/" + auftragId + "/neu-versuch").then().statusCode(200);
                given().when().post("/outbox/dispatch").then().statusCode(200);
            }

            // Azubi NICHT in WebUntis; Auftrag im Dead-Letter (FEHLGESCHLAGEN)
            given().when().get("/mock/webuntis/schueler").then().statusCode(200)
                    .body("email", not(hasItem(azubiEmail)));
            given().when().get("/outbox?status=FEHLGESCHLAGEN").then().statusCode(200)
                    .body("find { it.id == %d }.versuche".formatted(auftragId), equalTo(5));

            // ── WebUntis wieder verfügbar + manueller Neuversuch (HITL) → ERLEDIGT ──
            given().when().post("/mock/webuntis/verfuegbarkeit?verfuegbar=true").then().statusCode(200);
            given().when().post("/outbox/" + auftragId + "/neu-versuch").then().statusCode(200);
            given().when().post("/outbox/dispatch").then().statusCode(200);

            given().when().get("/outbox?status=ERLEDIGT").then().statusCode(200)
                    .body("find { it.id == %d }.id".formatted(auftragId), equalTo(auftragId));
            given().when().get("/mock/webuntis/schueler").then().statusCode(200)
                    .body("email", hasItem(azubiEmail));

            // ── Idempotenz: erneuter Dispatch exportiert nicht doppelt ──
            int vorher = given().when().get("/mock/webuntis/schueler").then().statusCode(200)
                    .extract().jsonPath().getList("findAll { it.email == '%s' }".formatted(azubiEmail)).size();
            given().when().post("/outbox/dispatch").then().statusCode(200);
            int nachher = given().when().get("/mock/webuntis/schueler").then().statusCode(200)
                    .extract().jsonPath().getList("findAll { it.email == '%s' }".formatted(azubiEmail)).size();
            org.junit.jupiter.api.Assertions.assertEquals(vorher, nachher, "kein Doppel-Export (idempotent)");
        } finally {
            // Geteilten Mock-Zustand für andere Tests wiederherstellen
            given().when().post("/mock/webuntis/verfuegbarkeit?verfuegbar=true").then().statusCode(200);
        }
    }
}
