package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * End-to-end-Beweis des Berufsschul-Belegflusses (R1): Stammdaten → Rechnungslauf (Sammelrechnung je
 * Debitor, 1–2 Positionen je Anmeldung) → Festschreibung (lückenlose Nummer, danach unveränderbar) →
 * Storno (Korrekturbeleg). Plus Cross-Field- und RBAC-Tor.
 * <p>
 * Die Test-DB ist die echte {@code controlling}-DB (kein Throwaway): je Lauf einzigartiger Debitor +
 * Schuljahr → der Rechnungslauf isoliert sich auf die hier angelegten Anmeldungen.
 */
@QuarkusTest
class RechnungLebenszyklusTest {

    /** Einzigartiges, gültiges Schuljahr (^\d{4}/\d{4}$) je Lauf → isoliert den Rechnungslauf. */
    private static String eindeutigesSchuljahr() {
        int y = 3000 + (int) (System.nanoTime() % 900);
        return y + "/" + (y + 1);
    }

    private static String eindeutigeDebitorNr() {
        return "BS-DEB-" + System.nanoTime();
    }

    /** Läuft im Security-Kontext der aufrufenden @Test-Methode (die die Rolle rechnung-pflege trägt). */
    private long createDebitor() {
        String body = """
                {"debitorNr":"%s","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"Muster Ausbildungs GmbH",
                 "strasse":"Hauptstr. 1","plz":"45657","ort":"Recklinghausen","land":"DE"}
                """.formatted(eindeutigeDebitorNr());
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/debitoren")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private void createBsAnmeldung(long debitorId, String schuljahr, String name, String zimmerart,
            int unterrichtCent, Integer uebernachtungCent) {
        String ueb = uebernachtungCent == null ? "" : ",\"uebernachtungBetragCent\":" + uebernachtungCent;
        String body = """
                {"typ":"BERUFSSCHULE","teilnehmerName":"%s","zahlungspflichtigerDebitorId":%d,"status":"AKTIV",
                 "schuljahr":"%s","halbjahr":2,"zimmerart":"%s","unterrichtBetragCent":%d%s}
                """.formatted(name, debitorId, schuljahr, zimmerart, unterrichtCent, ueb);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/anmeldungen")
                .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void berufsschulLauf_festschreibung_storno() {
        long debitor = createDebitor();
        String schuljahr = eindeutigesSchuljahr();
        // Teilnehmer A: Doppelzimmer (2 Positionen) · Teilnehmer B: ohne Übernachtung (1 Position)
        createBsAnmeldung(debitor, schuljahr, "Anna Azubi", "DOPPEL", 150000, 130000);
        createBsAnmeldung(debitor, schuljahr, "Ben Azubi", "KEINE", 150000, null);

        // ── Rechnungslauf: eine Sammelrechnung für diesen Debitor, 3 Positionen, Entwurf ohne Nummer ──
        String body = """
                {"schuljahr":"%s","halbjahr":2}""".formatted(schuljahr);
        io.restassured.path.json.JsonPath lauf = given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/laeufe")
                .then().statusCode(200).extract().jsonPath();

        // genau eine Rechnung für unseren Debitor (Schuljahr ist eindeutig)
        int idx = lauf.getList("debitorId").indexOf((int) debitor);
        org.junit.jupiter.api.Assertions.assertTrue(idx >= 0, "Sammelrechnung für Debitor erwartet");
        long rechnungId = lauf.getList("id", Long.class).get(idx);

        given().when().get("/rechnung/rechnungen/" + rechnungId).then().statusCode(200)
                .body("status", equalTo("ENTWURF"))
                .body("belegart", equalTo("RECHNUNG"))
                .body("nummer", nullValue())
                .body("positionen.size()", equalTo(3))
                .body("summeCent", equalTo(150000 + 130000 + 150000)); // 430.000 Cent

        // ── Festschreibung: lückenlose Nummer, Status AUSGESTELLT, Datum gesetzt ──
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen").then().statusCode(200)
                .body("status", equalTo("AUSGESTELLT"))
                .body("nummer", startsWith("RE-BS-"))
                .body("ausstellungsdatum", notNullValue());

        // erneutes Ausstellen → 409 (idempotenter Schutz)
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen").then().statusCode(409);

        // Position nach Festschreibung ergänzen → 409 (unveränderbar)
        String pos = """
                {"beschreibung":"Nachtrag","menge":1,"einzelbetragCent":1000,"steuerfall":"BEFREIT",
                 "steuersatz":0,"leistungsart":"SONSTIGE"}
                """;
        given().contentType(ContentType.JSON).body(pos)
                .when().post("/rechnung/rechnungen/" + rechnungId + "/positionen").then().statusCode(409);

        // ── Storno: eigener Korrekturbeleg mit Bezug, gespiegelte Summe; Original wird STORNIERT ──
        given().when().post("/rechnung/rechnungen/" + rechnungId + "/storno").then().statusCode(200)
                .body("belegart", equalTo("STORNO"))
                .body("nummer", startsWith("ST-BS-"))
                .body("originalRechnungId", equalTo((int) rechnungId))
                .body("summeCent", equalTo(-(150000 + 130000 + 150000)));

        given().when().get("/rechnung/rechnungen/" + rechnungId).then().statusCode(200)
                .body("status", equalTo("STORNIERT"));
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void manuellePosition_dann_nachberechnung() {
        long debitor = createDebitor();
        String schuljahr = eindeutigesSchuljahr();
        createBsAnmeldung(debitor, schuljahr, "Carla Azubi", "EINZEL", 150000, 126000);

        io.restassured.path.json.JsonPath lauf = given().contentType(ContentType.JSON)
                .body("""
                        {"schuljahr":"%s","halbjahr":2}""".formatted(schuljahr))
                .when().post("/rechnung/laeufe").then().statusCode(200)
                .extract().jsonPath();
        // genau die Rechnung dieses Debitors wählen (robust gegen Schuljahr-Kollisionen in der Shared-DB)
        int idx = lauf.getList("debitorId").indexOf((int) debitor);
        org.junit.jupiter.api.Assertions.assertTrue(idx >= 0, "Sammelrechnung für Debitor erwartet");
        long rechnungId = lauf.getList("id", Long.class).get(idx);

        // manuelle Position im Entwurf → 3. Position
        given().contentType(ContentType.JSON)
                .body("""
                        {"beschreibung":"Materialpauschale","menge":1,"einzelbetragCent":5000,
                         "steuerfall":"BEFREIT","steuersatz":0,"leistungsart":"SONSTIGE"}""")
                .when().post("/rechnung/rechnungen/" + rechnungId + "/positionen").then().statusCode(200)
                .body("positionen.size()", equalTo(3))
                .body("positionen[2].herkunft", equalTo("MANUELL"));

        given().when().post("/rechnung/rechnungen/" + rechnungId + "/ausstellen").then().statusCode(200);

        // Nachberechnung (positiver Korrekturbeleg) mit Bezug aufs Original
        given().contentType(ContentType.JSON)
                .body("""
                        {"grund":"Block B Übernachtung","positionen":[
                          {"beschreibung":"Übernachtung Block B","menge":1,"einzelbetragCent":40000,
                           "steuerfall":"BEFREIT","steuersatz":0,"leistungsart":"UEBERNACHTUNG"}]}""")
                .when().post("/rechnung/rechnungen/" + rechnungId + "/nachberechnung").then().statusCode(200)
                .body("belegart", equalTo("NACHBERECHNUNG"))
                .body("nummer", startsWith("NB-BS-"))
                .body("originalRechnungId", equalTo((int) rechnungId))
                .body("summeCent", equalTo(40000));
    }

    @Test
    @TestSecurity(user = "sb", roles = "rechnung-pflege")
    void crossFieldUebernachtungOhneBetrag_liefert400() {
        long debitor = createDebitor();
        // Doppelzimmer, aber uebernachtungBetragCent fehlt → 400 am Feld uebernachtungBetragCent
        String body = """
                {"typ":"BERUFSSCHULE","teilnehmerName":"Dora","zahlungspflichtigerDebitorId":%d,"status":"AKTIV",
                 "schuljahr":"3999/4000","halbjahr":1,"zimmerart":"DOPPEL","unterrichtBetragCent":150000}
                """.formatted(debitor);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/anmeldungen").then().statusCode(400)
                .body(containsString("uebernachtungBetragCent"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void schreibenOhneRechnungPflegeRolle_istVerboten() {
        String body = """
                {"debitorNr":"BS-DEB-RBAC","bereich":"BERUFSSCHULE","rolle":"FIRMA","name":"X"}
                """;
        given().contentType(ContentType.JSON).body(body)
                .when().post("/rechnung/debitoren").then().statusCode(403);
    }

    @Test
    void lesenOffen_ausstellenOhneRolleVerboten() {
        // Lese-Liste ist offen (kein Token nötig) ...
        given().when().get("/rechnung/rechnungen").then().statusCode(200);
        // ... eine Schreib-/Lebenszyklus-Op ohne Token/Rolle aber 401/403
        given().when().post("/rechnung/rechnungen/424242/ausstellen").then()
                .statusCode(greaterThanOrEqualTo(401));
    }
}
