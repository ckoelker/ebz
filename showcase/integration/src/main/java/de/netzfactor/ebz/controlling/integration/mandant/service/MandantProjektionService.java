package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.opentelemetry.api.baggage.Baggage;

import de.netzfactor.ebz.controlling.integration.mandant.model.IdpFoederation;
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
    KeycloakOrganizationsProvisioning keycloakOrg;

    @Inject
    Prozessspur prozess;

    /**
     * Fordert — idempotent — die OpenOLAT-Org-Projektion des Mandanten an (M2). Ein bereits offener
     * {@code ORG_ANLEGEN}-Auftrag wird wiederverwendet (kein Dublett). Aufruf <b>innerhalb</b> der
     * auslösenden Transaktion.
     */
    @Transactional
    public MandantProjektion anfordern(Long mandantId) {
        return anfordern(mandantId, Operation.ORG_ANLEGEN, "Org-Projektion anfordern",
                Phase.MANDANT_ORG_PROJEKTION);
    }

    /**
     * Fordert — idempotent — die Keycloak-Organization-Projektion des föderierten B2B-Mandanten an (M3).
     * Quelle des Domain→Mandant-Routings und des {@code mandant}-Claims (K4). Aufruf <b>innerhalb</b> der
     * auslösenden Transaktion.
     */
    @Transactional
    public MandantProjektion anfordernKeycloakOrg(Long mandantId) {
        return anfordern(mandantId, Operation.KEYCLOAK_ORG_ANLEGEN, "Keycloak-Org-Projektion anfordern",
                Phase.MANDANT_IDP_FOEDERATION);
    }

    /** Gemeinsame, idempotent deduplizierende Enqueue-Logik je Mandant×Operation (Outbox). */
    private MandantProjektion anfordern(Long mandantId, Operation op, String spanLabel, Phase phase) {
        Mandant m = Mandant.findById(mandantId);
        if (m == null) {
            throw new IllegalArgumentException("Unbekannter Mandant " + mandantId);
        }
        MandantProjektion offen = MandantProjektion
                .find("mandant = ?1 and operation = ?2 and status = ?3", m, op, Status.ANGEFORDERT)
                .firstResult();
        if (offen != null) {
            return offen;
        }
        MandantProjektion p = new MandantProjektion();
        p.mandant = m;
        p.operation = op;
        p.status = Status.ANGEFORDERT;
        p.versuche = 0;
        p.naechsterVersuchAm = Instant.now();
        p.erstelltAm = Instant.now();
        p.prozessFall = aktuellerFall();
        p.persist();
        // SERVICE_TASK: der aktivierte Mandant wird zur Projektions-Anforderung (Outbox).
        prozess.schritt(spanLabel, Akteur.SYSTEM, Prozess.System.BACKEND, Typ.SERVICE_TASK, phase);
        LOG.infof("Projektion %s angefordert für Mandant %d (%s)", op, mandantId, m.schluessel);
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
            switch (p.operation) {
                case ORG_ANLEGEN -> m.openolatOrganisationKey =
                        openolatOrg.ensureOrganisation(m.schluessel, m.anzeigeName, cssKlasse(m));
                case KEYCLOAK_ORG_ANLEGEN -> m.keycloakOrganizationId =
                        keycloakOrg.ensureOrganization(m.schluessel, m.anzeigeName, sammleDomains(m));
            }
            p.status = Status.ERLEDIGT;
            p.erledigtAm = Instant.now();
            p.letzterFehler = null;
        } catch (RuntimeException ex) {
            p.letzterFehler = kurz(ex.getMessage());
            if (p.versuche >= MandantProjektion.MAX_VERSUCHE) {
                p.status = Status.FEHLGESCHLAGEN; // Dead-Letter → HITL
                LOG.errorf("Projektion %d (%s) eskaliert nach %d Versuchen: %s",
                        p.id, p.operation, p.versuche, p.letzterFehler);
            } else {
                p.naechsterVersuchAm = Instant.now().plus(backoff(p.versuche));
                LOG.warnf("Projektion %d (%s) Versuch %d fehlgeschlagen (%s) → Retry %s",
                        p.id, p.operation, p.versuche, p.letzterFehler, p.naechsterVersuchAm);
            }
        }
        // SERVICE_TASK im Zielsystem (Jaeger + BPMN), dem gespeicherten Fall zugeordnet.
        boolean keycloak = p.operation == Operation.KEYCLOAK_ORG_ANLEGEN;
        prozess.schritt(fall(p), keycloak ? "Mandant-Org in Keycloak anlegen" : "Mandant-Org in OpenOLAT anlegen",
                Akteur.SYSTEM, keycloak ? Prozess.System.KEYCLOAK : Prozess.System.OPENOLAT,
                Typ.SERVICE_TASK, keycloak ? Phase.MANDANT_IDP_FOEDERATION : Phase.MANDANT_ORG_PROJEKTION);
    }

    /**
     * Sammelt die eindeutigen E-Mail-Domains aller {@link IdpFoederation}en des Mandanten (Semikolon-/Komma-
     * separiert, lowercase) — die Routing-Domains der Keycloak-Organization (M3).
     */
    private static List<String> sammleDomains(Mandant m) {
        List<String> domains = new ArrayList<>();
        for (IdpFoederation f : IdpFoederation.<IdpFoederation>list("mandant", m)) {
            if (f.emailDomains == null) {
                continue;
            }
            for (String d : f.emailDomains.split("[;,]")) {
                String dom = d.trim().toLowerCase();
                if (!dom.isEmpty() && !domains.contains(dom)) {
                    domains.add(dom);
                }
            }
        }
        return domains;
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
