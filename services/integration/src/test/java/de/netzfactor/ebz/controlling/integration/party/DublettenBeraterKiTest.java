package de.netzfactor.ebz.controlling.integration.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil;
import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil.Einschaetzung;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.DublettenBerater;

/**
 * Schritt B — <b>nicht-Fallback-Pfad</b>: liefert der {@link DublettenBerater} ein bestimmtes (hier
 * aufgezeichnetes) Urteil, muss es unverändert durch {@code DublettenReviewService} → Resource bis in
 * die Queue durchgereicht werden. Der Berater ist gemockt (kein API-Key/keine Netzlast in der CI);
 * das verdrahtete LLM (Berater → {@code DublettenKlassifikator}) entspricht 1:1 dem erprobten
 * {@code DealEnricher → DealClassifier}-Muster.
 */
@QuarkusTest
@TestSecurity(user = "sb", roles = "rechnung-pflege")
class DublettenBeraterKiTest {

    /** Aufgezeichnetes Urteil — ersetzt die echte KI-Bewertung im Test. */
    public static class FakeBerater extends DublettenBerater {
        @Override
        public DublettenUrteil bewerteFirma(Organisation kandidat, Organisation ziel) {
            return new DublettenUrteil(0.97, Einschaetzung.MATCH, "Test: dieselbe Firma (gemockt)");
        }

        @Override
        public DublettenUrteil bewertePerson(Person kandidat, Person ziel) {
            return new DublettenUrteil(0.97, Einschaetzung.MATCH, "Test: dieselbe Person (gemockt)");
        }
    }

    @BeforeEach
    void mockBerater() {
        QuarkusMock.installMockForType(new FakeBerater(), DublettenBerater.class);
    }

    @Test
    void kiUrteil_match_erscheintInDerQueue() {
        long n = System.nanoTime();
        String ust = "DE" + (n % 100000000L);

        int ziel = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"KI Bau GmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"%s"}"""
                        .formatted(n, ust))
                .when().post("/party/organisationen").then().statusCode(201)
                .extract().jsonPath().getInt("id");

        int kandidat = given().contentType(ContentType.JSON)
                .header("X-Forwarded-For", "198.51." + (int) ((n / 251) % 251) + "." + (int) (n % 251))
                .body("""
                        {"name":"KI-Bau GesmbH %d","plz":"45657","ort":"RE","land":"DE","ustId":"%s",
                         "ansprechpartnerEmail":"ki+%d@bau.de","ansprechpartnerName":"Kai KI"}"""
                        .formatted(n, ust, n))
                .when().post("/party/anfragen/ausbildungsbetrieb").then().statusCode(201)
                .extract().jsonPath().getInt("organisationId");

        given().when().get("/party/reviews/queue").then().statusCode(200)
                .body("find { it.kandidatId == " + kandidat + " }.vorschlaege[0].zielId", equalTo(ziel))
                .body("find { it.kandidatId == " + kandidat + " }.vorschlaege[0].einschaetzung", equalTo("MATCH"))
                .body("find { it.kandidatId == " + kandidat + " }.vorschlaege[0].aehnlichkeit", equalTo(0.97f));
    }
}
