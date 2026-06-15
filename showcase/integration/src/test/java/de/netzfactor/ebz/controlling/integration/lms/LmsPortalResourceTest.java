package de.netzfactor.ebz.controlling.integration.lms;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.EinschreibungStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;

/**
 * Portal-Sicht „Meine Trainings": Eigen-Skopierung über den Token-{@code sub} und der SSO-Launch-Deeplink
 * (nur für eingeschriebene Kurse). Token via {@code @TestSecurity} (OIDC im Test-Profil aus); der
 * Principal-Name = {@code sub}, genau wie der Rechnungsabruf ihn nutzt.
 */
@QuarkusTest
class LmsPortalResourceTest {

    // Feste Sub-Literale (für @TestSecurity nötig: Konstantenausdruck). Die persistente Test-DB wird je
    // Test über @BeforeEach für genau diese Subs bereinigt → deterministische size()-Erwartungen.
    private static final String SUB_A = "portal-test-sub-a";
    private static final String SUB_B = "portal-test-sub-b";
    private static final String SUB_LEER = "portal-test-sub-leer";

    @BeforeEach
    void cleanup() {
        QuarkusTransaction.requiringNew()
                .run(() -> Kurseinschreibung.delete("keycloakSub in ?1", List.of(SUB_A, SUB_B, SUB_LEER)));
    }

    private Long neuerKurs() {
        return QuarkusTransaction.requiringNew().call(() -> {
            WbtKurs k = new WbtKurs();
            k.code = "WBT-P-" + (System.nanoTime() % 1_000_000_000L);
            k.titel = "Portal-Testkurs";
            k.status = AngebotStatus.AKTIV;
            k.openolatKey = 884736L;
            k.persist();
            return k.id;
        });
    }

    private void einschreibung(Long kursId, String sub, EinschreibungStatus status) {
        QuarkusTransaction.requiringNew().run(() -> {
            Kurseinschreibung e = new Kurseinschreibung();
            e.wbtKurs = WbtKurs.findById(kursId);
            e.keycloakSub = sub;
            e.email = sub + "@ebz.de";
            e.status = status;
            e.versuche = 0;
            e.erstelltAm = Instant.now();
            e.naechsterVersuchAm = Instant.now();
            if (status == EinschreibungStatus.EINGESCHRIEBEN) {
                e.openolatIdentityKey = 458752L;
            }
            e.persist();
        });
    }

    @Test
    void anonymOhneLogin401() {
        given().when().get("/lms/portal/trainings").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = SUB_A)
    @JwtSecurity(claims = @Claim(key = "sub", value = SUB_A))
    void nurEigeneTrainingsMitLaunchUrlNurWennEingeschrieben() {
        Long kursEin = neuerKurs();
        Long kursOffen = neuerKurs();
        Long kursFremd = neuerKurs();
        einschreibung(kursEin, SUB_A, EinschreibungStatus.EINGESCHRIEBEN);
        einschreibung(kursOffen, SUB_A, EinschreibungStatus.ANGEFORDERT);
        einschreibung(kursFremd, SUB_B, EinschreibungStatus.EINGESCHRIEBEN); // fremd → unsichtbar

        given().when().get("/lms/portal/trainings")
                .then().statusCode(200)
                // genau die zwei EIGENEN (SUB_A ist pro Lauf eindeutig) → Fremdeinschreibung SUB_B ausgeschlossen
                .body("size()", equalTo(2))
                .body("status", hasItem("EINGESCHRIEBEN"))
                .body("status", hasItem("ANGEFORDERT"))
                // Launch-URL nur für das eingeschriebene (Jump-in-Deeplink)
                .body("find { it.status == 'EINGESCHRIEBEN' }.launchUrl", containsString("/url/RepositoryEntry/884736"))
                .body("find { it.status == 'ANGEFORDERT' }.launchUrl", nullValue());
    }

    @Test
    @TestSecurity(user = SUB_LEER)
    @JwtSecurity(claims = @Claim(key = "sub", value = SUB_LEER))
    void ohneEinschreibungenLeereListe() {
        given().when().get("/lms/portal/trainings")
                .then().statusCode(200).body("$", empty());
    }
}
