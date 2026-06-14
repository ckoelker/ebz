package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * E-Mail-Adresse einer {@link Person} — eigener Datensatz, weil ein Mensch mehrere führt (privat +
 * dienstlich). Die <b>globale Unique-Constraint auf {@link #email} ist der Dreh- und Angelpunkt</b>:
 * sie erzwingt „eine E-Mail → genau eine Person" und macht damit die idempotente Identitäts-Auflösung
 * (firmenseitige Vor-Anlage vs. private Selbstregistrierung mit derselben Adresse) eindeutig.
 */
@Entity
@Table(name = "person_email", schema = "party")
public class PersonEmail extends PanacheEntity {

    @Column(name = "person_id", nullable = false)
    public Long personId;

    @Column(name = "email", nullable = false, unique = true, length = 200)
    public String email;

    /** Verifiziert, sobald der Mensch die Adresse beim Selbst-Login bestätigt hat (Account-Claiming). */
    @Column(name = "verifiziert", nullable = false)
    public boolean verifiziert;

    @Column(name = "primaer", nullable = false)
    public boolean primaer;
}
