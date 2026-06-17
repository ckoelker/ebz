package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Person↔Person-Beziehung (Plan A5): gerichtet von {@link #personA} zu {@link #personB} mit
 * {@link #typ}-{@link Lookups.Beziehungstyp Lookup} (Erziehungsberechtigt/Notfallkontakt/…). Trägt u. a.
 * die Eltern-/Sorgerechts-Einwilligung bei Minderjährigen ({@link Lookups.Beziehungstyp#sorgerecht}).
 */
@Audited
@Entity
@Table(name = "beziehung", schema = "mdm")
public class Beziehung extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_a_id", nullable = false)
    public Person personA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_b_id", nullable = false)
    public Person personB;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "typ_id", nullable = false)
    public Lookups.Beziehungstyp typ;

    @Column(name = "gueltig_von")
    public LocalDate gueltigVon;

    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    @Column(name = "notiz", length = 300)
    public String notiz;
}
