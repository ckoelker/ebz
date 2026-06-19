package de.netzfactor.ebz.controlling.integration.kommunikation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.PraeferenzService;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.ZustellService;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.RealtimePort;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K1 — System→Person: die Event-Spine ({@code Event<KommunikationsEreignis>} → {@code @Observes}
 * BenachrichtigungService → KommunikationApi) projiziert Domänen-Events in den Portal-Aktivitätslog;
 * Kanal-Präferenzen schalten E-Mail ab (PORTAL bleibt); Templates (Qute) rendern den E-Mail-Body; der
 * SSE-RealtimePort signalisiert neue Ereignisse.
 */
@QuarkusTest
class KommunikationK1Test {

    @Inject
    Event<KommunikationsEreignis> spine;

    @Inject
    ZustellService zustellService;

    @Inject
    PraeferenzService praeferenzen;

    @Inject
    PartyHoheitService party;

    @Inject
    RealtimePort realtime;

    @Inject
    MockMailbox mailbox;

    @Test
    @TestSecurity(user = "k1-spine-sub")
    void spine_projiziertPortalLog() {
        Person p = party.selbstRegistrieren("k1-spine-sub", "k1-spine@ebz.de", "Sina Spine");
        mailbox.clear();

        // Domänen-Event feuern (wie es ein Bestands-Flow täte) → @Observes legt den Portal-Log-Eintrag an.
        // Eindeutiger Schlüssel pro Lauf (persistente Showcase-DB → sonst Dedupe aus Vorlauf).
        String betreff = "Anmeldebestätigung: Azubi Anton " + System.nanoTime();
        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.ANMELDUNG_BESTAETIGT, p.id,
                betreff, KontextTyp.ANMELDUNG, 1L, null, "k1:anmeldung:" + System.nanoTime()));

        given().when().get("/kommunikation/portal/ereignisse").then().statusCode(200)
                .body("betreff", hasItem(betreff));
        // ANMELDUNG_BESTAETIGT ist vorerst PORTAL-only → keine E-Mail
        assertTrue(mailbox.getMailMessagesSentTo("k1-spine@ebz.de").isEmpty(), "kein E-Mail-Versand erwartet");
    }

    @Test
    @TestSecurity(user = "k1-pref-sub")
    void praeferenz_schaltetEmailAus_portalBleibt() {
        Person p = party.selbstRegistrieren("k1-pref-sub", "k1-pref@ebz.de", "Pia Pref");

        // E-Mail abschalten → RECHNUNG_VERSANDT (PORTAL+EMAIL) darf nur noch ins Portal
        praeferenzen.setze(p.id, Kanal.EMAIL, false);
        mailbox.clear();
        String betreffOff = "Ihre Rechnung steht bereit " + System.nanoTime();
        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.RECHNUNG_VERSANDT, p.id,
                betreffOff, KontextTyp.RECHNUNG, null, null, "k1:re-off:" + System.nanoTime()));
        zustellService.verarbeiteFaellige(100);
        assertTrue(mailbox.getMailMessagesSentTo("k1-pref@ebz.de").isEmpty(), "E-Mail unterdrückt (Präferenz aus)");
        given().when().get("/kommunikation/portal/ereignisse").then().statusCode(200)
                .body("betreff", hasItem(betreffOff));

        // E-Mail wieder an → jetzt wird zugestellt, Body kommt aus dem Qute-Template
        praeferenzen.setze(p.id, Kanal.EMAIL, true);
        mailbox.clear();
        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.RECHNUNG_VERSANDT, p.id,
                "Ihre Rechnung steht bereit " + System.nanoTime(), KontextTyp.RECHNUNG, null, null,
                "k1:re-on:" + System.nanoTime()));
        zustellService.verarbeiteFaellige(100);
        var mails = mailbox.getMailMessagesSentTo("k1-pref@ebz.de");
        assertFalse(mails.isEmpty(), "E-Mail-Zustellung nach Wieder-Einschalten erwartet");
        assertTrue(mails.get(0).getText().contains("EBZ-Portal"), "Qute-Template gerendert");
    }

    @Test
    void sse_signalisiertNeuesEreignis() {
        Person p = party.selbstRegistrieren("k1-sse-sub", "k1-sse@ebz.de", "Sven SSE");
        AssertSubscriber<String> sub = realtime.stream(p.id).subscribe()
                .withSubscriber(AssertSubscriber.create(1));

        spine.fire(KommunikationsEreignis.ohneKontext(EreignisTyp.SYSTEM_HINWEIS, p.id,
                "Live-Hinweis", "k1:sse:" + System.nanoTime()));

        sub.awaitItems(1, Duration.ofSeconds(5));
        assertFalse(sub.getItems().isEmpty(), "SSE-Signal für neues Ereignis erwartet");
    }
}
