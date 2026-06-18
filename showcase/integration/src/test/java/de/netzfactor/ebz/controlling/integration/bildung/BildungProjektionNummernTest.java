package de.netzfactor.ebz.controlling.integration.bildung;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;
import de.netzfactor.ebz.controlling.integration.bildung.model.PreisModell;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

/**
 * Live-Test der reduzierten Shop-Verknüpfung (Produktkatalog P1, §C): Vendure ist SoR des Katalogs,
 * der MDM-Kern verknüpft nur per <b>Nummer</b>. Setzt voraus, dass der Shop initialisiert ist
 * ({@code POST /shop/init} → Produkt mit {@code angebotsnummer=SVA015729}); ist Vendure nicht
 * erreichbar, wird übersprungen.
 */
@QuarkusTest
class BildungProjektionNummernTest {

    private static final String MATCH = "SVA015729";
    private static final String NOMATCH = "ZZ-NICHTDA-999";

    private Long idMatch;
    private Long idNoMatch;

    @BeforeEach
    void setup() {
        boolean up;
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

        QuarkusTransaction.requiringNew().run(() -> {
            Bildungsangebot.delete("code in ?1", java.util.List.of(MATCH, NOMATCH));
            idMatch = persist(MATCH);
            idNoMatch = persist(NOMATCH);
        });
    }

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Bildungsangebot.delete("code in ?1", java.util.List.of(MATCH, NOMATCH)));
    }

    private static Long persist(String code) {
        Bildungsangebot e = new Bildungsangebot();
        e.typ = BildungsangebotTyp.SEMINAR;
        e.code = code;
        e.titel = "Test " + code;
        e.bereich = Bereich.AKADEMIE;
        e.status = AngebotStatus.AKTIV;
        e.gueltigAb = LocalDate.now();
        e.preisModell = PreisModell.EINMALIG;
        e.shopVerkauf = true;
        e.persist();
        return e.id;
    }

    @Test
    @TestSecurity(user = "sb", roles = "katalog-pflege")
    void bekannteNummer_verknuepft() {
        given().when().post("/bildung/angebote/" + idMatch + "/shop-projektion")
                .then().statusCode(200)
                .body("vendureProductId", notNullValue());
    }

    @Test
    @TestSecurity(user = "sb", roles = "katalog-pflege")
    void unbekannteNummer_409() {
        given().when().post("/bildung/angebote/" + idNoMatch + "/shop-projektion")
                .then().statusCode(409);
    }
}
