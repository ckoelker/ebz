package de.netzfactor.ebz.controlling.integration.rechnung.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Nummernkreis für die <b>zentrale</b> Debitorennummern-Vergabe je {@link Bereich} (R3). Genau wie der
 * Beleg-Nummernkreis wird die Zeile bei der Vergabe pessimistisch gesperrt, sodass nebenläufige
 * Anlagen nie dieselbe Nummer ziehen — das ist die Wurzel gegen künftige Doppel-Debitoren.
 */
@Entity
@Table(name = "debitor_kreis", schema = "rechnung",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bereich"}))
public class DebitorKreis extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "bereich", nullable = false, length = 16)
    public Bereich bereich;

    /** Präfix der formatierten Debitorennummer, z. B. "BS-". */
    @Column(name = "praefix", nullable = false, length = 16)
    public String praefix;

    /** Nächste laufende Nummer (Start im Showcase bei 10001, wird bei jeder Vergabe inkrementiert). */
    @Column(name = "naechste_nummer", nullable = false)
    public long naechsteNummer = 10001;
}
