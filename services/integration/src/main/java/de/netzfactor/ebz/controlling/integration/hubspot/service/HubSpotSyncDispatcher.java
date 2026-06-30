package de.netzfactor.ebz.controlling.integration.hubspot.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Asynchroner Worker der HubSpot-Sync-Outbox (Pendant zum {@code ZustellDispatcher}): zieht im festen Takt
 * fällige {@code HubSpotSyncAuftrag}-Einträge und stößt ihren Push über den {@link HubSpotSyncService} an.
 * Entkoppelt die sofort committende Geschäfts-Tx vom unzuverlässigen HubSpot-Aufruf (Backoff/Dead-Letter).
 * {@code concurrentExecution=SKIP} + Pessimistic-Lock sichern gegen Doppelverarbeitung; im Test ist der
 * Autostart aus ({@code hubspot.sync.enabled=false}) und Aufträge werden gezielt getriggert.
 */
@ApplicationScoped
public class HubSpotSyncDispatcher {

    private static final Logger LOG = Logger.getLogger(HubSpotSyncDispatcher.class);

    @Inject
    HubSpotSyncService sync;

    @ConfigProperty(name = "hubspot.sync.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "hubspot.sync.batch-size", defaultValue = "50")
    int batchSize;

    @Scheduled(every = "${hubspot.sync.dispatcher.every:15s}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) {
            return;
        }
        int verarbeitet = sync.verarbeiteFaellige(batchSize);
        if (verarbeitet > 0) {
            LOG.debugf("HubSpot-Sync-Dispatcher: %d Auftrag/Aufträge verarbeitet", verarbeitet);
        }
    }
}
