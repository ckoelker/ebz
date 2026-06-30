package de.netzfactor.ebz.controlling.integration.mandant.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Seat-Lizenz eines B2B-{@link Mandant}en ({@code ENTERPRISE_FLAT}): wie viele aktive Org-Mitglieder der
 * Kunde gleichzeitig führen darf ({@link #seatLimit}). Der EBZ-Kernmandant (B2C) und EBZ-Staff haben
 * <b>kein</b> Limit → für sie existiert kein Lizenzvertrag.
 * <p>
 * Die Cap wird bei der Provisionierung/Org-Aufnahme geprüft, <b>nicht</b> im Login-Hot-Path (E1); eine
 * Überschreitung wird <b>weich durchgelassen</b> und erzeugt eine verpflichtende HITL-Meldung (E4, M5).
 * Flach im Schema {@code mdm}, echte FK auf {@link Mandant}.
 */
@Entity
@Table(name = "lizenzvertrag", schema = "mdm")
public class Lizenzvertrag extends PanacheEntity {

    @Version
    public long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mandant_id", nullable = false)
    public Mandant mandant;

    /** Bezahlte Sitzplätze (aktive Org-Mitglieder, E2); Überschreitung = weich + HITL (E4). */
    @Column(name = "seat_limit", nullable = false)
    public int seatLimit;

    @Column(name = "gueltig_von", nullable = false)
    public LocalDate gueltigVon;

    /** Vertragsende (leer = unbefristet). */
    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    @Column(name = "aktiv", nullable = false)
    public boolean aktiv = true;
}
