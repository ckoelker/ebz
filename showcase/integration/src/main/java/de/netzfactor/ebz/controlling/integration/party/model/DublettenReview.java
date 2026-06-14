package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Audit einer HITL-Dubletten-Entscheidung: <i>wer</i> hat <i>wann</i> für welchen Kandidaten
 * <i>was</i> entschieden — inkl. <b>Snapshot des KI-Urteils</b> (Ähnlichkeit/Einschätzung/Begründung),
 * auf dessen Basis entschieden wurde. Macht die menschliche Entscheidung nachvollziehbar (Governance).
 */
@Entity
@Table(name = "dubletten_review", schema = "party")
public class DublettenReview extends PanacheEntity {

    /** {@code FIRMA} | {@code PERSON} — auf welche Party-Art sich die Entscheidung bezieht. */
    @Column(name = "art", nullable = false, length = 16)
    public String art;

    /** Die geprüfte (neue/provisorische) Party. */
    @Column(name = "kandidat_id", nullable = false)
    public Long kandidatId;

    /** Ziel-Party bei einem Merge; {@code null} bei bestätigter Neuanlage. */
    @Column(name = "ziel_id")
    public Long zielId;

    /** {@code NEUANLAGE_BESTAETIGT} | {@code GEMERGT}. */
    @Column(name = "entscheidung", nullable = false, length = 32)
    public String entscheidung;

    @Column(name = "ki_aehnlichkeit")
    public Double kiAehnlichkeit;

    @Column(name = "ki_einschaetzung", length = 16)
    public String kiEinschaetzung;

    @Column(name = "ki_begruendung", length = 1000)
    public String kiBegruendung;

    /** Token-{@code sub}/Kennung der entscheidenden Sachbearbeitung. */
    @Column(name = "entschieden_von", length = 100)
    public String entschiedenVon;

    @Column(name = "entschieden_am", nullable = false)
    public Instant entschiedenAm;
}
