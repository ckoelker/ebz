package de.netzfactor.ebz.controlling.integration.bildung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Spec-Test 1 (Plan §5, Krit. 2 „über die Spezifikation testbar").
 * <p>
 * Beweist, dass die EINZIGE Validierungsquelle — die Bean-Validation am {@code SeminarDto} —
 * tatsächlich ins {@code /q/openapi}-JSON-Schema gespiegelt wird (required / pattern / maxLength /
 * minimum). Das ist die Garantie, auf der die nachgelagerte Generierung (@hey-api/openapi-ts → zod)
 * fußt: fehlt hier eine Annotation, fällt sie auf, bevor sie still aus Maske + zod verschwindet.
 */
@QuarkusTest
class BildungOpenApiSpecTest {

    @Test
    void seminarDtoConstraintsErscheinenImOpenApiSchema() {
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                // @NotNull/@NotBlank → required
                .body("components.schemas.SeminarDto.required",
                        hasItems("code", "titel", "bereich", "status", "gueltigAb", "preisModell", "kategorie"))
                // @Pattern → pattern
                .body("components.schemas.SeminarDto.properties.code.pattern", equalTo("^[A-Z0-9-]{2,32}$"))
                // @Size(max=...) → maxLength
                .body("components.schemas.SeminarDto.properties.titel.maxLength", equalTo(200))
                .body("components.schemas.SeminarDto.properties.kurzbeschreibung.maxLength", equalTo(2000))
                // @Min(...) → minimum
                .body("components.schemas.SeminarDto.properties.dauerUE.minimum", equalTo(1))
                .body("components.schemas.SeminarDto.properties.maxTN.minimum", equalTo(1));
    }

    @Test
    void perTypEndpunkteSindFlachVorhanden() {
        // §11.2: per-Typ-API statt oneOf — ALLE vier Subtyp-Pfade + die Registry existieren (P1.1-Kernbeweis).
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/seminare"))
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/tagungen"))
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/berufsschuljahre"))
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/studiengaenge"))
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/angebote"));
    }

    @Test
    void subtypSchemasTragenTypSpezifischeConstraints() {
        // Jeder Subtyp ist ein eigenes FLACHES Schema mit seinen Constraints (kein oneOf/Vererbung im Schema).
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                // Tagung: programmUrl-Pattern + maxTN minimum
                .body("components.schemas.TagungDto.properties.programmUrl.pattern", equalTo("^https?://.+"))
                .body("components.schemas.TagungDto.required", hasItems("thema", "terminVon"))
                // Berufsschuljahr: schuljahr-Format JJJJ/JJ
                .body("components.schemas.BerufsschuljahrDto.properties.schuljahr.pattern", equalTo("^\\d{4}/\\d{2}$"))
                // Studiengang: startsemester-Format WS/SS+Jahr
                .body("components.schemas.StudiengangDto.properties.startsemester.pattern", equalTo("^(WS|SS)\\d{4}$"))
                .body("components.schemas.StudiengangDto.required", hasItems("abschluss", "studienform", "startsemester"));
    }

    @Test
    void crossFieldGueltigerZeitraumIstServerSeitigNichtImSchema() {
        // Die Cross-Field-Regel (gueltigBis ≥ gueltigAb, §11.9-D/F14) darf NICHT als Feld-Constraint
        // im JSON-Schema stehen — sie ist eine Klassen-Regel und lebt server-seitig.
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                // gueltigBis bleibt ein simples Datumsfeld ohne abgeleitete Min-/Vergleichs-Constraint
                .body("components.schemas.SeminarDto.properties.gueltigBis.minimum", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void crossFieldVerletzungLiefert400MitGueltigBis() {
        // Ansonsten valides Seminar, nur gueltigBis VOR gueltigAb → 400 aus dem Klassen-Validator,
        // Verletzung am Feld gueltigBis (für die Cockpit-Anzeige). @TestSecurity liefert die RBAC-Rolle.
        String body = """
                {"code":"SEM-CF-001","titel":"Cross-Field-Test","bereich":"AKADEMIE","status":"ENTWURF",
                 "gueltigAb":"2026-09-01","gueltigBis":"2026-08-01","preisModell":"EINMALIG",
                 "shopVerkauf":false,"kategorie":"SONSTIGE","dauerUE":8,"minTN":1,"maxTN":10}
                """;
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/bildung/seminare")
                .then()
                .statusCode(400)
                .body(containsString("gueltigBis"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void schreibenOhneKatalogPflegeRolleIstVerboten() {
        // RBAC (§11.5/F6): valides Seminar, aber ohne Rolle katalog-pflege → 403 (vor Validierung).
        String body = """
                {"code":"SEM-RBAC-1","titel":"RBAC-Test","bereich":"AKADEMIE","status":"ENTWURF",
                 "gueltigAb":"2026-09-01","preisModell":"EINMALIG","shopVerkauf":false,
                 "kategorie":"SONSTIGE","dauerUE":8,"minTN":1,"maxTN":10}
                """;
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/bildung/seminare")
                .then()
                .statusCode(403);
    }

    @Test
    void shopProjektionEndpunktExistiertImOpenApiSchema() {
        // P1.3-Naht (§11.6): der Projektions-Endpunkt ist Teil des Vertrags (für die SDK-Generierung).
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/angebote/{id}/shop-projektion"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void shopProjektionOhneKatalogPflegeRolleIstVerboten() {
        // RBAC vor Geschäftslogik: ohne Rolle 403 — der Vendure-Pfad wird gar nicht erst betreten.
        given()
                .when().post("/bildung/angebote/424242/shop-projektion")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void shopProjektionNurFuerVerkaeuflicheAktiveAngebote() {
        // Guard (§11.6): ein nicht-verkäufliches Angebot → 409, ohne Vendure überhaupt zu rufen.
        // Eindeutiger Code je Lauf — die Test-DB ist die echte `controlling`-DB (kein Throwaway).
        String code = "SEM-PROJ-" + System.currentTimeMillis();
        String body = """
                {"code":"%s","titel":"Nicht im Shop","bereich":"AKADEMIE","status":"AKTIV",
                 "gueltigAb":"2026-09-01","preisModell":"EINMALIG","shopVerkauf":false,
                 "kategorie":"SONSTIGE","dauerUE":8,"minTN":1,"maxTN":10}
                """.formatted(code);
        String location = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/bildung/seminare")
                .then()
                .statusCode(201)
                .extract().header("Location");
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        given()
                .when().post("/bildung/angebote/" + id + "/shop-projektion")
                .then()
                .statusCode(409)
                .body(containsString("shopVerkauf"));
    }
}
