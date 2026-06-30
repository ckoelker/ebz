package de.netzfactor.ebz.controlling.integration.rechnung.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Alias-Layer der Debitoren-Hoheit (R3, Weg A: minimal-invasiv statt Renumbering). Bildet eine
 * <b>externe/alte</b> Debitorennummer (je Quellsystem) auf den Golden-Record im Billing ab. So lösen
 * sich die historischen Doppel-Debitoren auf: jede Altnummer bleibt nachschlagbar, zeigt aber auf
 * <i>einen</i> Debitor. Auch beim Merge wird die unterlegene Nummer hier als Alias konserviert.
 */
@Entity
@Table(name = "debitor_alias", schema = "mdm",
        uniqueConstraints = @UniqueConstraint(columnNames = {"quelle", "externe_nr"}))
public class DebitorAlias extends PanacheEntity {

    /** Golden-Record, auf den diese Altnummer zeigt. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debitor_id", nullable = false)
    public Debitor debitor;

    /** Quellsystem/Kontext der Altnummer, z. B. "DATEV-Alt", "MERGE", "BESTAND". */
    @Column(name = "quelle", nullable = false, length = 40)
    public String quelle;

    /** Die externe/alte Debitorennummer im Quellsystem. */
    @Column(name = "externe_nr", nullable = false, length = 64)
    public String externeNr;

    /** Abgeleitete FK-ID (View-/Mapping-Komfort). */
    public Long debitorId() {
        return debitor == null ? null : debitor.id;
    }
}
