package de.netzfactor.ebz.controlling.integration.shop;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

/**
 * Periodischer CRM→Vendure-Personen-Sync (Ansprechpartner + Fotos).
 * <p>
 * <b>Best Practice:</b> {@code quarkus-scheduler} ({@link Scheduled}) für in-process-Zeitsteuerung —
 * leichtgewichtig, kein externer Scheduler. Intervall konfigurierbar über {@code crm.vendure.sync.every}
 * (Default 1h). {@link ConcurrentExecution#SKIP} verhindert überlappende Läufe. Fehler werden geloggt,
 * nicht geworfen (der nächste Lauf versucht es erneut). Für mehrere Instanzen mit garantiert
 * einmaliger Ausführung wäre {@code quarkus-quartz} (Cluster-Mode) bzw. die bestehende
 * Outbox-/Lock-Strategie die Wahl; der manuelle Trigger bleibt {@code POST /shop/crm-personen-sync}.
 */
@ApplicationScoped
public class CrmVendureSyncScheduler {

    private static final Logger LOG = Logger.getLogger(CrmVendureSyncScheduler.class);

    @Inject
    CrmVendureSyncService service;

    @Scheduled(every = "{crm.vendure.sync.every:1h}", concurrentExecution = ConcurrentExecution.SKIP, identity = "crm-vendure-sync")
    void periodischerSync() {
        try {
            CrmVendureSyncService.Ergebnis r = service.syncAnsprechpartner();
            LOG.infof("CRM→Vendure-Sync: %d Ansprechpartner gesynct, %d Foto(s) hochgeladen",
                    r.gesendet(), r.fotosHochgeladen());
        } catch (RuntimeException e) {
            LOG.warn("CRM→Vendure-Sync fehlgeschlagen (Retry beim nächsten Intervall): " + e.getMessage());
        }
    }
}
