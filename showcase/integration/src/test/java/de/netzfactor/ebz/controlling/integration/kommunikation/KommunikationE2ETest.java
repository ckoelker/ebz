package de.netzfactor.ebz.controlling.integration.kommunikation;

import java.util.List;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.ZustellService;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * <b>End-to-End-Durchstich „Benachrichtigung & Bestätigung" als Living-Documentation-Quelle.</b>
 * <p>
 * Fährt die Event-Spine: ein Domänen-Event → Benachrichtigung auslösen (Portal-Inbox) → Kanal-Zustellung
 * (EMAIL über die Zustell-Outbox) → Pflicht-Bestätigung (Person quittiert die Kenntnisnahme). Die
 * {@code Prozessspur}-Spans (Verfahren {@code KOMMUNIKATION}) werden über den {@code SpanLogExporter} nach
 * {@code spans.jsonl} geschrieben → BPMN. Da die Spine intern (CDI-Event), nicht über REST läuft, setzt der
 * Test die Fall-Korrelation direkt im W3C-Baggage-Kontext (statt über den HTTP-{@code baggage}-Header).
 */
@QuarkusTest
class KommunikationE2ETest {

    @Inject
    Event<KommunikationsEreignis> spine;

    @Inject
    ZustellService zustellService;

    @Inject
    KommunikationApi kommunikation;

    @Inject
    PartyHoheitService party;

    @Test
    void benachrichtigung_zustellung_bestaetigung() {
        String fall = "benachrichtigung-pflicht";
        long n = System.nanoTime();
        String sub = "komm-e2e-" + n;
        Person p = party.selbstRegistrieren(sub, "komm-e2e+" + n + "@ebz.de", "Konrad Kommunikation");

        try (Scope ignored = Baggage.current().toBuilder()
                .put(Prozessspur.BAGGAGE_FALL, fall).build().makeCurrent()) {

            // 1) Domänen-Event mit EMAIL-Kanal → Benachrichtigung auslösen (Portal-Inbox) + EMAIL einreihen
            spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.EINSCHREIBUNG_AKTIV, p.id,
                    "Ihre Kurseinschreibung ist aktiv " + n, KontextTyp.BILDUNGSANGEBOT, 1L, null,
                    "e2e:einschr:" + n));

            // 2) Zustell-Outbox abarbeiten → EMAIL-Kanal-Zustellung
            zustellService.verarbeiteFaellige(100);

            // 3) Pflicht-Kenntnisnahme-Event (PORTAL, bestätigungspflichtig) auslösen …
            spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.AZUBI_VERTRAG_BESTAETIGT, p.id,
                    "Bitte Ausbildungsvertrag zur Kenntnis nehmen " + n, KontextTyp.ANMELDUNG, 1L, null,
                    "e2e:vertrag:" + n));

            // … und durch die Person quittieren → Pflicht-Bestätigung
            List<PersonEreignis> offen = kommunikation.offeneBestaetigungen(p.id);
            org.junit.jupiter.api.Assertions.assertFalse(offen.isEmpty(), "Pflicht-Kenntnisnahme erwartet");
            kommunikation.bestaetige(offen.get(0).id, sub, "203.0.113.7");
        }
    }
}
