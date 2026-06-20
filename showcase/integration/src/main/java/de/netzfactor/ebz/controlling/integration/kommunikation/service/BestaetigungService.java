package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;

/**
 * K5 <b>Pflicht-Bestätigungs-Workflow</b> auf den seit K1 vorhandenen Quittierungs-Feldern: dieser Scheduler
 * zieht im festen Takt alle <i>noch nicht quittierten</i> Pflicht-Kenntnisnahmen ({@link PersonEreignis}
 * mit {@code bestaetigungErforderlich} und ohne {@code bestaetigtAm}) und treibt sie weiter:
 * <ul>
 *   <li><b>Erinnerung</b> (vor Fristablauf, gedrosselt über das Nachfass-Intervall): erzeugt über die
 *       Event-Spine ein {@link EreignisTyp#BESTAETIGUNG_ERINNERUNG}-Ereignis (PORTAL + E-Mail) und merkt
 *       {@code erinnertAm}/{@code erinnerungen}.</li>
 *   <li><b>Eskalation</b> (nach Fristablauf): markiert {@code eskaliertAm} — der Eintrag erscheint im
 *       Cockpit-Report als überfällig/eskaliert; ein {@code hatUeberfaelligeBestaetigung}-Gate kann darauf
 *       blockieren ({@link KommunikationApi}).</li>
 * </ul>
 * Die eigentliche Quittierung erfolgt weiterhin durch die Person ({@code KommunikationApi.bestaetige}); ist
 * sie erfolgt, fällt der Eintrag aus der Auswahl. Idempotent über das pro Erinnerung eindeutige
 * {@code idempotenzSchluessel} der Erinnerungs-Ereignisse.
 */
@ApplicationScoped
public class BestaetigungService {

    private static final Logger LOG = Logger.getLogger(BestaetigungService.class);

    @Inject
    KommunikationApi kommunikation;

    /** Mindestabstand zwischen zwei Erinnerungen zur selben Kenntnisnahme (Default 24h; Test: klein). */
    @ConfigProperty(name = "kommunikation.bestaetigung.erinnerung-intervall", defaultValue = "PT24H")
    Duration erinnerungIntervall;

    @Scheduled(every = "${kommunikation.bestaetigung.every:1h}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int n = verarbeiteFaellige();
        if (n > 0) {
            LOG.debugf("Bestätigungs-Scheduler: %d Erinnerung(en)/Eskalation(en) verarbeitet", n);
        }
    }

    /**
     * Verarbeitet alle offenen Pflicht-Kenntnisnahmen: Eskalation bei Fristablauf, sonst (gedrosselte)
     * Erinnerung. Liefert die Anzahl der ausgelösten Aktionen.
     */
    @Transactional
    public int verarbeiteFaellige() {
        LocalDateTime jetzt = LocalDateTime.now();
        List<PersonEreignis> offen = PersonEreignis.list(
                "bestaetigungErforderlich = true and bestaetigtAm is null and eskaliertAm is null");
        int aktionen = 0;
        for (PersonEreignis pe : offen) {
            boolean ueberfaellig = pe.bestaetigenBis != null && jetzt.isAfter(pe.bestaetigenBis);
            if (ueberfaellig) {
                pe.eskaliertAm = jetzt;
                LOG.warnf("Pflicht-Kenntnisnahme %d (Person %d) eskaliert — Frist %s überschritten",
                        pe.id, pe.empfaengerPersonId, pe.bestaetigenBis);
                aktionen++;
            } else if (erinnerungFaellig(pe, jetzt)) {
                erinnere(pe);
                pe.erinnerungen++;
                pe.erinnertAm = jetzt;
                aktionen++;
            }
        }
        return aktionen;
    }

    /** Cockpit-Report: alle bestätigungspflichtigen Ereignisse (neueste zuerst), Status leitet die Resource ab. */
    public List<PersonEreignis> bestaetigungspflichtige() {
        return PersonEreignis.list("bestaetigungErforderlich = true order by zeitpunkt desc");
    }

    private boolean erinnerungFaellig(PersonEreignis pe, LocalDateTime jetzt) {
        return pe.erinnertAm == null || pe.erinnertAm.plus(erinnerungIntervall).isBefore(jetzt);
    }

    /** Erzeugt das Erinnerungs-Ereignis über die Spine (PORTAL + E-Mail) mit Bezug auf den Originalkontext. */
    private void erinnere(PersonEreignis pe) {
        String schluessel = "erinnerung:" + pe.id + ":" + (pe.erinnerungen + 1);
        kommunikation.protokolliere(KommunikationsEreignis.mitKontext(
                EreignisTyp.BESTAETIGUNG_ERINNERUNG, pe.empfaengerPersonId,
                "Erinnerung: " + pe.betreff, pe.kontextTyp, pe.kontextId, pe.prozessFall, schluessel));
    }
}
