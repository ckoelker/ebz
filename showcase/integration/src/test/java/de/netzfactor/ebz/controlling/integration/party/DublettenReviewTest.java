package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Anmeldung Berufsschule — Schritt B (HITL-Dubletten-Review), <b>Fallback-Pfad</b> (KI im Test aus →
 * deterministisches {@code UNSICHER}-Urteil). Beweist:
 * <ul>
 *   <li>angefragte Firma mit gleicher USt-Id erscheint in der Review-Queue mit Merge-Vorschlag + KI-Urteil;</li>
 *   <li>menschliche Merge-Entscheidung führt die Dublette zusammen und wird auditiert (Entscheider aus Token);</li>
 *   <li>provisorische Person mit Namensdublette erscheint und kann als eigenständige Neuanlage bestätigt werden;</li>
 *   <li>entschiedene Fälle verschwinden aus der Queue.</li>
 * </ul>
 */
@QuarkusTest
@TestSecurity(user = "sb", roles = "rechnung-pflege")
class DublettenReviewTest {

    private static long uniq() {
        return System.nanoTime();
    }

    @Test
    void firmaDublette_erscheintInQueue_wirdGemergt_undAuditiert() {
        long n = uniq();
        String ust = "DE" + (n % 100000000L);

        // Bestehende, produktive Firma (Ziel)
        int ziel = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Review Bau GmbH %d","plz":"45657","ort":"Recklinghausen","land":"DE","ustId":"%s"}"""
                        .formatted(n, ust))
                .when().post("/party/organisationen").then().statusCode(201)
                .extract().jsonPath().getInt("id");

        // Self-Service-Anfrage derselben Firma (gleiche USt-Id) → ANGEFRAGT, Dubletten-Kandidat
        int kandidat = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Review-Bau GesmbH %d","plz":"45657","ort":"Recklinghausen","land":"DE","ustId":"%s",
                         "ansprechpartnerEmail":"rev+%d@bau.de","ansprechpartnerName":"Rita Review"}"""
                        .formatted(n, ust, n))
                .when().post("/party/anfragen/ausbildungsbetrieb").then().statusCode(201)
                .extract().jsonPath().getInt("organisationId");

        // Queue: unser Fall mit KI-Vorschlag (Fallback → UNSICHER, Ähnlichkeit 0.85 wg. gleichem Schlüssel)
        given().when().get("/party/reviews/queue").then().statusCode(200)
                .body("find { it.kandidatId == " + kandidat + " }.art", equalTo("FIRMA"))
                .body("find { it.kandidatId == " + kandidat + " }.vorschlaege[0].zielId", equalTo(ziel))
                .body("find { it.kandidatId == " + kandidat + " }.vorschlaege[0].einschaetzung", equalTo("UNSICHER"))
                .body("find { it.kandidatId == " + kandidat + " }.vorschlaege[0].aehnlichkeit", equalTo(0.85f));

        // HITL-Entscheidung: Merge Kandidat → Ziel; Entscheider kommt aus dem Token (sb), KI-Urteil auditiert
        given().contentType(ContentType.JSON)
                .body("""
                        {"art":"FIRMA","kandidatId":%d,"entscheidung":"GEMERGT","zielId":%d}"""
                        .formatted(kandidat, ziel))
                .when().post("/party/reviews/entscheidung").then().statusCode(200)
                .body("entscheidung", equalTo("GEMERGT"))
                .body("zielId", equalTo(ziel))
                .body("entschiedenVon", equalTo("sb"))
                .body("kiEinschaetzung", equalTo("UNSICHER"))
                .body("kiAehnlichkeit", notNullValue());

        // Dublette ist zusammengeführt …
        given().when().get("/party/organisationen/" + kandidat).then().statusCode(200)
                .body("status", equalTo("ZUSAMMENGEFUEHRT"))
                .body("goldenOrganisationId", equalTo(ziel));

        // … und nicht mehr in der Queue
        given().when().get("/party/reviews/queue").then().statusCode(200)
                .body("find { it.kandidatId == " + kandidat + " }", nullValue());
    }

    @Test
    void personDublette_erscheintInQueue_wirdAlsNeuanlageBestaetigt_undAuditiert() {
        long n = uniq();
        String name = "Dubletta Pruef " + n;

        // Bestehende, aktive Person (gleicher Name = schwacher Dublettenschlüssel)
        given().contentType(ContentType.JSON)
                .body("""
                        {"keycloakSub":"kc-dub-%d","email":"p1+%d@dublette.de","anzeigeName":"%s"}"""
                        .formatted(n, n, name))
                .when().post("/party/personen/selbstregistrieren").then().statusCode(201);

        // Provisorische Person mit demselben Namen (firmenseitig vor-angelegt)
        int org = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Dub Org GmbH %d","plz":"45657","ort":"RE","land":"DE"}""".formatted(n))
                .when().post("/party/organisationen").then().statusCode(201).extract().jsonPath().getInt("id");
        int kandidat = given().contentType(ContentType.JSON)
                .body("""
                        {"email":"p2+%d@dublette.de","anzeigeName":"%s","rolle":"AZUBI","buchungsberechtigt":false}"""
                        .formatted(n, name))
                .when().post("/party/organisationen/" + org + "/teilnehmer").then().statusCode(201)
                .extract().jsonPath().getInt("id");

        // Queue: Personenfall vorhanden
        given().when().get("/party/reviews/queue").then().statusCode(200)
                .body("find { it.kandidatId == " + kandidat + " }.art", equalTo("PERSON"));

        // HITL-Entscheidung: eigenständige Neuanlage bestätigen (kein Ziel → kein KI-Snapshot)
        given().contentType(ContentType.JSON)
                .body("""
                        {"art":"PERSON","kandidatId":%d,"entscheidung":"NEUANLAGE_BESTAETIGT"}""".formatted(kandidat))
                .when().post("/party/reviews/entscheidung").then().statusCode(200)
                .body("entscheidung", equalTo("NEUANLAGE_BESTAETIGT"))
                .body("zielId", nullValue())
                .body("kiEinschaetzung", nullValue())
                .body("entschiedenVon", equalTo("sb"));

        // Person bleibt provisorisch (claimbar) und ist aus der Queue raus
        given().when().get("/party/personen/" + kandidat).then().statusCode(200)
                .body("status", equalTo("PROVISORISCH"));
        given().when().get("/party/reviews/queue").then().statusCode(200)
                .body("find { it.kandidatId == " + kandidat + " }", nullValue());
    }
}
