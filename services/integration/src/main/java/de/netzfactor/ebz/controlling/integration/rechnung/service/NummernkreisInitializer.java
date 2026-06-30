package de.netzfactor.ebz.controlling.integration.rechnung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.StartupEvent;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Nummernkreis;

/**
 * Legt beim Start je {@code Bereich × Belegart} eine Nummernkreis-Zeile an, falls sie fehlt
 * (idempotent, single-threaded). Damit existiert die Zeile <b>vor</b> jeder nebenläufigen Vergabe und
 * {@code NummernkreisService} muss sie nie lazy erzeugen — der pessimistische Lock greift immer auf
 * eine vorhandene Zeile (sonst könnten parallele Erst-Inserts dieselbe (bereich,belegart) anlegen und
 * an der Unique-Constraint scheitern).
 */
@ApplicationScoped
public class NummernkreisInitializer {

    @Transactional
    void seed(@Observes StartupEvent ev) {
        for (Bereich bereich : Bereich.values()) {
            for (Belegart belegart : Belegart.values()) {
                long vorhanden = Nummernkreis.count("bereich = ?1 and belegart = ?2", bereich, belegart);
                if (vorhanden == 0) {
                    Nummernkreis k = new Nummernkreis();
                    k.bereich = bereich;
                    k.belegart = belegart;
                    k.praefix = NummernkreisService.praefix(bereich, belegart);
                    k.naechsteNummer = 1;
                    k.persist();
                }
            }
        }
    }
}
