package de.netzfactor.ebz.controlling.integration.kommunikation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.ZustellService;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K0-Fundament des Kommunikations-Systems: ein {@code KommunikationsEreignis} projiziert in den
 * personenseitigen Aktivitätslog ({@code PersonEreignis}) + Push (PORTAL synchron, E-Mail async über die
 * Zustell-Outbox). Geprüft werden: Pull-Zeitstrahl im Portal, Ungelesen-Badge, gelesen-markieren, der
 * E-Mail-Fan-out über den Dispatcher (MockMailbox) sowie der kontext-skopierte Zugriffsschutz
 * (fremdes Ereignis = 403, unauthentifiziert = 401).
 */
@QuarkusTest
class KommunikationK0Test {

    @Inject
    KommunikationApi kommunikation;

    @Inject
    ZustellService zustellService;

    @Inject
    PartyHoheitService party;

    @Inject
    MockMailbox mailbox;

    @Test
    @TestSecurity(user = "komm-k0-sub")
    void ereignis_landetImPortalLogUndPerEmail() {
        mailbox.clear();
        Person p = party.selbstRegistrieren("komm-k0-sub", "komm-k0@ebz.de", "Tom Test");

        // Eindeutiger Idempotenz-Schlüssel pro Lauf (persistente Showcase-DB → sonst Dedupe aus Vorlauf).
        PersonEreignis ev = kommunikation.protokolliere(KommunikationsEreignis.mitKontext(
                EreignisTyp.RECHNUNG_VERSANDT, p.id, "Ihre Testrechnung steht bereit",
                KontextTyp.RECHNUNG, 4711L, "fall-k0", "k0test:re:" + System.nanoTime()));
        assertNotNull(ev);

        // Pull-Zeitstrahl im Portal zeigt den frischen, ungelesenen Eintrag …
        given().when().get("/kommunikation/portal/ereignisse").then().statusCode(200)
                .body("id", hasItem(ev.id.intValue()))
                .body("find { it.id == " + ev.id + " }.kontextTyp", org.hamcrest.Matchers.equalTo("RECHNUNG"))
                .body("find { it.id == " + ev.id + " }.gelesen", org.hamcrest.Matchers.equalTo(false));

        // … und der Ungelesen-Badge zählt ihn
        given().when().get("/kommunikation/portal/ungelesen").then().statusCode(200)
                .body("anzahl", greaterThanOrEqualTo(1));

        // E-Mail-Fan-out: der Dispatcher stellt die EMAIL-Zustellung über die Outbox zu (MockMailbox)
        zustellService.verarbeiteFaellige(100);
        assertFalse(mailbox.getMailMessagesSentTo("komm-k0@ebz.de").isEmpty(),
                "E-Mail-Zustellung über die Zustell-Outbox erwartet");

        // gelesen-markieren senkt den Badge
        given().when().post("/kommunikation/portal/ereignisse/" + ev.id + "/gelesen").then().statusCode(204);
        given().when().get("/kommunikation/portal/ereignisse").then().statusCode(200)
                .body("find { it.id == " + ev.id + " }.gelesen", org.hamcrest.Matchers.equalTo(true));
    }

    @Test
    @TestSecurity(user = "komm-k0-sub")
    void fremdesEreignis_ist403() {
        party.selbstRegistrieren("komm-k0-sub", "komm-k0@ebz.de", "Tom Test");
        Person other = party.selbstRegistrieren("komm-k0-fremd-sub", "komm-k0-fremd@ebz.de", "Olaf Other");
        PersonEreignis fremd = kommunikation.protokolliere(KommunikationsEreignis.ohneKontext(
                EreignisTyp.SYSTEM_HINWEIS, other.id, "Hinweis für Olaf", "k0test:fremd:" + System.nanoTime()));
        assertNotNull(fremd);

        given().when().post("/kommunikation/portal/ereignisse/" + fremd.id + "/gelesen").then().statusCode(403);
    }

    @Test
    void ohneLogin_ist401() {
        given().when().get("/kommunikation/portal/ereignisse").then().statusCode(401);
    }
}
