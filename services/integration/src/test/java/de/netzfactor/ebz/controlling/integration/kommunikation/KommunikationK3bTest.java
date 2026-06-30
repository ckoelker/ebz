package de.netzfactor.ebz.controlling.integration.kommunikation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;
import de.netzfactor.ebz.controlling.integration.bildung.model.PreisModell;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.GruppenService;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K3b — Bildungsangebot-Kohorte als Verteiler: die Auflösung der Teilnehmenden eines Bildungsangebots
 * (Seminar/Berufsschulklasse) hängt an {@code rechnung.Anmeldung} und kommt über die <b>Inbound-SPI</b>
 * {@code KohortenAuskunft}, die das {@code rechnung}-Modul implementiert. Der Test darf {@code rechnung}/
 * {@code bildung} importieren (Tests sind vom ArchUnit-Scan ausgenommen) und baut die Einschreibungen auf.
 */
@QuarkusTest
class KommunikationK3bTest {

    @Inject
    GruppenService gruppen;

    @Inject
    PartyHoheitService party;

    @Test
    void kohorteLoestTeilnehmendeUeberInboundSpiAuf() {
        long n = System.nanoTime();
        Person a = party.selbstRegistrieren("k3b-a-" + n, "k3b-a-" + n + "@ebz.de", "Aylin A");
        Person b = party.selbstRegistrieren("k3b-b-" + n, "k3b-b-" + n + "@ebz.de", "Baran B");

        Long baId = QuarkusTransaction.requiringNew().call(() -> {
            Bildungsangebot ba = new Bildungsangebot();
            ba.typ = BildungsangebotTyp.SEMINAR;
            ba.code = "K3B-" + n;
            ba.titel = "Testseminar K3b";
            ba.bereich = de.netzfactor.ebz.controlling.integration.bildung.model.Bereich.AKADEMIE;
            ba.status = AngebotStatus.AKTIV;
            ba.gueltigAb = LocalDate.now();
            ba.preisModell = PreisModell.EINMALIG;
            ba.shopVerkauf = false;
            ba.persist();
            einschreiben(a.id, ba);
            einschreiben(b.id, ba);
            return ba.id;
        });

        Personengruppe g = gruppen.anlegenKohorte("Seminar-Kohorte " + n, null, baId);

        List<Long> mitglieder = gruppen.mitglieder(g.id);
        assertTrue(mitglieder.contains(a.id) && mitglieder.contains(b.id),
                "Kohorte löst die Teilnehmenden über die rechnung-Inbound-SPI auf");

        // Voller Pfad: Broadcast an die Kohorte erreicht beide.
        int erreicht = gruppen.broadcast(g.id, "Kursunterlagen sind online " + n);
        assertEquals(2, erreicht, "Broadcast erreicht die ganze Kohorte");
    }

    /** Legt eine AKTIV-Anmeldung der Person für das Bildungsangebot an (privater Debitor projiziert). */
    private void einschreiben(Long personId, Bildungsangebot ba) {
        Debitor d = party.ermittleDebitor(personId, null, Bereich.AKADEMIE);
        Person p = Person.findById(personId);
        Anmeldung a = new Anmeldung();
        a.typ = AnmeldungTyp.BERUFSSCHULE;
        a.teilnehmerName = p.anzeigeName();
        a.teilnehmerPerson = p;
        a.zahlungspflichtigerDebitor = d;
        a.bildungsangebot = ba;
        a.status = AnmeldungStatus.AKTIV;
        a.persist();
    }
}
