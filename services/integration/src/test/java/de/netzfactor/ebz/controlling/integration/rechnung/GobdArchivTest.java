package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import io.minio.GetObjectRetentionArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * GoBD-Archiv (R2, §4 Säule 4): beim Ausstellen wird das validierte ZUGFeRD revisionssicher in MinIO
 * abgelegt. Läuft gegen das laufende MinIO (localhost:9000), eigener Bucket {@code gobd-test} mit
 * GOVERNANCE-Retention (im Test bypass-/aufräumbar; produktiv COMPLIANCE = echtes WORM). Beweist:
 * Objekt vorhanden (= valides PDF) + Retention gesetzt (Object-Lock greift).
 */
@QuarkusTest
@TestProfile(GobdArchivTest.MinioProfil.class)
class GobdArchivTest {

    public static final String BUCKET = "gobd-test";

    public static class MinioProfil implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "rechnung.gobd.archiv.enabled", "true",
                    "minio.url", "http://localhost:9000",
                    "rechnung.gobd.bucket", BUCKET,
                    "rechnung.gobd.retention-mode", "GOVERNANCE",
                    "rechnung.gobd.retention-days", "1");
        }
    }

    @Inject
    MinioClient minio;

    private static String schuljahr() {
        int y = 4000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void ausstellen_archiviertValidesZugferdAlsWorm() throws Exception {
        long debitor = given().contentType(ContentType.JSON)
                .body("""
                        {"debitorNr":"BS-GOBD-%d","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"GoBD Test GmbH",
                         "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE",
                         "email":"buchhaltung@gobd-test.example"}""".formatted(System.nanoTime()))
                .when().post("/rechnung/debitoren").then().statusCode(201)
                .extract().jsonPath().getLong("id");

        String sj = schuljahr();
        given().contentType(ContentType.JSON)
                .body("""
                        {"typ":"BERUFSSCHULE","teilnehmerName":"Greta GoBD","zahlungspflichtigerDebitorId":%d,
                         "status":"AKTIV","schuljahr":"%s","halbjahr":1,"zimmerart":"DOPPEL",
                         "unterrichtBetragCent":150000,"uebernachtungBetragCent":130000}""".formatted(debitor, sj))
                .when().post("/rechnung/anmeldungen").then().statusCode(201);

        io.restassured.path.json.JsonPath lauf = given().contentType(ContentType.JSON)
                .body("""
                        {"schuljahr":"%s","halbjahr":1}""".formatted(sj))
                .when().post("/rechnung/laeufe").then().statusCode(200).extract().jsonPath();
        int idx = lauf.getList("debitorId").indexOf((int) debitor);
        long rid = lauf.getList("id", Long.class).get(idx);

        // Ausstellen = Festschreibung + GoBD-Archivierung in einer Transaktion
        String nummer = given().when().post("/rechnung/rechnungen/" + rid + "/ausstellen").then()
                .statusCode(200).extract().jsonPath().getString("nummer");

        String key = "berufsschule/" + Year.now() + "/" + nummer + ".pdf";

        StatObjectResponse stat = minio.statObject(StatObjectArgs.builder()
                .bucket(BUCKET).object(key).build());
        assertTrue(stat.size() > 3000, "archiviertes ZUGFeRD-PDF nicht leer: " + key);
        assertEquals("application/pdf", stat.contentType(), "Content-Type application/pdf erwartet");

        Retention ret = minio.getObjectRetention(GetObjectRetentionArgs.builder()
                .bucket(BUCKET).object(key).build());
        assertEquals(RetentionMode.GOVERNANCE, ret.mode(), "WORM-Retention muss gesetzt sein");
        assertTrue(ret.retainUntilDate().isAfter(java.time.ZonedDateTime.now()), "Retention in der Zukunft");
    }
}
