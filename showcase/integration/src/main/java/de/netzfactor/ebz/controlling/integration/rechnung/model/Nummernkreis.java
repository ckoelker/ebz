package de.netzfactor.ebz.controlling.integration.rechnung.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Lückenloser Nummernkreis je {@code Bereich × Belegart}. Die Vergabe erfolgt atomar über einen
 * pessimistischen Lock auf der Zeile (kein DB-Sequence-Loch durch Rollbacks) — siehe
 * {@code NummernkreisService}.
 */
@Entity
@Table(name = "nummernkreis", schema = "rechnung",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bereich", "belegart"}))
public class Nummernkreis extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "bereich", nullable = false, length = 16)
    public Bereich bereich;

    @Enumerated(EnumType.STRING)
    @Column(name = "belegart", nullable = false, length = 16)
    public Belegart belegart;

    /** Präfix der formatierten Nummer, z. B. "RE-BS-2026-". */
    @Column(name = "praefix", nullable = false, length = 32)
    public String praefix;

    /** Nächste laufende Nummer (wird bei jeder Vergabe inkrementiert). */
    @Column(name = "naechste_nummer", nullable = false)
    public long naechsteNummer = 1;
}
