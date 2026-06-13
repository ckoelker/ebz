package de.netzfactor.ebz.controlling.integration.bildung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

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
        // §11.2: per-Typ-API statt oneOf — die flachen Seminar-Pfade + die Registry existieren.
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/seminare"))
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/seminare/{id}"))
                .body("paths", org.hamcrest.Matchers.hasKey("/bildung/angebote"));
    }
}
