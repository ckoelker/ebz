package de.netzfactor.ebz.controlling.integration.mandant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.EinschreibungStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.model.LernleistungsFakt;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;
import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.service.LernnachweisService;
import de.netzfactor.ebz.controlling.integration.mandant.service.OpenolatNachweisProvisioning.KursRef;

/**
 * M6-Beweis des Nachweis-Seams (K6): die in OpenOLAT (gemockt) gehaltene Completion eines WBT wird als
 * kanonischer {@link LernleistungsFakt} mit den <b>Soll-Stunden</b> ins MDM projiziert; idempotent je
 * Einschreibung; ohne Abschluss entsteht kein Fakt. Der OpenOLAT-Call ist über
 * {@link FakeOpenolatNachweisProvisioning} gesteuert (kein echter REST-Aufruf).
 */
@QuarkusTest
class LernnachweisTest {

    @Inject
    LernnachweisService nachweis;

    @Inject
    FakeOpenolatNachweisProvisioning openolat;

    private static final BigDecimal SOLL = new BigDecimal("3.50");

    @BeforeEach
    void setup() {
        openolat.reset();
    }

    /** Legt WBT + Mandant (mit Org) + provisionierte Einschreibung an; liefert die Einschreibungs-Id. */
    private Long szenario(Long identityKey) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);

            WbtKurs wbt = new WbtKurs();
            wbt.code = "WBT-" + suffix;
            wbt.titel = "Compliance-Grundlagen";
            wbt.sollStundenAnrechenbar = SOLL;
            wbt.status = AngebotStatus.AKTIV;
            wbt.shopVerkauf = false;
            wbt.persist();

            Mandant m = new Mandant();
            m.schluessel = "NW-" + suffix;
            m.anzeigeName = "Nachweis-Mandant";
            m.vertragstyp = Mandant.Vertragstyp.ENTERPRISE_FLAT;
            m.status = Mandant.Status.AKTIV;
            m.openolatOrganisationKey = 4242L;
            m.erstelltAm = Instant.now();
            m.persist();

            Kurseinschreibung e = new Kurseinschreibung();
            e.wbtKurs = wbt;
            e.mandant = m;
            e.keycloakSub = "sub-" + suffix;
            e.anzeigeName = "Max Mitarbeiter";
            e.status = EinschreibungStatus.EINGESCHRIEBEN;
            e.openolatIdentityKey = identityKey;
            e.versuche = 0;
            e.naechsterVersuchAm = Instant.now();
            e.erstelltAm = Instant.now();
            e.persist();
            return e.id;
        });
    }

    @Test
    void ensureNachweisKursMerktSchluesselAmWbt() {
        Long eId = szenario(327681L);
        Long wbtId = QuarkusTransaction.requiringNew()
                .call(() -> ((Kurseinschreibung) Kurseinschreibung.findById(eId)).wbtKurs.id);

        KursRef ref = nachweis.ensureNachweisKurs(wbtId);
        assertEquals(7000L, ref.courseId());

        WbtKurs nach = QuarkusTransaction.requiringNew().call(() -> WbtKurs.findById(wbtId));
        assertEquals(7000L, nach.openolatNachweisKursId);
        assertEquals("NODE-NACHWEIS", nach.openolatNachweisNodeId);
    }

    @Test
    void completionWirdAlsFaktMitSollStundenProjiziert() {
        Long eId = szenario(327681L);

        // Abschluss in OpenOLAT festhalten → synchronisieren projiziert den Fakt.
        nachweis.meldeAbschluss(eId, true);
        assertTrue(openolat.meldeCalls.get() >= 1);
        assertTrue(openolat.linkCalls.get() >= 1, "Kurs der Mandant-Org sichtbar gemacht");

        LernleistungsFakt f = nachweis.synchronisiere(eId);
        assertNotNull(f, "Completion → Fakt");

        LernleistungsFakt geladen = QuarkusTransaction.requiringNew().call(() -> {
            LernleistungsFakt x = LernleistungsFakt.findById(f.id);
            // FK-Felder im Tx-Kontext berühren (LAZY)
            x.wbtKurs.getClass();
            return x;
        });
        assertTrue(geladen.bestanden);
        assertEquals(0, SOLL.compareTo(geladen.sollStunden), "Soll-Stunden-Snapshot");
        assertNotNull(geladen.abgeschlossenAm);
        assertEquals("Max Mitarbeiter", geladen.lernenderName);
    }

    @Test
    void synchronisierenIstIdempotent() {
        Long eId = szenario(327681L);
        nachweis.meldeAbschluss(eId, true);
        nachweis.synchronisiere(eId);
        nachweis.synchronisiere(eId);
        long anzahl = QuarkusTransaction.requiringNew().call(() -> LernleistungsFakt.count("einschreibung.id", eId));
        assertEquals(1, anzahl, "genau ein Fakt je Einschreibung");
    }

    @Test
    void ohneAbschlussKeinFakt() {
        Long eId = szenario(327681L);
        LernleistungsFakt f = nachweis.synchronisiere(eId);
        assertNull(f, "ohne Completion kein Fakt");
        long anzahl = QuarkusTransaction.requiringNew().call(() -> LernleistungsFakt.count("einschreibung.id", eId));
        assertEquals(0, anzahl);
    }
}
