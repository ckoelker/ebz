package de.netzfactor.ebz.controlling.integration.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import de.netzfactor.ebz.controlling.integration.party.model.OrgVorschlag;
import de.netzfactor.ebz.controlling.integration.party.service.AnreicherungService;
import de.netzfactor.ebz.controlling.integration.party.service.AnreicherungService.AnreicherungsAnfrage;
import de.netzfactor.ebz.controlling.integration.party.service.AnreicherungService.AnreicherungsEvent;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Firmen-Anreicherung (A15): der WebSocket-Service streamt Fortschritts-Events und einen finalen
 * {@link OrgVorschlag}. Im Test ist die KI aus ({@code %test.crm.anreicherung.ki.enabled=false}) →
 * der deterministische Heuristik-Fallback extrahiert aus dem (gemockten) Impressum-Text.
 */
@QuarkusTest
class AnreicherungServiceTest {

    @Inject
    AnreicherungService service;

    @Test
    void streamtSchritteUndExtrahiertVorschlag() {
        List<AnreicherungsEvent> events = service.anreichern(
                        new AnreicherungsAnfrage("Muster Immobilien GmbH", "https://muster-immo.de", "DE123456789"))
                .collect().asList().await().atMost(Duration.ofSeconds(20));

        // Fortschritts-Schritte VIES, IMPRESSUM, KI-EXTRAKTION kommen als eigene Events.
        assertTrue(events.stream().anyMatch(e -> "schritt".equals(e.typ()) && "VIES".equals(e.schritt())));
        assertTrue(events.stream().anyMatch(e -> "schritt".equals(e.typ()) && "IMPRESSUM".equals(e.schritt())));

        // Genau ein Vorschlag am Ende, mit den heuristisch extrahierten Feldern.
        List<AnreicherungsEvent> vorschlaege = events.stream()
                .filter(e -> "vorschlag".equals(e.typ())).toList();
        assertEquals(1, vorschlaege.size());
        OrgVorschlag v = vorschlaege.get(0).vorschlag();
        assertNotNull(v);
        assertEquals("Muster Immobilien GmbH", v.name());
        assertEquals("GmbH", v.rechtsform());
        assertEquals("DE123456789", v.ustId());
        assertEquals("44137", v.plz());
        assertEquals("Dortmund", v.ort());
        assertNotNull(v.brancheHinweis());
    }

    @Test
    void ohneNamen_meldetFehler() {
        List<AnreicherungsEvent> events = service.anreichern(new AnreicherungsAnfrage("  ", null, null))
                .collect().asList().await().atMost(Duration.ofSeconds(10));
        assertTrue(events.stream().anyMatch(e -> "fehler".equals(e.typ())));
    }
}
