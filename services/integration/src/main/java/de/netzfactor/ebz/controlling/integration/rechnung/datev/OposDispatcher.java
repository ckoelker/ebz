package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Zieht im festen Takt die ausgeglichenen offenen Posten aus DATEV und stößt das BEZAHLT-Setzen an
 * (D3-Regelkreis) — analog {@code EnrollmentDispatcher}/{@code OutboxDispatcher}. Dünner Tick, die Arbeit
 * (inkl. Transaktion) liegt im {@link OposRegelkreisService}. Im Test faktisch aus
 * ({@code %test.opos.dispatcher.every=600s}); der Test treibt {@code verarbeiteFaellige()} selbst.
 */
@ApplicationScoped
public class OposDispatcher {

    private static final Logger LOG = Logger.getLogger(OposDispatcher.class);

    @Inject
    OposRegelkreisService service;

    @ConfigProperty(name = "opos.dispatcher.batch-size", defaultValue = "50")
    int batchSize;

    @Scheduled(every = "${opos.dispatcher.every:15s}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int verbucht = service.verarbeiteFaellige(batchSize);
        if (verbucht > 0) {
            LOG.debugf("OPOS-Dispatcher: %d Zahlung(en) verbucht", verbucht);
        }
    }
}
