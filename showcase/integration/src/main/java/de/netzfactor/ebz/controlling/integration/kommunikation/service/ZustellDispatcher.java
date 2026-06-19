package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Asynchroner Worker der Kommunikations-Outbox (Pendant zum {@code OutboxDispatcher}): zieht im festen
 * Takt fällige {@code ZustellAuftrag}-Einträge und stößt ihre Zustellung über den {@link ZustellService}
 * an. Entkoppelt die (sofort committende) Geschäfts-Transaktion vom unzuverlässigen externen Versand
 * (SMTP/SMS) — ein Ausfall blockiert nie das Auslöser-Event, sondern wird mit Backoff erneut versucht und
 * landet zuletzt als Dead-Letter zur HITL-Klärung. {@code concurrentExecution=SKIP} + Pessimistic-Lock
 * im {@code verarbeite} sichern gegen Doppel-Zustellung.
 */
@ApplicationScoped
public class ZustellDispatcher {

    private static final Logger LOG = Logger.getLogger(ZustellDispatcher.class);

    @Inject
    ZustellService zustellung;

    @ConfigProperty(name = "kommunikation.dispatcher.batch-size", defaultValue = "50")
    int batchSize;

    @Scheduled(every = "${kommunikation.dispatcher.every:15s}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int verarbeitet = zustellung.verarbeiteFaellige(batchSize);
        if (verarbeitet > 0) {
            LOG.debugf("Zustell-Dispatcher: %d Auftrag/Aufträge verarbeitet", verarbeitet);
        }
    }
}
