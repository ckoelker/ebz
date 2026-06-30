package de.netzfactor.ebz.controlling.integration.mandant;

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
 * M1-Beweis der Mandanten-Verwaltung: Mandant-CRUD + RBAC + die B2B-Bausteine IdP-Föderation und
 * Seat-Lizenz, plus der Stack-B-Spec-Vertrag (Bean-Validation am {@code MandantDto} erscheint im
 * {@code /q/openapi} → orval). Die Test-DB ist die echte {@code controlling}-DB (kein Throwaway) →
 * eindeutige Schlüssel je Lauf.
 */
@QuarkusTest
class MandantResourceTest {

    @Test
    void mandantDtoConstraintsUndPfadeImOpenApi() {
        given()
                .queryParam("format", "json")
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body("components.schemas.MandantDto.required", hasItems("schluessel", "anzeigeName", "vertragstyp"))
                .body("components.schemas.MandantDto.properties.schluessel.pattern", equalTo("^[A-Z0-9_-]{2,40}$"))
                .body("components.schemas.MandantDto.properties.anzeigeName.maxLength", equalTo(200))
                .body("paths", hasKey("/mandant"))
                .body("paths", hasKey("/mandant/{id}/foederationen"))
                .body("paths", hasKey("/mandant/{id}/lizenzen"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void schreibenOhneMandantPflegeRolleIstVerboten() {
        String body = """
                {"schluessel":"RBAC-1","anzeigeName":"RBAC-Test","vertragstyp":"ENTERPRISE_FLAT"}
                """;
        given().contentType(ContentType.JSON).body(body)
                .when().post("/mandant")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "mandant-pflege")
    void crudHappyPath() {
        String schluessel = "DEMO-" + System.currentTimeMillis() % 100000000;
        String body = """
                {"schluessel":"%s","anzeigeName":"Demo AG","vertragstyp":"ENTERPRISE_FLAT",
                 "primaerFarbe":"#E2001A","sekundaerFarbe":"#1A1A1A"}
                """.formatted(schluessel);
        String location = given()
                .contentType(ContentType.JSON).body(body)
                .when().post("/mandant")
                .then().statusCode(201)
                .body("schluessel", equalTo(schluessel))
                .body("status", equalTo("ENTWURF"))
                .body("primaerFarbe", equalTo("#E2001A"))
                .extract().header("Location");
        Long id = idAus(location);

        given().when().get("/mandant/" + id)
                .then().statusCode(200).body("anzeigeName", equalTo("Demo AG"));

        // Update: aktiv schalten (mit version=0 → Optimistic-Lock-Match)
        String upd = """
                {"version":0,"schluessel":"%s","anzeigeName":"Demo AG","vertragstyp":"ENTERPRISE_FLAT","status":"AKTIV"}
                """.formatted(schluessel);
        given().contentType(ContentType.JSON).body(upd)
                .when().put("/mandant/" + id)
                .then().statusCode(200).body("status", equalTo("AKTIV"));

        // Soft-Delete → BEENDET (kein Hard-Delete)
        given().when().delete("/mandant/" + id).then().statusCode(204);
        given().when().get("/mandant/" + id).then().statusCode(200).body("status", equalTo("BEENDET"));
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "mandant-pflege")
    void doppelterSchluesselIstKonflikt() {
        String schluessel = "DUP-" + System.currentTimeMillis() % 100000000;
        String body = """
                {"schluessel":"%s","anzeigeName":"Erst","vertragstyp":"EBZ_CUSTOMER"}
                """.formatted(schluessel);
        given().contentType(ContentType.JSON).body(body).when().post("/mandant").then().statusCode(201);
        given().contentType(ContentType.JSON).body(body).when().post("/mandant")
                .then().statusCode(409).body("message", containsString(schluessel));
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "mandant-pflege")
    void foederationAnlegenUndListen() {
        String schluessel = "FED-" + System.currentTimeMillis() % 100000000;
        Long id = idAus(create("""
                {"schluessel":"%s","anzeigeName":"Föderierter Kunde","vertragstyp":"ENTERPRISE_FLAT"}
                """.formatted(schluessel)));
        String fed = """
                {"idpAlias":"kunde-oidc","emailDomains":"kunde.de;kunde.com","protokoll":"OIDC"}
                """;
        given().contentType(ContentType.JSON).body(fed)
                .when().post("/mandant/" + id + "/foederationen")
                .then().statusCode(201).body("idpAlias", equalTo("kunde-oidc"))
                .body("status", equalTo("ENTWURF"));

        given().when().get("/mandant/" + id + "/foederationen")
                .then().statusCode(200).body("idpAlias", hasItems("kunde-oidc"));
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "mandant-pflege")
    void seatLizenzNurFuerEnterpriseFlat() {
        // EBZ-Kernmandant (B2C) → kein Seat-Limit → 409
        String b2c = "B2C-" + System.currentTimeMillis() % 100000000;
        Long b2cId = idAus(create("""
                {"schluessel":"%s","anzeigeName":"EBZ Customer","vertragstyp":"EBZ_CUSTOMER"}
                """.formatted(b2c)));
        String lizenz = """
                {"seatLimit":25,"gueltigVon":"2026-01-01","aktiv":true}
                """;
        given().contentType(ContentType.JSON).body(lizenz)
                .when().post("/mandant/" + b2cId + "/lizenzen")
                .then().statusCode(409).body("message", containsString("ENTERPRISE_FLAT"));

        // ENTERPRISE_FLAT → Lizenz wird angelegt
        String b2b = "FLAT-" + System.currentTimeMillis() % 100000000;
        Long b2bId = idAus(create("""
                {"schluessel":"%s","anzeigeName":"Flatrate AG","vertragstyp":"ENTERPRISE_FLAT"}
                """.formatted(b2b)));
        given().contentType(ContentType.JSON).body(lizenz)
                .when().post("/mandant/" + b2bId + "/lizenzen")
                .then().statusCode(201).body("seatLimit", equalTo(25));
    }

    // ── Helfer (aufgerufen aus Tests, die bereits die Rolle mandant-pflege tragen) ──
    private static String create(String body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/mandant")
                .then().statusCode(201).extract().header("Location");
    }

    private static Long idAus(String location) {
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }
}
