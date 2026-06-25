package de.netzfactor.ebz.controlling.integration.mandant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.mandant.model.Lizenzvertrag;
import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.model.SeatMeldung;
import de.netzfactor.ebz.controlling.integration.mandant.service.SeatLimitService;
import de.netzfactor.ebz.controlling.integration.mandant.service.SeatLimitService.Entscheidung;

/**
 * M5-Beweis des weichen Seat-Caps: Aufnahme innerhalb des Limits passiert ohne Meldung; eine Überbuchung
 * wird durchgelassen, erzeugt aber je Überschreitung eine HITL-{@link SeatMeldung} (E4); EBZ-Kontexte
 * sind unbegrenzt. Die OpenOLAT-Mitgliederzahl ist über {@link FakeOpenolatOrganisationProvisioning}
 * steuerbar (kein echter Call).
 */
@QuarkusTest
class SeatLimitTest {

    @Inject
    SeatLimitService seats;

    @Inject
    FakeOpenolatOrganisationProvisioning openolat;

    @BeforeEach
    void setup() {
        openolat.reset();
    }

    private Long mandantMitLizenz(Mandant.Vertragstyp typ, int seatLimit) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Mandant m = new Mandant();
            m.schluessel = "SEAT-" + (System.nanoTime() % 1_000_000_000L);
            m.anzeigeName = "Seat-Mandant";
            m.vertragstyp = typ;
            m.status = Mandant.Status.AKTIV;
            m.openolatOrganisationKey = 4242L; // Org projiziert → belegung wird gezählt
            m.erstelltAm = Instant.now();
            m.persist();
            if (typ == Mandant.Vertragstyp.ENTERPRISE_FLAT) {
                Lizenzvertrag lz = new Lizenzvertrag();
                lz.mandant = m;
                lz.seatLimit = seatLimit;
                lz.gueltigVon = LocalDate.of(2026, 1, 1);
                lz.aktiv = true;
                lz.persist();
            }
            return m.id;
        });
    }

    @Test
    void aufnahmeInnerhalbDesLimitsOhneMeldung() {
        Long id = mandantMitLizenz(Mandant.Vertragstyp.ENTERPRISE_FLAT, 3);
        openolat.mitgliederCount = 1; // 1 von 3 belegt → noch Platz
        SeatLimitService.SeatAufnahme a = seats.aufnahmePruefen(id);
        assertEquals(Entscheidung.INNERHALB, a.entscheidung());
        assertNull(a.meldungId());
    }

    @Test
    void ueberbuchungWirdDurchgelassenUndMeldetHitl() {
        Long id = mandantMitLizenz(Mandant.Vertragstyp.ENTERPRISE_FLAT, 2);
        openolat.mitgliederCount = 2; // genau am Limit → die nächste Aufnahme überbucht

        SeatLimitService.SeatAufnahme a = seats.aufnahmePruefen(id);
        assertEquals(Entscheidung.UEBERBUCHT, a.entscheidung());
        assertNotNull(a.meldungId(), "Überbuchung erzeugt eine HITL-Meldung");

        SeatMeldung.Status status = QuarkusTransaction.requiringNew()
                .call(() -> ((SeatMeldung) SeatMeldung.findById(a.meldungId())).status);
        assertEquals(SeatMeldung.Status.OFFEN, status);

        // Bestätigung (HITL) → BESTAETIGT + Bearbeiter gesetzt.
        seats.bestaetige(a.meldungId(), "sachbearbeiter");
        SeatMeldung nachher = QuarkusTransaction.requiringNew().call(() -> SeatMeldung.findById(a.meldungId()));
        assertEquals(SeatMeldung.Status.BESTAETIGT, nachher.status);
        assertEquals("sachbearbeiter", nachher.bestaetigtVon);
    }

    @Test
    void jedeWeitereUeberschreitungMeldetErneut() {
        Long id = mandantMitLizenz(Mandant.Vertragstyp.ENTERPRISE_FLAT, 1);
        openolat.mitgliederCount = 5; // weit über Limit
        long vorher = QuarkusTransaction.requiringNew()
                .call(() -> SeatMeldung.count("mandant.id = ?1", id));
        seats.aufnahmePruefen(id);
        seats.aufnahmePruefen(id);
        long nachher = QuarkusTransaction.requiringNew()
                .call(() -> SeatMeldung.count("mandant.id = ?1", id));
        assertEquals(vorher + 2, nachher, "jede Überschreitung erzeugt erneut eine Meldung");
    }

    @Test
    void ebzKernmandantIstUnbegrenzt() {
        Long id = mandantMitLizenz(Mandant.Vertragstyp.EBZ_CUSTOMER, 0);
        openolat.mitgliederCount = 9999;
        SeatLimitService.SeatAufnahme a = seats.aufnahmePruefen(id);
        assertEquals(Entscheidung.UNBEGRENZT, a.entscheidung());
    }
}
