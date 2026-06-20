package de.netzfactor.ebz.controlling.integration.kommunikation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * Prozessfall-Korrelation: ein projiziertes {@link PersonEreignis} trägt die {@code prozess.fall}-Id (BPMN-
 * Case-Id) aus dem aktuellen W3C-Baggage, sodass Support eine Benachrichtigung ↔ Trace/BPMN zuordnen kann.
 * Ohne Baggage-Kontext bleibt das Feld {@code null} (Bestandsverhalten). Direkt gegen die Fassade geprüft
 * (synchroner Auslöser-Kontext = derselbe Thread wie ein {@code @Observes}-Consumer).
 */
@QuarkusTest
class KommunikationKorrelationTest {

    @Inject
    KommunikationApi kommunikation;

    @Inject
    PartyHoheitService party;

    @Test
    void prozessFall_ausBaggage_landetAmEreignis() {
        Person p = party.selbstRegistrieren("korr-sub-" + System.nanoTime(),
                "korr-" + System.nanoTime() + "@ebz.de", "Kora Korrelation");
        String fall = "BPMN-FALL-" + System.nanoTime();

        PersonEreignis pe;
        try (Scope s = Baggage.builder().put(Prozessspur.BAGGAGE_FALL, fall).build().makeCurrent()) {
            pe = kommunikation.protokolliere(KommunikationsEreignis.ohneKontext(
                    EreignisTyp.SYSTEM_HINWEIS, p.id, "Korrelations-Hinweis", "korr:" + fall));
        }
        assertEquals(fall, pe.prozessFall, "prozess.fall aus dem Baggage übernommen");
    }

    @Test
    void ohneBaggage_bleibtProzessFallNull() {
        Person p = party.selbstRegistrieren("korr-leer-" + System.nanoTime(),
                "korr-leer-" + System.nanoTime() + "@ebz.de", "Leo Leer");
        PersonEreignis pe = kommunikation.protokolliere(KommunikationsEreignis.ohneKontext(
                EreignisTyp.SYSTEM_HINWEIS, p.id, "Ohne Korrelation", "korr-leer:" + System.nanoTime()));
        assertNull(pe.prozessFall, "ohne Baggage kein prozess.fall");
    }
}
