package de.netzfactor.ebz.controlling.integration.mandant.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Asynchroner Worker der Mandanten-Projektions-Outbox: zieht im festen Takt die fälligen
 * {@link de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion}en und stößt ihre
 * OpenOLAT-Zustellung an (Org-Anlage). Entkoppelt die sofort committende Mandanten-Transaktion vom
 * (potenziell ausfallenden) OpenOLAT — analog {@code EnrollmentDispatcher}.
 * <p>
 * {@code concurrentExecution=SKIP}: ein überlappender Lauf wird übersprungen; zusätzlich sichert der
 * Pessimistic-Lock in {@link MandantProjektionService#verarbeite} gegen Doppel-Zustellung.
 */
@ApplicationScoped
public class MandantProjektionDispatcher {

    private static final Logger LOG = Logger.getLogger(MandantProjektionDispatcher.class);

    @Inject
    MandantProjektionService service;

    @ConfigProperty(name = "mandant.dispatcher.batch-size", defaultValue = "50")
    int batchSize;

    @Scheduled(every = "${mandant.dispatcher.every:15s}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int verarbeitet = service.verarbeiteFaellige(batchSize);
        if (verarbeitet > 0) {
            LOG.debugf("Mandant-Projektions-Dispatcher: %d Projektion(en) verarbeitet", verarbeitet);
        }
    }
}
