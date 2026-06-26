package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.model.LernleistungsFakt;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;
import de.netzfactor.ebz.controlling.integration.mandant.service.OpenolatNachweisProvisioning.CompletionVO;
import de.netzfactor.ebz.controlling.integration.mandant.service.OpenolatNachweisProvisioning.KursRef;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Nachweis-Seam (M6/K6): projiziert die in OpenOLAT (System-of-Record) gehaltene Completion eines WBT auf
 * einen kanonischen {@link LernleistungsFakt} mit den rechtlich maßgeblichen <b>Soll-Stunden</b> (F1/F2) —
 * NICHT der unzuverlässigen SCORM-{@code session_time}.
 * <p>
 * Drei Schritte, jeder per OpenOLAT-REST (kein UI, das Inhalts-Nugget bleibt unangetastet):
 * <ol>
 *   <li>{@link #ensureNachweisKurs} — den trackbaren Nachweis-Kurs (mit Assessment-Knoten) bereitstellen,
 *       idempotent; Schlüssel am {@link WbtKurs} merken.</li>
 *   <li>{@link #meldeAbschluss} — den Abschluss eines Lernenden in OpenOLAT festhalten (Completion-Event).</li>
 *   <li>{@link #synchronisiere} — die Completion lesen und als {@link LernleistungsFakt} ins MDM projizieren
 *       (idempotent je Einschreibung).</li>
 * </ol>
 */
@ApplicationScoped
public class LernnachweisService {

    private static final Logger LOG = Logger.getLogger(LernnachweisService.class);

    @Inject
    OpenolatNachweisProvisioning provisioning;

    @Inject
    Prozessspur prozess;

    /** Stellt den trackbaren Nachweis-Kurs zum WBT sicher (idempotent) und liefert die {@link KursRef}. */
    @Transactional
    public KursRef ensureNachweisKurs(Long wbtKursId) {
        WbtKurs wbt = WbtKurs.findById(wbtKursId);
        if (wbt == null) {
            throw new IllegalArgumentException("Unbekannter WbtKurs " + wbtKursId);
        }
        return ensureFuer(wbt);
    }

    private KursRef ensureFuer(WbtKurs wbt) {
        if (wbt.openolatNachweisKursId != null && wbt.openolatNachweisNodeId != null) {
            return new KursRef(wbt.openolatNachweisKursId, wbt.openolatNachweisNodeId);
        }
        KursRef ref = provisioning.ensureNachweisKurs("WBT-NACHWEIS-" + wbt.code, "Nachweis: " + wbt.titel);
        wbt.openolatNachweisKursId = ref.courseId();
        wbt.openolatNachweisNodeId = ref.nodeId();
        prozess.schritt("Nachweis-Kurs bereitstellen", Akteur.SYSTEM, Prozess.System.OPENOLAT,
                Typ.SERVICE_TASK, Phase.MANDANT_NACHWEIS);
        LOG.infof("Nachweis-Kurs für WBT %s bereit (courseId %d, node %s)", wbt.code, ref.courseId(), ref.nodeId());
        return ref;
    }

    /**
     * Hält den Abschluss eines Lernenden (seiner {@link Kurseinschreibung}) in OpenOLAT fest — der
     * Completion-Event am Nachweis-Knoten. Voraussetzung: die Einschreibung ist in OpenOLAT provisioniert
     * ({@link Kurseinschreibung#openolatIdentityKey} gesetzt). Macht den Kurs zugleich der Mandant-Org sichtbar.
     */
    @Transactional
    public void meldeAbschluss(Long einschreibungId, boolean bestanden) {
        Kurseinschreibung e = Kurseinschreibung.findById(einschreibungId);
        if (e == null) {
            throw new IllegalArgumentException("Unbekannte Einschreibung " + einschreibungId);
        }
        if (e.openolatIdentityKey == null) {
            throw new IllegalStateException("Einschreibung " + einschreibungId + " ist in OpenOLAT noch nicht provisioniert");
        }
        KursRef ref = ensureFuer(e.wbtKurs);
        if (e.mandant != null && e.mandant.openolatOrganisationKey != null) {
            provisioning.linkKursZuOrg(ref.courseId(), e.mandant.openolatOrganisationKey);
        }
        provisioning.meldeCompletion(ref.courseId(), ref.nodeId(), e.openolatIdentityKey, bestanden);
        prozess.schritt("WBT-Abschluss in OpenOLAT festhalten", Akteur.KUNDE, Prozess.System.OPENOLAT,
                Typ.USER_TASK, Phase.MANDANT_NACHWEIS);
    }

    /**
     * Liest die OpenOLAT-Completion der Einschreibung und projiziert sie als {@link LernleistungsFakt}
     * (Soll-Stunden-Snapshot). Idempotent: ein Fakt je Einschreibung wird angelegt/aktualisiert.
     * {@code null}, solange kein Abschluss vorliegt.
     */
    @Transactional
    public LernleistungsFakt synchronisiere(Long einschreibungId) {
        Kurseinschreibung e = Kurseinschreibung.findById(einschreibungId);
        if (e == null) {
            throw new IllegalArgumentException("Unbekannte Einschreibung " + einschreibungId);
        }
        if (e.openolatIdentityKey == null) {
            return null;
        }
        KursRef ref = ensureFuer(e.wbtKurs);
        CompletionVO c = provisioning.leseCompletion(ref.courseId(), ref.nodeId(), e.openolatIdentityKey);
        if (!c.vorhanden()) {
            return null;
        }
        LernleistungsFakt f = LernleistungsFakt.find("einschreibung", e).firstResult();
        boolean neu = f == null;
        if (neu) {
            f = new LernleistungsFakt();
            f.einschreibung = e;
            f.erfasstAm = Instant.now();
        }
        f.mandant = e.mandant;
        f.wbtKurs = e.wbtKurs;
        f.keycloakSub = e.keycloakSub;
        f.lernenderName = e.anzeigeName;
        f.bestanden = c.bestanden();
        f.abgeschlossenAm = c.abgeschlossenAm();
        f.sollStunden = e.wbtKurs.sollStundenAnrechenbar;
        if (neu) {
            f.persist();
        }
        prozess.schritt("Weiterbildungsnachweis erfassen (Soll-Stunden)", Akteur.SYSTEM, Prozess.System.BACKEND,
                Typ.BUSINESS_RULE, Phase.MANDANT_NACHWEIS);
        LOG.infof("LernleistungsFakt %s Einschreibung %d: bestanden=%s, sollStunden=%s, am=%s",
                neu ? "angelegt" : "aktualisiert", einschreibungId, f.bestanden, f.sollStunden, f.abgeschlossenAm);
        return f;
    }

    /** Nachweise (Fakten) eines Mandanten — K6-Report („in MDM lesbar"). */
    public List<LernleistungsFakt> faktenJeMandant(Long mandantId) {
        return LernleistungsFakt.list("mandant.id", mandantId);
    }

    public List<LernleistungsFakt> alleFakten() {
        return LernleistungsFakt.listAll();
    }
}
