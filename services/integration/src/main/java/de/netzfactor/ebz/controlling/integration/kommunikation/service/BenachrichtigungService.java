package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;

/**
 * <b>Consumer der Event-Spine</b> (K1): beobachtet die Domänen-Events {@link KommunikationsEreignis}, die
 * die fachlichen Module in ihrer Geschäfts-Transaktion feuern, und projiziert sie über die Fassade
 * {@link KommunikationApi} in den personenseitigen Aktivitätslog (+ Push). So ist die Benachrichtigung
 * <b>ein {@code @Observes}-Pfad statt eines zweiten imperativen Aufrufs</b> — parallel zum OTel-Trace
 * (Prozessspur), korreliert über {@code prozess.fall}, aber entkoppelt.
 * <p>
 * Synchron im selben Transaktionskontext des Auslösers: die unverlierbare Portal-Inbox-Kopie entsteht
 * atomar mit der Geschäftsänderung; externe Kanäle gehen über die Zustell-Outbox. Die Module kennen nur
 * den Kommunikations-Vertrag ({@code event}-Paket), nicht die Interna des Moduls (Richtung erlaubt).
 */
@ApplicationScoped
public class BenachrichtigungService {

    private static final Logger LOG = Logger.getLogger(BenachrichtigungService.class);

    @Inject
    KommunikationApi kommunikation;

    void onEreignis(@Observes KommunikationsEreignis ev) {
        var pe = kommunikation.protokolliere(ev);
        if (pe != null) {
            LOG.debugf("Benachrichtigung projiziert: %s → Person %d (Ereignis %d)",
                    ev.ereignisTyp(), ev.empfaengerPersonId(), pe.id);
        }
    }
}
