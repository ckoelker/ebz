package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Mitgliedschaft = die <b>N:M-Verknüpfung Person × Organisation</b> mit Rolle und Gültigkeit. Damit
 * kann dieselbe Person gleichzeitig Azubi-Ansprechpartner bei Firma A und Aufsichtsrat bei Firma B
 * sein, und über die Zeit wechseln. {@link #buchungsberechtigt} entscheidet, ob die Person <i>im
 * Auftrag</i> dieser Organisation bestellen darf — das speist die wählbaren Bestellkontexte.
 */
@Entity
@Table(name = "mitgliedschaft", schema = "mdm",
        uniqueConstraints = @UniqueConstraint(columnNames = {"person_id", "organisation_id", "rolle"}))
public class Mitgliedschaft extends PanacheEntity {

    /** Rolle der Person in der Organisation. Buchende Rollen (Ausbilder, Seminarbucher, …) vs. reine
     *  Teilnehmer-Rollen (Azubi, Student) — Letztere lösen i. d. R. keine eigene Bestellberechtigung aus. */
    public enum Rolle {
        AUSBILDER, ANSPRECHPARTNER_STUDIUM, SEMINAR_BUCHER, AZUBI, STUDENT, AUFSICHTSRAT
    }

    @Version
    public long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    public Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(name = "rolle", nullable = false, length = 32)
    public Rolle rolle;

    /** Darf im Auftrag dieser Organisation bestellen → Bestellkontext „im Auftrag von …". */
    @Column(name = "buchungsberechtigt", nullable = false)
    public boolean buchungsberechtigt;

    @Column(name = "gueltig_von")
    public LocalDate gueltigVon;

    /** {@code null} = offen/unbefristet. */
    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    /** Abgeleitete FK-IDs (View-/Mapping-Komfort; greifen ohne Proxy-Init nur die {@code id} ab). */
    public Long personId() {
        return person == null ? null : person.id;
    }

    public Long organisationId() {
        return organisation == null ? null : organisation.id;
    }
}
