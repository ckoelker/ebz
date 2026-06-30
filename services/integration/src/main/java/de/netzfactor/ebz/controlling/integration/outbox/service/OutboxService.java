package de.netzfactor.ebz.controlling.integration.outbox.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.opentelemetry.api.baggage.Baggage;

import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Ereignis;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Status;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Zielsystem;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;

/**
 * Schreibt und verarbeitet {@link OutboxAuftrag}-Einträge — das Herz des Outbox-Patterns.
 * <p>
 * {@link #enqueue} läuft in der <b>Geschäftstransaktion des Auslösers</b> (atomar mit der
 * Statusänderung). {@link #verarbeite} läuft je Auftrag in <b>eigener</b> Transaktion
 * ({@code REQUIRES_NEW}, mit Zeilen-Lock), ruft den passenden {@link Zielsystemexport} und führt
 * Erfolg/Backoff-Retry/Dead-Letter nach. Jeder Versuch emittiert einen {@link Prozessspur}-Span
 * (Phase {@link Phase#PROVISIONIERUNG}) — so erscheint der WebUntis-Sync in Jaeger UND im BPMN.
 */
@ApplicationScoped
public class OutboxService {

    private static final Logger LOG = Logger.getLogger(OutboxService.class);

    @Inject
    Instance<Zielsystemexport> exporte;

    @Inject
    Prozessspur prozess;

    /**
     * Legt — idempotent — einen Provisionierungs-Auftrag an (gleicher {@code idempotenzSchluessel} →
     * kein Doppel). Aufruf <b>innerhalb</b> der auslösenden {@code @Transactional}-Methode, damit
     * Auftrag und Geschäftsänderung gemeinsam committen. Die Fall-Korrelation wird aus dem aktuellen
     * Baggage abgegriffen und mitgespeichert (der asynchrone Dispatcher hat keinen HTTP-Kontext mehr).
     */
    @Transactional
    public OutboxAuftrag enqueue(Anmeldung anmeldung, Zielsystem ziel, Ereignis ereignis) {
        String schluessel = ziel.name() + ":" + ereignis.name() + ":" + anmeldung.id;
        OutboxAuftrag vorhanden = OutboxAuftrag.find("idempotenzSchluessel", schluessel).firstResult();
        if (vorhanden != null) {
            return vorhanden; // idempotent — Auftrag existiert bereits
        }
        OutboxAuftrag a = new OutboxAuftrag();
        a.anmeldung = anmeldung;
        a.zielsystem = ziel;
        a.ereignis = ereignis;
        a.status = Status.OFFEN;
        a.versuche = 0;
        a.erstelltAm = Instant.now();
        a.naechsterVersuchAm = a.erstelltAm; // sofort fällig
        a.idempotenzSchluessel = schluessel;
        a.prozessFall = aktuellerFall();
        a.persist();
        LOG.infof("Outbox-Auftrag angelegt: %s (Anmeldung %d)", schluessel, anmeldung.id);
        return a;
    }

    /** IDs aller fälligen, offenen Aufträge (eigene Lese-Transaktion für den Dispatcher). */
    @Transactional
    public List<Long> faelligeIds(int limit) {
        return OutboxAuftrag.<OutboxAuftrag>find("status = ?1 and naechsterVersuchAm <= ?2",
                        Status.OFFEN, Instant.now())
                .page(0, limit).list()
                .stream().map(a -> a.id).toList();
    }

    /**
     * Verarbeitet genau einen Auftrag in eigener Transaktion (mit Pessimistic-Lock gegen Doppel-
     * Zustellung bei mehreren Dispatcher-Läufen). Erfolg → {@code ERLEDIGT}; Fehlschlag → Versuch
     * hochzählen + Backoff, nach {@link OutboxAuftrag#MAX_VERSUCHE} → {@code FEHLGESCHLAGEN} (Dead-Letter).
     * Der Adapter-Fehler wird abgefangen (kein Rollback der Auftrags-Buchführung) und protokolliert.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void verarbeite(Long auftragId) {
        OutboxAuftrag a = OutboxAuftrag.findById(auftragId, LockModeType.PESSIMISTIC_WRITE);
        if (a == null || a.status != Status.OFFEN) {
            return; // schon erledigt / von anderem Lauf gegriffen
        }
        a.versuche++;
        boolean erfolg = false;
        try {
            adapterFuer(a.zielsystem).exportiere(a);
            a.status = Status.ERLEDIGT;
            a.erledigtAm = Instant.now();
            a.letzterFehler = null;
            erfolg = true;
        } catch (RuntimeException e) {
            a.letzterFehler = kurz(e.getMessage());
            if (a.versuche >= OutboxAuftrag.MAX_VERSUCHE) {
                a.status = Status.FEHLGESCHLAGEN; // Dead-Letter → HITL im Cockpit
                LOG.errorf("Outbox-Auftrag %d eskaliert nach %d Versuchen: %s", a.id, a.versuche, a.letzterFehler);
            } else {
                a.naechsterVersuchAm = Instant.now().plus(backoff(a.versuche));
                LOG.warnf("Outbox-Auftrag %d Versuch %d fehlgeschlagen (%s) → Retry %s",
                        a.id, a.versuche, a.letzterFehler, a.naechsterVersuchAm);
            }
        }
        // Sync-Schritt sichtbar machen (Jaeger + BPMN): SERVICE_TASK in der Provisionierungs-Phase,
        // demselben Fall zugeordnet wie der auslösende Anmeldungs-Durchlauf. Als NEBENLÄUFIG markiert
        // (parallelgruppe = Phase): jedes Outbox-Ziel wird so generisch als paralleler BPMN-Zweig
        // gerendert — neue Ziele erscheinen ohne Generator-Änderung automatisch parallel.
        prozess.schritt(fall(a), "Azubi nach " + systemLabel(a.zielsystem) + " übertragen",
                Akteur.SYSTEM, prozessSystem(a.zielsystem), Typ.SERVICE_TASK, Phase.PROVISIONIERUNG,
                Phase.PROVISIONIERUNG.name());
        LOG.infof("Outbox-Auftrag %d verarbeitet: %s", a.id, erfolg ? "ERLEDIGT" : a.status);
    }

    /** Manueller Neuversuch eines Dead-Letter-Auftrags (HITL-Aktion aus dem Cockpit). */
    @Transactional
    public OutboxAuftrag neuVersuch(Long auftragId) {
        OutboxAuftrag a = OutboxAuftrag.findById(auftragId);
        if (a == null || a.status == Status.ERLEDIGT) {
            return a;
        }
        a.status = Status.OFFEN;
        a.naechsterVersuchAm = Instant.now(); // sofort fällig
        return a;
    }

    /** Zieht alle fälligen Aufträge und verarbeitet sie einzeln. Liefert die Anzahl. */
    public int verarbeiteFaellige(int limit) {
        List<Long> ids = faelligeIds(limit);
        for (Long id : ids) {
            verarbeite(id);
        }
        return ids.size();
    }

    private Zielsystemexport adapterFuer(Zielsystem ziel) {
        for (Zielsystemexport e : exporte) {
            if (e.ziel() == ziel) {
                return e;
            }
        }
        throw new IllegalStateException("Kein Zielsystemexport-Adapter für " + ziel);
    }

    private static Duration backoff(int versuch) {
        long sek = Math.min(30L * (1L << (versuch - 1)), 3600L); // 30s, 60s, 120s … max 1h
        return Duration.ofSeconds(sek);
    }

    private static String kurz(String s) {
        if (s == null) {
            return "(ohne Meldung)";
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    private static String systemLabel(Zielsystem ziel) {
        return prozessSystem(ziel).label;
    }

    private static Prozess.System prozessSystem(Zielsystem ziel) {
        return switch (ziel) {
            case WEBUNTIS -> Prozess.System.WEBUNTIS;
            case SUITE8 -> Prozess.System.SUITE8;
            default -> Prozess.System.BACKEND;
        };
    }

    private static String fall(OutboxAuftrag a) {
        return (a.prozessFall == null || a.prozessFall.isBlank()) ? "unbekannt" : a.prozessFall;
    }

    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(Prozessspur.BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? null : fall;
    }
}
