package de.netzfactor.ebz.controlling.integration.shop;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

/**
 * Live-Test des CRM→Vendure-Personen-Syncs (P7b) gegen das laufende Vendure. Projiziert aktive
 * EBZ-Mitarbeiter als Vendure-Ansprechpartner (idempotent). Ohne erreichbares Vendure übersprungen.
 */
@QuarkusTest
class CrmVendureSyncResourceTest {

    @BeforeEach
    void vendureErreichbar() {
        boolean up = false;
        try {
            HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(HttpRequest.newBuilder(URI.create("http://localhost:3000/admin-api"))
                            .timeout(Duration.ofSeconds(2))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{__typename}\"}"))
                            .header("content-type", "application/json").build(),
                            HttpResponse.BodyHandlers.ofString());
            up = r.statusCode() == 200;
        } catch (Exception e) {
            up = false;
        }
        Assumptions.assumeTrue(up, "Vendure (localhost:3000) nicht erreichbar — Live-Test übersprungen");
    }

    @Test
    @TestSecurity(user = "sb", roles = "katalog-pflege")
    void sync_liefertGesendetCount() {
        given().contentType("application/json")
                .when().post("/shop/crm-personen-sync")
                .then().statusCode(200)
                .body("gesendet", greaterThanOrEqualTo(0));
    }

    @Test
    void sync_ohneRolle_abgewiesen() {
        given().contentType("application/json")
                .when().post("/shop/crm-personen-sync")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(401), equalTo(403)));
    }
}
