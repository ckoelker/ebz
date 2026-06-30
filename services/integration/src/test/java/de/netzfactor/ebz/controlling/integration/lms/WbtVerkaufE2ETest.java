package de.netzfactor.ebz.controlling.integration.lms;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.service.KurseinschreibungService;
import de.netzfactor.ebz.controlling.integration.lms.vendure.WbtVendureProjektion;

/**
 * <b>End-to-End-Durchstich „WBT-Verkauf (Shop → OpenOLAT)" als Living-Documentation-Quelle.</b>
 * <p>
 * Zweites Verfahren neben {@code AnmeldungBerufsschuleE2ETest}: jede Testmethode spielt ein vollständiges
 * WBT-Szenario durch und trägt über den W3C-{@code baggage}-Header die Fall-Korrelation
 * ({@code prozess.fall}) — daraus stempeln die im Service-/Resource-Code gesetzten {@code Prozessspur}-
 * Spans (Verfahren {@code WBT_VERKAUF}) ihre Case-Id. Der {@code SpanLogExporter} schreibt sie nach
 * {@code target/prozess-log/spans.jsonl}; der Generator ({@code showcase/prozessdoku/}) macht daraus eine
 * eigene Übersicht/Gesamt-Sicht für dieses Verfahren.
 * <p>
 * Phasen: Katalog &amp; Shop-Listung → Kauf im Shop → Auslieferung in OpenOLAT → Training nutzen.
 * OpenOLAT ist global per {@link FakeOpenolatProvisioning} gemockt; Vendure wird je Test per
 * {@link QuarkusMock} durch {@link FakeVendure} ersetzt (kein laufender Shop nötig). Die asynchrone
 * Auslieferung treibt der Test über den injizierten {@link KurseinschreibungService} (der Span trägt den
 * beim Anfordern gespeicherten Fall, nicht den Request-Kontext).
 */
@QuarkusTest
class WbtVerkaufE2ETest {

    // Feste Subs (für @TestSecurity nötig: Konstantenausdruck) → @BeforeEach bereinigt deterministisch.
    private static final String SUB_HAPPY = "wbt-e2e-sub-happy";
    private static final String SUB_STORNO = "wbt-e2e-sub-storno";

    @Inject
    KurseinschreibungService service;

    @Inject
    FakeOpenolatProvisioning openolat;

    @BeforeEach
    void cleanup() {
        openolat.reset(); // gemeinsames @Singleton-Mock — Zustand (fail/Counter) anderer Tests zurücksetzen
        QuarkusTransaction.requiringNew()
                .run(() -> Kurseinschreibung.delete("keycloakSub in ?1", List.of(SUB_HAPPY, SUB_STORNO)));
    }

    /** Vendure-Test-Doppel: liefert deterministische IDs zurück, ohne einen laufenden Shop zu brauchen. */
    public static class FakeVendure extends WbtVendureProjektion {
        @Override
        public Ergebnis projiziere(de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs e) {
            return new Ergebnis("vendure-prod-" + e.id, "vendure-var-" + e.id);
        }
    }

    private static String uniqueCode(String praefix) {
        return praefix + (System.nanoTime() % 1_000_000_000L);
    }

    /** rest-assured-Spezifikation mit Fall-Korrelation über den W3C-baggage-Header. */
    private static RequestSpecification gegeben(String fall) {
        return given().contentType(ContentType.JSON).header("baggage", "prozess.fall=" + fall);
    }

    /** Legt einen verkäuflichen, aktiven, importierten WBT an und gibt seine MDM-Id zurück. */
    private long wbtAnlegenUndVeroeffentlichen(String fall, String code) {
        long id = gegeben(fall)
                .body("""
                        {"code":"%s","titel":"Golf-Grundlagen (WBT)","kurzbeschreibung":"SCORM-Kurs",
                         "openolatKey":884736,"scormVersion":"SCORM_12","preisCent":4900,
                         "shopVerkauf":true,"status":"AKTIV"}""".formatted(code))
                .when().post("/lms/kurse").then().statusCode(201)
                .extract().jsonPath().getLong("id");

        // Katalog → Shop projizieren (Vendure gemockt) → vendureProductId wird zurückgeschrieben
        gegeben(fall).when().post("/lms/kurse/" + id + "/veroeffentlichen").then().statusCode(200)
                .body("vendureProductId", equalTo("vendure-prod-" + id));
        return id;
    }

    @Test
    @TestSecurity(user = SUB_HAPPY, roles = "katalog-pflege")
    @JwtSecurity(claims = @Claim(key = "sub", value = SUB_HAPPY))
    void happyPath_kaufen_ausliefern_nutzen() {
        QuarkusMock.installMockForType(new FakeVendure(), WbtVendureProjektion.class);
        String fall = "wbt-happy";
        long kursId = wbtAnlegenUndVeroeffentlichen(fall, uniqueCode("WBT-E2E-H-"));

        // Kauf im Shop (Order-Naht): bezahlte Vendure-Order → Einschreibungs-Anforderung (Outbox)
        long einschreibungId = gegeben(fall)
                .body("""
                        {"vendureOrderId":"ord-h-%d","keycloakSub":"%s","email":"%s@kunde.de",
                         "anzeigeName":"Karla Kundin","vendureProductIds":["vendure-prod-%d"]}"""
                        .formatted(kursId, SUB_HAPPY, SUB_HAPPY, kursId))
                .when().post("/lms/einschreibungen/bestellung").then().statusCode(202)
                .extract().jsonPath().getLong("[0].id");

        // Auslieferung: Dispatcher schreibt den Kunden in OpenOLAT ein (OpenOLAT gemockt) → EINGESCHRIEBEN
        service.verarbeite(einschreibungId);

        // Nutzung: der Kunde sieht „Meine Trainings" im Portal inkl. SSO-Launch-Deeplink
        gegeben(fall).when().get("/lms/portal/trainings").then().statusCode(200)
                .body("status", hasItem("EINGESCHRIEBEN"))
                .body("find { it.status == 'EINGESCHRIEBEN' }.launchUrl",
                        containsString("/url/RepositoryEntry/884736"));
    }

    /**
     * Variante: Refund/Storno der Order → der Zugang wird in OpenOLAT wieder entzogen (Unenrol). Liefert
     * der Auslieferungs-Phase einen zweiten, alternativen Trace (Enrol vs. Unenrol) ⇒ der Inductive Miner
     * erzeugt ein Gateway. Übt die heute gebaute Refund-Naht ({@code /bestellung/{id}/storno}).
     */
    @Test
    @TestSecurity(user = SUB_STORNO, roles = "katalog-pflege")
    @JwtSecurity(claims = @Claim(key = "sub", value = SUB_STORNO))
    void variante_refund_zugangEntziehen() {
        QuarkusMock.installMockForType(new FakeVendure(), WbtVendureProjektion.class);
        String fall = "wbt-storno";
        long kursId = wbtAnlegenUndVeroeffentlichen(fall, uniqueCode("WBT-E2E-S-"));
        String orderId = "ord-s-" + kursId;

        long einschreibungId = gegeben(fall)
                .body("""
                        {"vendureOrderId":"%s","keycloakSub":"%s","email":"%s@kunde.de",
                         "anzeigeName":"Sven Storno","vendureProductIds":["vendure-prod-%d"]}"""
                        .formatted(orderId, SUB_STORNO, SUB_STORNO, kursId))
                .when().post("/lms/einschreibungen/bestellung").then().statusCode(202)
                .extract().jsonPath().getLong("[0].id");
        service.verarbeite(einschreibungId); // EINGESCHRIEBEN

        // Refund der Order → alle Einschreibungen auf Storno; Dispatcher entzieht den OpenOLAT-Zugang
        gegeben(fall).when().post("/lms/einschreibungen/bestellung/" + orderId + "/storno").then().statusCode(200);
        service.verarbeite(einschreibungId); // AUSGESCHRIEBEN (Unenrol)

        // Nach dem Storno ist das Training nicht mehr in der Portal-Sicht (AUSGESCHRIEBEN wird gefiltert)
        gegeben(fall).when().get("/lms/portal/trainings").then().statusCode(200)
                .body("size()", equalTo(0));
    }
}
