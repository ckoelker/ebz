package de.netzfactor.ebz.controlling.integration.rechnung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Stack-B-Beweis für die Rechnungs-API: die Bean-Validation der DTOs spiegelt sich ins
 * {@code /q/openapi}-Schema (Quelle der SDK-/zod-Generierung), und die Cross-Field-Regel der
 * Berufsschul-Anmeldung bleibt server-seitig (NICHT im Schema). Außerdem: die Billing-Pfade existieren.
 */
@QuarkusTest
class RechnungOpenApiSpecTest {

    @Test
    void billingPfadeImSchemaVorhanden() {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("paths", Matchers.hasKey("/rechnung/debitoren"))
                .body("paths", Matchers.hasKey("/rechnung/anmeldungen"))
                .body("paths", Matchers.hasKey("/rechnung/laeufe"))
                .body("paths", Matchers.hasKey("/rechnung/rechnungen"))
                .body("paths", Matchers.hasKey("/rechnung/rechnungen/{id}/ausstellen"))
                .body("paths", Matchers.hasKey("/rechnung/rechnungen/{id}/storno"))
                .body("paths", Matchers.hasKey("/rechnung/rechnungen/{id}/gutschrift"));
    }

    @Test
    void debitorConstraintsImSchema() {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("components.schemas.DebitorDto.required", hasItems("debitorNr", "bereich", "rolle", "name"))
                .body("components.schemas.DebitorDto.properties.debitorNr.pattern", equalTo("^[A-Z0-9-]{2,32}$"));
    }

    @Test
    void anmeldungFeldConstraintsImSchema_crossFieldNicht() {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                // Feld-Constraints (required-Kern) erscheinen
                .body("components.schemas.AnmeldungDto.required",
                        hasItems("typ", "teilnehmerName", "zahlungspflichtigerDebitorId", "status"))
                .body("components.schemas.AnmeldungDto.properties.schuljahr.pattern", equalTo("^\\d{4}/\\d{4}$"))
                // Cross-Field: schuljahr/zimmerart/unterrichtBetragCent sind NICHT required im Schema
                // (Pflicht nur für BERUFSSCHULE, server-seitig über @StimmigeBerufsschulAnmeldung)
                .body("components.schemas.AnmeldungDto.required",
                        not(hasItems("schuljahr", "zimmerart", "unterrichtBetragCent", "uebernachtungBetragCent")));
    }

    @Test
    void rechnungslaufRequestConstraintsImSchema() {
        given().queryParam("format", "json").when().get("/q/openapi").then().statusCode(200)
                .body("components.schemas.RechnungslaufRequest.required", hasItems("schuljahr", "halbjahr"))
                .body("components.schemas.RechnungslaufRequest.properties.halbjahr.minimum", equalTo(1))
                .body("components.schemas.RechnungslaufRequest.properties.halbjahr.maximum", equalTo(2))
                // kein abgeleitetes Vergleichs-Constraint auf einem simplen Feld
                .body("components.schemas.AnmeldungDto.properties.unterrichtBetragCent.exclusiveMinimum", nullValue());
    }
}
