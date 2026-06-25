package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.opentelemetry.api.baggage.Baggage;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion;
import de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion.Operation;
import de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion.Status;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Schreibt und verarbeitet {@link MandantProjektion}en — die Outbox-Naht der Mandanten-Schicht
 * (gleiches Muster wie {@code KurseinschreibungService}, eigene Tabelle). {@link #anfordern} läuft in der
 * auslösenden Geschäftstransaktion (Anforderung atomar mit dem Outbox-Zustand, idempotent dedupliziert je
 * Mandant×Operation); {@link #verarbeite} läuft je Zeile in eigener Transaktion ({@code REQUIRES_NEW} +
 * Zeilen-Lock), ruft {@link OpenolatOrganisationProvisioning} und führt Erfolg/Backoff/Dead-Letter nach.
 * Jeder Versuch emittiert einen {@link Prozessspur}-Span (Phase {@link Phase#MANDANT_ORG_PROJEKTION}).
 */
@ApplicationScoped
public class MandantProjektionService {

    private static final Logger LOG = Logger.getLogger(MandantProjektionService.class);

    @Inject
    OpenolatOrganisationProvisioning openolatOrg;

    @Inject
    Prozessspur prozess;

    /**
     * Fordert — idempotent — die Org-Projektion des Mandanten an. Ein bereits offener {@code ORG_ANLEGEN}-
     * Auftrag wird wiederverwendet (kein Dublett). Aufruf <b>innerhalb</b> der auslösenden Transaktion.
     */
    @Transactional
    public MandantProjektion anfordern(Long mandantId) {
        Mandant m = Mandant.findById(mandantId);
        if (m == null) {
            throw new IllegalArgumentException("Unbekannter Mandant " + mandantId);
        }
        MandantProjektion offen = MandantProjektion
                .find("mandant = ?1 and operation = ?2 and status = ?3", m, Operation.ORG_ANLEGEN, Status.ANGEFORDERT)
                .firstResult();
        if (offen != null) {
            return offen;
        }
        MandantProjektion p = new MandantProjektion();
        p.mandant = m;
        p.operation = Operation.ORG_ANLEGEN;
        p.status = Status.ANGEFORDERT;
        p.versuche = 0;
        p.naechsterVersuchAm = Instant.now();
        p.erstelltAm = Instant.now();
        p.prozessFall = aktuellerFall();
        p.persist();
        // SERVICE_TASK: der aktivierte Mandant wird zur Org-Projektions-Anforderung (Outbox).
        prozess.schritt("Org-Projektion anfordern", Akteur.SYSTEM, Prozess.System.BACKEND,
                Typ.SERVICE_TASK, Phase.MANDANT_ORG_PROJEKTION);
        LOG.infof("Org-Projektion angefordert für Mandant %d (%s)", mandantId, m.schluessel);
        return p;
    }

    /** IDs aller fälligen Projektionen (offen + Zeit erreicht). */
    @Transactional
    public List<Long> faelligeIds(int limit) {
        return MandantProjektion.<MandantProjektion>find(
                        "status = ?1 and naechsterVersuchAm <= ?2", Status.ANGEFORDERT, Instant.now())
                .page(0, limit).list().stream().map(x -> x.id).toList();
    }

    /** Zieht alle fälligen Projektionen und verarbeitet sie einzeln. Liefert die Anzahl. */
    public int verarbeiteFaellige(int limit) {
        List<Long> ids = faelligeIds(limit);
        for (Long id : ids) {
            verarbeite(id);
        }
        return ids.size();
    }

    /**
     * Verarbeitet genau eine Projektion in eigener Transaktion (Pessimistic-Lock gegen Doppel-Zustellung):
     * legt die OpenOLAT-Organisation an (idempotent über {@code externalId}) und schreibt
     * {@code openolatOrganisationKey} zurück. Fehlschlag → Versuch++ + Backoff, nach {@link
     * MandantProjektion#MAX_VERSUCHE} → {@code FEHLGESCHLAGEN} (Dead-Letter, HITL).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void verarbeite(Long projektionId) {
        MandantProjektion p = MandantProjektion.findById(projektionId, LockModeType.PESSIMISTIC_WRITE);
        if (p == null || !p.istFaellig(Instant.now())) {
            return; // schon erledigt / von anderem Lauf gegriffen / noch nicht fällig
        }
        Mandant m = p.mandant;
        p.versuche++;
        try {
            long orgKey = openolatOrg.ensureOrganisation(m.schluessel, m.anzeigeName, cssKlasse(m));
            m.openolatOrganisationKey = orgKey;
            p.status = Status.ERLEDIGT;
            p.erledigtAm = Instant.now();
            p.letzterFehler = null;
        } catch (RuntimeException ex) {
            p.letzterFehler = kurz(ex.getMessage());
            if (p.versuche >= MandantProjektion.MAX_VERSUCHE) {
                p.status = Status.FEHLGESCHLAGEN; // Dead-Letter → HITL
                LOG.errorf("Org-Projektion %d eskaliert nach %d Versuchen: %s", p.id, p.versuche, p.letzterFehler);
            } else {
                p.naechsterVersuchAm = Instant.now().plus(backoff(p.versuche));
                LOG.warnf("Org-Projektion %d Versuch %d fehlgeschlagen (%s) → Retry %s",
                        p.id, p.versuche, p.letzterFehler, p.naechsterVersuchAm);
            }
        }
        // SERVICE_TASK in OpenOLAT (Jaeger + BPMN), dem gespeicherten Fall zugeordnet.
        prozess.schritt(fall(p), "Mandant-Org in OpenOLAT anlegen", Akteur.SYSTEM, Prozess.System.OPENOLAT,
                Typ.SERVICE_TASK, Phase.MANDANT_ORG_PROJEKTION);
    }

    /** Manueller Neuversuch eines Dead-Letter-Auftrags (HITL-Aktion aus dem Cockpit). */
    @Transactional
    public MandantProjektion neuVersuch(Long projektionId) {
        MandantProjektion p = MandantProjektion.findById(projektionId);
        if (p == null || p.status != Status.FEHLGESCHLAGEN) {
            return p;
        }
        p.status = Status.ANGEFORDERT;
        p.versuche = 0;
        p.naechsterVersuchAm = Instant.now();
        return p;
    }

    /**
     * Per-Org-CSS-Klasse (M0-Branding). Der EBZ-Kernmandant (B2C) bleibt bewusst auf dem globalen
     * Default-Theme → keine cssClass; alle übrigen bekommen {@code mandant-<schluessel>} (kleingeschrieben).
     */
    static String cssKlasse(Mandant m) {
        if (m.vertragstyp == Mandant.Vertragstyp.EBZ_CUSTOMER) {
            return null;
        }
        return "mandant-" + m.schluessel.toLowerCase().replace('_', '-');
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

    private static String fall(MandantProjektion p) {
        return (p.prozessFall == null || p.prozessFall.isBlank()) ? "unbekannt" : p.prozessFall;
    }

    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(Prozessspur.BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? null : fall;
    }
}
