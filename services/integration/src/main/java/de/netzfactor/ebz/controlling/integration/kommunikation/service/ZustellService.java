package de.netzfactor.ebz.controlling.integration.kommunikation.service;

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

import de.netzfactor.ebz.controlling.integration.kommunikation.model.ZustellAuftrag;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.ZustellAuftrag.Status;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.KanalVersand;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Wiederverwendbare <b>Transactional-Outbox-Mechanik</b> der Kommunikation — die aus {@code OutboxService}
 * herausgelöste, vom Auslöser entkoppelte Dispatcher-Logik (Enqueue/Poll/Lock/Backoff/Dead-Letter). Statt
 * an {@code Anmeldung} hängt sie an einer {@link Zustellung} (echte FK) und wählt den {@link KanalVersand}-
 * Adapter über den {@link Zustellung#kanal Kanal}.
 * <p>
 * {@link #zustelleSofort} ist der synchrone Pfad für {@code PORTAL} (in der Geschäfts-Tx, kann nicht
 * fehlschlagen); {@link #enqueue} reiht externe Kanäle ({@code EMAIL}/{@code SMS}) ein, die der
 * {@code ZustellDispatcher} async über {@link #verarbeite} zustellt (jeweils eigene Transaktion + Lock).
 */
@ApplicationScoped
public class ZustellService {

    private static final Logger LOG = Logger.getLogger(ZustellService.class);

    @Inject
    Instance<KanalVersand> adapter;

    @Inject
    Prozessspur prozess;

    /** Synchrone Zustellung (PORTAL): Adapter direkt in der laufenden Transaktion aufrufen. */
    public void zustelleSofort(Zustellung zustellung) {
        adapterFuer(zustellung.kanal).zustelle(zustellung);
    }

    /**
     * Reiht — idempotent — eine {@link Zustellung} zur garantierten async Auslieferung ein (gleicher
     * {@code idempotenzSchluessel} → kein Doppel). Aufruf <b>innerhalb</b> der auslösenden Transaktion,
     * damit Zustellung und Auftrag gemeinsam committen. Fall-Korrelation aus dem aktuellen Baggage.
     */
    @Transactional
    public ZustellAuftrag enqueue(Zustellung zustellung) {
        return enqueue(zustellung, Instant.now());
    }

    /**
     * Wie {@link #enqueue(Zustellung)}, aber mit explizitem frühesten Versandzeitpunkt {@code faelligAb}
     * (K1b Deferred-Send: Quiet-Hours/Rate-Limit) — der Dispatcher zieht den Auftrag erst dann.
     */
    @Transactional
    public ZustellAuftrag enqueue(Zustellung zustellung, Instant faelligAb) {
        String schluessel = zustellung.kanal.name() + ":" + zustellung.id;
        ZustellAuftrag vorhanden = ZustellAuftrag.find("idempotenzSchluessel", schluessel).firstResult();
        if (vorhanden != null) {
            return vorhanden;
        }
        ZustellAuftrag a = new ZustellAuftrag();
        a.zustellung = zustellung;
        a.status = Status.OFFEN;
        a.versuche = 0;
        a.erstelltAm = Instant.now();
        a.naechsterVersuchAm = faelligAb == null ? a.erstelltAm : faelligAb;
        a.idempotenzSchluessel = schluessel;
        a.prozessFall = aktuellerFall();
        a.persist();
        return a;
    }

    /** IDs aller fälligen, offenen Aufträge (eigene Lese-Transaktion für den Dispatcher). */
    @Transactional
    public List<Long> faelligeIds(int limit) {
        return ZustellAuftrag.<ZustellAuftrag>find("status = ?1 and naechsterVersuchAm <= ?2",
                        Status.OFFEN, Instant.now())
                .page(0, limit).list()
                .stream().map(a -> a.id).toList();
    }

    /**
     * Verarbeitet genau einen Auftrag in eigener Transaktion (Pessimistic-Lock gegen Doppel-Zustellung).
     * Erfolg → {@code ERLEDIGT}; Fehlschlag → Versuch+Backoff, nach {@link ZustellAuftrag#MAX_VERSUCHE} →
     * {@code FEHLGESCHLAGEN} (Dead-Letter, Zustellung {@code FEHLER}).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void verarbeite(Long auftragId) {
        ZustellAuftrag a = ZustellAuftrag.findById(auftragId, LockModeType.PESSIMISTIC_WRITE);
        if (a == null || a.status != Status.OFFEN) {
            return;
        }
        a.versuche++;
        try {
            adapterFuer(a.zustellung.kanal).zustelle(a.zustellung);
            a.status = Status.ERLEDIGT;
            a.erledigtAm = Instant.now();
            a.letzterFehler = null;
            prozess.schritt("Zustellung " + a.zustellung.kanal, Prozess.Akteur.SYSTEM,
                    a.zustellung.kanal == Zustellung.Kanal.EMAIL ? Prozess.System.MAIL : Prozess.System.PORTAL,
                    Prozess.Typ.MESSAGE, Prozess.Phase.KANAL_ZUSTELLUNG);
        } catch (RuntimeException e) {
            a.letzterFehler = kurz(e.getMessage());
            if (a.versuche >= ZustellAuftrag.MAX_VERSUCHE) {
                a.status = Status.FEHLGESCHLAGEN;
                a.zustellung.status = Zustellung.Status.FEHLER;
                LOG.errorf("Zustell-Auftrag %d eskaliert nach %d Versuchen: %s", a.id, a.versuche, a.letzterFehler);
            } else {
                a.naechsterVersuchAm = Instant.now().plus(backoff(a.versuche));
                LOG.warnf("Zustell-Auftrag %d Versuch %d fehlgeschlagen (%s) → Retry %s",
                        a.id, a.versuche, a.letzterFehler, a.naechsterVersuchAm);
            }
        }
    }

    /** Manueller Neuversuch eines Dead-Letter-Auftrags (HITL-Aktion aus dem Cockpit). */
    @Transactional
    public ZustellAuftrag neuVersuch(Long auftragId) {
        ZustellAuftrag a = ZustellAuftrag.findById(auftragId);
        if (a == null || a.status == Status.ERLEDIGT) {
            return a;
        }
        a.status = Status.OFFEN;
        a.naechsterVersuchAm = Instant.now();
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

    private KanalVersand adapterFuer(Kanal kanal) {
        for (KanalVersand k : adapter) {
            if (k.kanal() == kanal) {
                return k;
            }
        }
        throw new IllegalStateException("Kein KanalVersand-Adapter für " + kanal);
    }

    private static Duration backoff(int versuch) {
        long sek = Math.min(30L * (1L << (versuch - 1)), 3600L);
        return Duration.ofSeconds(sek);
    }

    private static String kurz(String s) {
        if (s == null) {
            return "(ohne Meldung)";
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(Prozessspur.BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? null : fall;
    }
}
