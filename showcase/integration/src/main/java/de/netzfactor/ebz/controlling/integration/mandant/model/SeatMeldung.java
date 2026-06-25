package de.netzfactor.ebz.controlling.integration.mandant.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * HITL-Meldung über eine Seat-Überschreitung eines B2B-{@link Mandant}en (M5/E4). Der Seat-Cap ist
 * <b>weich</b>: ein überzähliger Nutzer wird durchgelassen, aber <b>jede</b> Überschreitung erzeugt eine
 * solche Meldung, die intern bestätigt werden muss (Nachverkauf von Seats bzw. bewusstes Dulden). So
 * bleibt die Belegung sichtbar, ohne den Login-Hot-Path zu blockieren (E1).
 * <p>
 * Flach im Schema {@code mdm}, echte FK auf {@link Mandant}. {@link #belegungBeiMeldung} ist die Belegung
 * <i>nach</i> der überschreitenden Aufnahme; {@link #seatLimit} der zum Zeitpunkt geltende Vertragswert.
 */
@Entity
@Table(name = "seat_meldung", schema = "mdm")
public class SeatMeldung extends PanacheEntity {

    /** Bearbeitungsstand der HITL-Meldung. */
    public enum Status {
        OFFEN, BESTAETIGT
    }

    @Version
    public long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mandant_id", nullable = false)
    public Mandant mandant;

    @Column(name = "belegung_bei_meldung", nullable = false)
    public int belegungBeiMeldung;

    @Column(name = "seat_limit", nullable = false)
    public int seatLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.OFFEN;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "bestaetigt_am")
    public Instant bestaetigtAm;

    /** Wer die Überbuchung intern bestätigt hat (Sachbearbeiter-Kennung). */
    @Column(name = "bestaetigt_von", length = 120)
    public String bestaetigtVon;
}
