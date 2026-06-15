package de.netzfactor.ebz.controlling.integration.outbox.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Asynchroner Worker des Outbox-Patterns: zieht in festem Takt die fälligen Provisionierungs-Aufträge
 * und stößt ihre Zustellung an. Entkoppelt die (sofort committende) Geschäftstransaktion von den
 * unzuverlässigen Drittsystemen — ein Ausfall blockiert nie die Vertragsbestätigung, sondern wird
 * vom {@link OutboxService} mit Backoff erneut versucht und landet zuletzt als Dead-Letter im Cockpit.
 * <p>
 * {@code concurrentExecution=SKIP}: überlappt ein Lauf den nächsten Tick, wird übersprungen (kein
 * Doppel-Anstoß; zusätzlich sichert der Pessimistic-Lock im {@code verarbeite} gegen Doppel-Zustellung).
 */
@ApplicationScoped
public class OutboxDispatcher {

    private static final Logger LOG = Logger.getLogger(OutboxDispatcher.class);

    @Inject
    OutboxService outbox;

    @ConfigProperty(name = "outbox.dispatcher.batch-size", defaultValue = "50")
    int batchSize;

    @Scheduled(every = "${outbox.dispatcher.every:30s}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int verarbeitet = outbox.verarbeiteFaellige(batchSize);
        if (verarbeitet > 0) {
            LOG.debugf("Outbox-Dispatcher: %d Auftrag/Aufträge verarbeitet", verarbeitet);
        }
    }
}
