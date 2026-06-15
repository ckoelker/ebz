package de.netzfactor.ebz.controlling.integration.lms.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.opentelemetry.api.baggage.Baggage;

import de.netzfactor.ebz.controlling.integration.lms.model.EinschreibungStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;
import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatProvisioning;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Schreibt und verarbeitet {@link Kurseinschreibung}en — das Outbox-Herz der LMS-Provisionierung
 * (gleiches Muster wie {@code OutboxService}, aber die Einschreibung ist ihr eigener Outbox-Datensatz).
 * <p>
 * {@link #anfordern}/{@link #ausschreiben} laufen in der auslösenden Geschäftstransaktion (Anforderung
 * atomar mit dem Outbox-Zustand). {@link #verarbeite} läuft je Zeile in eigener Transaktion
 * ({@code REQUIRES_NEW} + Zeilen-Lock), ruft {@link OpenolatProvisioning} und führt Erfolg/Backoff/
 * Dead-Letter nach. Jeder Versuch emittiert einen {@link Prozessspur}-Span (Phase
 * {@link Phase#PROVISIONIERUNG}) — der OpenOLAT-Sync erscheint so in Jaeger UND im BPMN.
 */
@ApplicationScoped
public class KurseinschreibungService {

    private static final Logger LOG = Logger.getLogger(KurseinschreibungService.class);

    @Inject
    OpenolatProvisioning openolat;

    @Inject
    Prozessspur prozess;

    /**
     * Fordert — idempotent — die Einschreibung an (Unique {@code wbtKurs×keycloakSub}). Existiert bereits
     * eine (egal welcher Status außer Storno), wird sie zurückgegeben; eine zuvor stornierte wird
     * reaktiviert. Aufruf <b>innerhalb</b> der auslösenden Transaktion (Order-Naht), damit Anforderung
     * und Outbox-Zustand gemeinsam committen.
     */
    @Transactional
    public Kurseinschreibung anfordern(String keycloakSub, String email, String anzeigeName,
            Long wbtKursId, String vendureOrderId) {
        WbtKurs kurs = WbtKurs.findById(wbtKursId);
        if (kurs == null) {
            throw new IllegalArgumentException("Unbekannter WbtKurs " + wbtKursId);
        }
        if (kurs.openolatKey == null) {
            throw new IllegalStateException("WbtKurs " + wbtKursId + " hat keinen openolatKey (nicht importiert).");
        }
        Kurseinschreibung e = Kurseinschreibung
                .find("wbtKurs = ?1 and keycloakSub = ?2", kurs, keycloakSub).firstResult();
        boolean neu = e == null;
        if (neu) {
            e = new Kurseinschreibung();
            e.wbtKurs = kurs;
            e.keycloakSub = keycloakSub;
            e.erstelltAm = Instant.now();
            e.versuche = 0;
        }
        // E-Mail/Name aktualisieren (frische Order-Daten) und auf "fällig" setzen, falls nicht schon eingeschrieben.
        e.email = email;
        e.anzeigeName = anzeigeName;
        e.vendureOrderId = vendureOrderId;
        if (e.status != EinschreibungStatus.EINGESCHRIEBEN) {
            e.status = EinschreibungStatus.ANGEFORDERT;
            e.versuche = 0;
            e.naechsterVersuchAm = Instant.now();
            e.letzterFehler = null;
            e.prozessFall = aktuellerFall();
        }
        if (neu) {
            e.persist(); // erst jetzt — alle NOT-NULL-Felder gesetzt (persist() flusht sofort)
        }
        LOG.infof("Einschreibung angefordert: Kurs %d (openolat %d) × sub %s", wbtKursId, kurs.openolatKey, keycloakSub);
        return e;
    }

    /** Storniert eine Einschreibung (Gegen-Event): setzt sie auf {@code STORNO_ANGEFORDERT} (Dispatcher schreibt aus). */
    @Transactional
    public Kurseinschreibung ausschreiben(Long einschreibungId) {
        Kurseinschreibung e = Kurseinschreibung.findById(einschreibungId);
        if (e == null) {
            return null;
        }
        e.status = EinschreibungStatus.STORNO_ANGEFORDERT;
        e.versuche = 0;
        e.naechsterVersuchAm = Instant.now();
        e.letzterFehler = null;
        return e;
    }

    /** IDs aller fälligen Einschreibungen (offene Richtung + Zeit erreicht). */
    @Transactional
    public List<Long> faelligeIds(int limit) {
        return Kurseinschreibung.<Kurseinschreibung>find(
                        "(status = ?1 or status = ?2) and naechsterVersuchAm <= ?3",
                        EinschreibungStatus.ANGEFORDERT, EinschreibungStatus.STORNO_ANGEFORDERT, Instant.now())
                .page(0, limit).list().stream().map(x -> x.id).toList();
    }

    /** Zieht alle fälligen Einschreibungen und verarbeitet sie einzeln. Liefert die Anzahl. */
    public int verarbeiteFaellige(int limit) {
        List<Long> ids = faelligeIds(limit);
        for (Long id : ids) {
            verarbeite(id);
        }
        return ids.size();
    }

    /**
     * Verarbeitet genau eine Einschreibung in eigener Transaktion (Pessimistic-Lock gegen Doppel-
     * Zustellung). Enrol bzw. Unenrol via {@link OpenolatProvisioning}; Erfolg → EINGESCHRIEBEN/
     * AUSGESCHRIEBEN, Fehlschlag → Versuch++ + Backoff, nach {@link Kurseinschreibung#MAX_VERSUCHE}
     * → FEHLGESCHLAGEN (Dead-Letter, HITL im Cockpit).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void verarbeite(Long einschreibungId) {
        Kurseinschreibung e = Kurseinschreibung.findById(einschreibungId, LockModeType.PESSIMISTIC_WRITE);
        if (e == null || !e.istFaellig(Instant.now())) {
            return; // schon erledigt / von anderem Lauf gegriffen / noch nicht fällig
        }
        boolean storno = e.status == EinschreibungStatus.STORNO_ANGEFORDERT;
        e.versuche++;
        try {
            if (storno) {
                if (e.openolatIdentityKey != null) {
                    openolat.ausschreiben(e.wbtKurs.openolatKey, e.openolatIdentityKey);
                }
                e.status = EinschreibungStatus.AUSGESCHRIEBEN;
            } else {
                long identityKey = openolat.ensureUserUndEnrol(
                        e.keycloakSub, e.email, e.anzeigeName, e.wbtKurs.openolatKey);
                e.openolatIdentityKey = identityKey;
                e.status = EinschreibungStatus.EINGESCHRIEBEN;
            }
            e.erledigtAm = Instant.now();
            e.letzterFehler = null;
        } catch (RuntimeException ex) {
            e.letzterFehler = kurz(ex.getMessage());
            if (e.versuche >= Kurseinschreibung.MAX_VERSUCHE) {
                e.status = EinschreibungStatus.FEHLGESCHLAGEN; // Dead-Letter → HITL
                LOG.errorf("Einschreibung %d eskaliert nach %d Versuchen: %s", e.id, e.versuche, e.letzterFehler);
            } else {
                e.naechsterVersuchAm = Instant.now().plus(backoff(e.versuche));
                LOG.warnf("Einschreibung %d Versuch %d fehlgeschlagen (%s) → Retry %s",
                        e.id, e.versuche, e.letzterFehler, e.naechsterVersuchAm);
            }
        }
        // SERVICE_TASK in der Provisionierungs-Phase (Jaeger + BPMN), demselben Fall zugeordnet.
        prozess.schritt(fall(e), (storno ? "WBT-Zugang in OpenOLAT entziehen" : "WBT-Einschreibung nach OpenOLAT"),
                Akteur.SYSTEM, Prozess.System.OPENOLAT, Typ.SERVICE_TASK, Phase.PROVISIONIERUNG,
                Phase.PROVISIONIERUNG.name());
    }

    /** Manueller Neuversuch eines Dead-Letter-Eintrags (HITL-Aktion aus dem Cockpit). */
    @Transactional
    public Kurseinschreibung neuVersuch(Long einschreibungId) {
        Kurseinschreibung e = Kurseinschreibung.findById(einschreibungId);
        if (e == null || e.status != EinschreibungStatus.FEHLGESCHLAGEN) {
            return e;
        }
        // Richtung anhand des bisherigen Fortschritts: eingeschrieben? → Storno-Richtung, sonst Enrol.
        e.status = e.openolatIdentityKey != null && e.erledigtAm != null
                ? EinschreibungStatus.STORNO_ANGEFORDERT : EinschreibungStatus.ANGEFORDERT;
        e.versuche = 0;
        e.naechsterVersuchAm = Instant.now();
        return e;
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

    private static String fall(Kurseinschreibung e) {
        return (e.prozessFall == null || e.prozessFall.isBlank()) ? "unbekannt" : e.prozessFall;
    }

    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(Prozessspur.BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? null : fall;
    }
}
