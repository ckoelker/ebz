package de.netzfactor.ebz.controlling.integration.lms.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Asynchroner Worker der Einschreibungs-Outbox: zieht im festen Takt die fälligen
 * {@link de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung}en und stößt ihre
 * OpenOLAT-Zustellung an (Enrol/Unenrol). Entkoppelt die sofort committende Order-/Anforderungs-
 * transaktion vom (potenziell ausfallenden) OpenOLAT — analog {@code OutboxDispatcher}.
 * <p>
 * {@code concurrentExecution=SKIP}: ein überlappender Lauf wird übersprungen; zusätzlich sichert der
 * Pessimistic-Lock in {@link KurseinschreibungService#verarbeite} gegen Doppel-Zustellung.
 */
@ApplicationScoped
public class EnrollmentDispatcher {

    private static final Logger LOG = Logger.getLogger(EnrollmentDispatcher.class);

    @Inject
    KurseinschreibungService service;

    @ConfigProperty(name = "lms.dispatcher.batch-size", defaultValue = "50")
    int batchSize;

    @Scheduled(every = "${lms.dispatcher.every:15s}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int verarbeitet = service.verarbeiteFaellige(batchSize);
        if (verarbeitet > 0) {
            LOG.debugf("Enrollment-Dispatcher: %d Einschreibung(en) verarbeitet", verarbeitet);
        }
    }
}
