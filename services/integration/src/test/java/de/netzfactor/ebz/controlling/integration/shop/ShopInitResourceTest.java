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
 * Live-Integrationstest des Shop-Initializers (Produktkatalog P1, §B) gegen das laufende Vendure
 * ({@code localhost:3000}). Initialisiert den Shop real und prüft Idempotenz: der zweite Lauf legt
 * nichts Neues an ({@code angelegt == 0}). Ist Vendure nicht erreichbar (z. B. CI ohne Stack), wird
 * der Test übersprungen (Assumption) statt hart zu scheitern.
 */
@QuarkusTest
class ShopInitResourceTest {

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
    void init_legtAn_dannIdempotent() {
        // 1. Lauf: initialisiert (gegen evtl. bereits geseedeten Stand idempotent).
        given().contentType("application/json")
                .when().post("/shop/init")
                .then().statusCode(200)
                .body("uebersprungen", greaterThanOrEqualTo(0));

        // 2. Lauf: nichts Neues mehr anzulegen.
        given().contentType("application/json")
                .when().post("/shop/init")
                .then().statusCode(200)
                .body("angelegt", equalTo(0));
    }

    @Test
    void init_ohneRolle_abgewiesen() {
        given().contentType("application/json")
                .when().post("/shop/init")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(401), equalTo(403)));
    }
}
