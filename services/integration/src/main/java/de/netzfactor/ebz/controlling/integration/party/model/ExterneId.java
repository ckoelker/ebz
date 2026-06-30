package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Externe ID / Alias (Plan A14): generische Zuordnung einer Party zu einer Fremdsystem-Identität
 * (HubSpot/HsTAG/SchILD/LMS/Debitor). Besitzer = {@link #person} ODER {@link #organisation} (echte
 * nullable FKs). Eindeutig je {@code (quelle, externeId)}, damit ein Fremd-Schlüssel genau eine Party trifft.
 */
@Entity
@Table(name = "externe_id", schema = "mdm",
        uniqueConstraints = @UniqueConstraint(name = "uk_externe_id_quelle_extid", columnNames = {"quelle", "externe_id"}))
public class ExterneId extends PanacheEntity {

    public enum Quelle {
        HUBSPOT, HSTAG, SCHILD, LMS, DEBITOR, VENDURE
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", nullable = false, length = 16)
    public Quelle quelle;

    @Column(name = "externe_id", nullable = false, length = 200)
    public String externeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;
}
