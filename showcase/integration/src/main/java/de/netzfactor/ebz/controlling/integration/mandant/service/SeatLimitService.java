package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.mandant.model.Lizenzvertrag;
import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.model.SeatMeldung;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Seat-Cap der B2B-Mandanten (M5). Der Cap ist <b>weich</b> (E4): bei der Aufnahme eines Org-Mitglieds wird
 * die aktive Belegung gegen das {@link Lizenzvertrag#seatLimit} geprüft (E1/E2 — nicht im Login-Hot-Path);
 * eine Überschreitung wird <b>durchgelassen</b>, erzeugt aber <b>je Überschreitung erneut</b> eine
 * {@link SeatMeldung} (HITL, intern zu bestätigen). EBZ-Kontexte ({@code EBZ_CUSTOMER}/{@code EBZ_STAFF})
 * und Mandanten ohne aktiven Lizenzvertrag gelten als unbegrenzt.
 * <p>
 * Die aktive Belegung = Anzahl der OpenOLAT-Org-Mitglieder in der Seat-Rolle ({@code user}, E2), gezählt
 * über {@link OpenolatOrganisationProvisioning#zaehleMitglieder} (im Test gemockt).
 */
@ApplicationScoped
public class SeatLimitService {

    private static final Logger LOG = Logger.getLogger(SeatLimitService.class);

    /** OpenOLAT-Organisationsrolle, die als „belegter Sitzplatz" zählt. */
    public static final String SEAT_ROLLE = "user";

    @Inject
    OpenolatOrganisationProvisioning openolatOrg;

    @Inject
    Prozessspur prozess;

    /** Entscheidung der Aufnahme-Prüfung. */
    public enum Entscheidung {
        UNBEGRENZT, INNERHALB, UEBERBUCHT
    }

    /**
     * Ergebnis einer Aufnahme-Prüfung. {@code seatLimit} ist {@code null} bei unbegrenzten Mandanten;
     * {@code meldungId} ist gesetzt, wenn die Aufnahme eine HITL-{@link SeatMeldung} ausgelöst hat.
     */
    public record SeatAufnahme(Entscheidung entscheidung, int belegungVorher, Integer seatLimit, Long meldungId) {
    }

    /** Aktive Belegung (Org-Mitglieder in der Seat-Rolle); 0, solange keine Org projiziert ist. */
    public int belegung(Mandant m) {
        return m.openolatOrganisationKey == null ? 0
                : openolatOrg.zaehleMitglieder(m.openolatOrganisationKey, SEAT_ROLLE);
    }

    /** Aktiver Lizenzvertrag des Mandanten (oder {@code null} → unbegrenzt). */
    public Lizenzvertrag aktiveLizenz(Mandant m) {
        return Lizenzvertrag.find("mandant = ?1 and aktiv = true", m).firstResult();
    }

    /**
     * Prüft die Aufnahme eines weiteren Mitglieds (E1: am Provisionierungs-Punkt). Bei
     * {@code belegung >= seatLimit} wird die Aufnahme als Überbuchung gewertet — durchgelassen, aber eine
     * HITL-{@link SeatMeldung} angelegt. Aufruf in der Geschäftstransaktion.
     */
    @Transactional
    public SeatAufnahme aufnahmePruefen(Long mandantId) {
        Mandant m = Mandant.findById(mandantId);
        if (m == null) {
            throw new IllegalArgumentException("Unbekannter Mandant " + mandantId);
        }
        if (!m.istSeatBegrenzt()) {
            return new SeatAufnahme(Entscheidung.UNBEGRENZT, 0, null, null);
        }
        Lizenzvertrag lz = aktiveLizenz(m);
        if (lz == null) {
            return new SeatAufnahme(Entscheidung.UNBEGRENZT, 0, null, null); // kein Vertrag → kein Limit
        }
        int belegung = belegung(m);
        if (belegung >= lz.seatLimit) {
            SeatMeldung meld = new SeatMeldung();
            meld.mandant = m;
            meld.belegungBeiMeldung = belegung + 1; // Belegung nach der überzähligen Aufnahme
            meld.seatLimit = lz.seatLimit;
            meld.status = SeatMeldung.Status.OFFEN;
            meld.erstelltAm = Instant.now();
            meld.persist();
            // BUSINESS_RULE: die Seat-Cap-Regel feuert → HITL-Meldung (intern zu bestätigen).
            prozess.schritt("Seat-Überschreitung melden (HITL)", Akteur.SYSTEM, Prozess.System.BACKEND,
                    Typ.BUSINESS_RULE, Phase.MANDANT_SEAT_VERWALTUNG);
            LOG.warnf("Seat-Überbuchung Mandant %d (%s): Belegung %d > Limit %d → HITL-Meldung %d",
                    mandantId, m.schluessel, belegung + 1, lz.seatLimit, meld.id);
            return new SeatAufnahme(Entscheidung.UEBERBUCHT, belegung, lz.seatLimit, meld.id);
        }
        return new SeatAufnahme(Entscheidung.INNERHALB, belegung, lz.seatLimit, null);
    }

    /** Bestätigt eine offene Überbuchungs-Meldung (HITL-Aktion). */
    @Transactional
    public SeatMeldung bestaetige(Long meldungId, String bearbeiter) {
        SeatMeldung meld = SeatMeldung.findById(meldungId);
        if (meld == null || meld.status != SeatMeldung.Status.OFFEN) {
            return meld;
        }
        meld.status = SeatMeldung.Status.BESTAETIGT;
        meld.bestaetigtAm = Instant.now();
        meld.bestaetigtVon = bearbeiter;
        prozess.schritt("Seat-Überbuchung bestätigen", Akteur.EBZ, Prozess.System.COCKPIT,
                Typ.USER_TASK, Phase.MANDANT_SEAT_VERWALTUNG);
        return meld;
    }

    public List<SeatMeldung> offeneMeldungen() {
        return SeatMeldung.list("status", SeatMeldung.Status.OFFEN);
    }

    public long offeneMeldungenAnzahl(Mandant m) {
        return SeatMeldung.count("mandant = ?1 and status = ?2", m, SeatMeldung.Status.OFFEN);
    }
}
