package de.netzfactor.ebz.controlling.integration.lms;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;

/**
 * REST-Naht der Einschreibung (L2): RBAC + 202-Annahme der Provisionierungs-Anforderung. Die
 * eigentliche Outbox-Mechanik deckt {@link KurseinschreibungServiceTest} ab (OpenOLAT gemockt).
 */
@QuarkusTest
class EinschreibungResourceTest {

    @Inject
    FakeOpenolatProvisioning openolat;

    @BeforeEach
    void setup() {
        openolat.reset();
    }

    private Long neuerKurs() {
        return QuarkusTransaction.requiringNew().call(() -> {
            WbtKurs k = new WbtKurs();
            k.code = "WBT-ER-" + (System.nanoTime() % 1_000_000_000L);
            k.titel = "Resource-Testkurs";
            k.status = AngebotStatus.AKTIV;
            k.openolatKey = 884736L;
            k.persist();
            return k.id;
        });
    }

    @Test
    void pfadImOpenApi() {
        given().queryParam("format", "json").when().get("/q/openapi")
                .then().statusCode(200).body("paths", hasKey("/lms/einschreibungen"));
    }

    @Test
    @TestSecurity(user = "nurstaff", roles = "staff")
    void anfordernOhneKatalogPflegeRolle403() {
        String body = """
                {"keycloakSub":"%s","email":"a@ebz.de","anzeigeName":"Anna Azubi","wbtKursIds":[1]}
                """.formatted(UUID.randomUUID());
        given().contentType(ContentType.JSON).body(body)
                .when().post("/lms/einschreibungen")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void anfordernLiefert202UndAngefordert() {
        Long kursId = neuerKurs();
        String sub = UUID.randomUUID().toString();
        String body = """
                {"keycloakSub":"%s","email":"a@ebz.de","anzeigeName":"Anna Azubi","vendureOrderId":"ORD-9","wbtKursIds":[%d]}
                """.formatted(sub, kursId);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/lms/einschreibungen")
                .then().statusCode(202)
                .body("status", hasItem("ANGEFORDERT"))
                .body("keycloakSub", hasItem(sub));

        // erscheint in der Cockpit-Liste
        given().when().get("/lms/einschreibungen")
                .then().statusCode(200)
                .body("findAll { it.keycloakSub == '%s' }.status".formatted(sub), hasItem("ANGEFORDERT"));
    }

    @Test
    @TestSecurity(user = "pfleger", roles = "katalog-pflege")
    void anfordernUnbekannterKursFuehrtZuFehler() {
        String body = """
                {"keycloakSub":"%s","wbtKursIds":[999999999]}
                """.formatted(UUID.randomUUID());
        given().contentType(ContentType.JSON).body(body)
                .when().post("/lms/einschreibungen")
                .then().statusCode(equalTo(500)); // IllegalArgumentException → 500 (interner Provisionierungs-Eingang)
    }
}
