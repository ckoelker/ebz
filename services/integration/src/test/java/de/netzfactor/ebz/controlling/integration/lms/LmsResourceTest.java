package de.netzfactor.ebz.controlling.integration.lms;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * L1-Katalog-Beweis für die LMS-Anbindung: WBT-CRUD + RBAC + die MDM→Vendure-Projektions-Guards.
 * Stack-B-Spec-Test (die Bean-Validation am {@link de.netzfactor.ebz.controlling.integration.lms.dto.WbtKursDto}
 * erscheint im {@code /q/openapi} — Grundlage der orval-Generierung). Die Test-DB ist die echte
 * {@code controlling}-DB (kein Throwaway) → eindeutige Codes je Lauf.
 */
@QuarkusTest
class LmsResourceTest {

    @Test
    void wbtKursDtoConstraintsUndPfadeImOpenApi() {
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                // Bean-Validation → Schema (required / pattern / maxLength / minimum)
                .body("components.schemas.WbtKursDto.required", hasItems("code", "titel", "status"))
                .body("components.schemas.WbtKursDto.properties.code.pattern", equalTo("^[A-Z0-9-]{2,32}$"))
                .body("components.schemas.WbtKursDto.properties.titel.maxLength", equalTo(200))
                .body("components.schemas.WbtKursDto.properties.preisCent.minimum", equalTo(0))
                .body("components.schemas.WbtKursDto.properties.openolatKey.minimum", equalTo(1))
                // Pflad-Vertrag (für die SDK-Generierung)
                .body("paths", hasKey("/lms/kurse"))
                .body("paths", hasKey("/lms/kurse/{id}/veroeffentlichen"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void schreibenOhneKatalogPflegeRolleIstVerboten() {
        // RBAC: authentifiziert, aber ohne Rolle katalog-pflege → 403 (vor Validierung).
        String body = """
                {"code":"WBT-RBAC-1","titel":"RBAC-Test","status":"ENTWURF","shopVerkauf":false}
                """;
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/lms/kurse")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void crudHappyPath() {
        String code = "WBT-CRUD-" + System.currentTimeMillis() % 100000000;
        String body = """
                {"code":"%s","titel":"Golf SCORM (Seed)","kurzbeschreibung":"Testkurs",
                 "openolatKey":884736,"scormVersion":"SCORM_12","preisCent":4900,
                 "shopVerkauf":true,"status":"ENTWURF"}
                """.formatted(code);
        String location = given()
                .contentType(ContentType.JSON).body(body)
                .when().post("/lms/kurse")
                .then().statusCode(201)
                .body("code", equalTo(code))
                .body("openolatKey", equalTo(884736))
                .extract().header("Location");
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        given().when().get("/lms/kurse/" + id)
                .then().statusCode(200).body("titel", equalTo("Golf SCORM (Seed)"));

        // Soft-Delete → ARCHIVIERT (kein Hard-Delete)
        given().when().delete("/lms/kurse/" + id).then().statusCode(204);
        given().when().get("/lms/kurse/" + id).then().statusCode(200).body("status", equalTo("ARCHIVIERT"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void veroeffentlichenOhneRolleIstVerboten() {
        given().when().post("/lms/kurse/424242/veroeffentlichen").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void veroeffentlichenNichtVerkaeuflich409() {
        // Guard: nicht-verkäuflicher Kurs → 409, ohne Vendure überhaupt zu rufen.
        String code = "WBT-NS-" + System.currentTimeMillis() % 100000000;
        String body = """
                {"code":"%s","titel":"Nicht im Shop","status":"AKTIV","shopVerkauf":false,"openolatKey":884736}
                """.formatted(code);
        Long id = createId(body);
        given().when().post("/lms/kurse/" + id + "/veroeffentlichen")
                .then().statusCode(409).body(containsString("shopVerkauf"));
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void veroeffentlichenOhneOpenolatKey409() {
        // Guard: verkäuflich + AKTIV, aber nicht importiert (kein openolatKey) → 409 (nicht auslieferbar).
        String code = "WBT-NK-" + System.currentTimeMillis() % 100000000;
        String body = """
                {"code":"%s","titel":"Ohne OpenOLAT","status":"AKTIV","shopVerkauf":true,"preisCent":4900}
                """.formatted(code);
        Long id = createId(body);
        given().when().post("/lms/kurse/" + id + "/veroeffentlichen")
                .then().statusCode(409).body(containsString("openolatKey"));
    }

    private static Long createId(String body) {
        String location = given()
                .contentType(ContentType.JSON).body(body)
                .when().post("/lms/kurse")
                .then().statusCode(201)
                .extract().header("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }
}
