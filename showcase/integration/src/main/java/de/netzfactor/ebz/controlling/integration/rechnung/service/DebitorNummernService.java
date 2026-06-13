package de.netzfactor.ebz.controlling.integration.rechnung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.StartupEvent;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorKreis;

/**
 * Zentrale Debitorennummern-Vergabe je {@link Bereich} (R3) — die Wurzel gegen Doppel-Debitoren:
 * Nummern entstehen nur noch hier, atomar über einen pessimistischen Lock auf der Kreis-Zeile
 * (analog {@code NummernkreisService}). Die Zeilen werden beim Start angelegt, damit der Lock nie
 * lazy inserten muss (sonst Erst-Insert-Race an der Unique-Constraint).
 */
@ApplicationScoped
public class DebitorNummernService {

    @Transactional
    void seed(@Observes StartupEvent ev) {
        for (Bereich bereich : Bereich.values()) {
            if (DebitorKreis.count("bereich", bereich) == 0) {
                DebitorKreis k = new DebitorKreis();
                k.bereich = bereich;
                k.praefix = praefix(bereich);
                k.naechsteNummer = 10001;
                k.persist();
            }
        }
    }

    /** Zieht die nächste Debitorennummer für den Bereich, z. B. {@code BS-10001}. */
    @Transactional(Transactional.TxType.MANDATORY)
    public String vergib(Bereich bereich) {
        DebitorKreis kreis = DebitorKreis.find("bereich", bereich)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (kreis == null) {
            kreis = new DebitorKreis();
            kreis.bereich = bereich;
            kreis.praefix = praefix(bereich);
            kreis.naechsteNummer = 10001;
            kreis.persist();
        }
        long laufend = kreis.naechsteNummer;
        kreis.naechsteNummer = laufend + 1;
        return "%s%d".formatted(kreis.praefix, laufend);
    }

    /** Default-Präfix je Bereich (im Showcase fest; produktiv DATEV-/Mandanten-Konvention). */
    static String praefix(Bereich bereich) {
        return switch (bereich) {
            case BERUFSSCHULE -> "BS-";
            case HOCHSCHULE -> "HS-";
            case AKADEMIE -> "AK-";
            case SHOP -> "SH-";
        };
    }
}
