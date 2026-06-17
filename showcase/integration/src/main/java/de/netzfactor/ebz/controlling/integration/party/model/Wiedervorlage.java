package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Wiedervorlage / To-do (Plan A10): fällige Aufgabe mit Bezug, zugewiesen an <b>Mitarbeiter ODER
 * Gruppe</b> (echte nullable FKs). Erinnerung nur In-App (kein E-Mail-Versand). {@link #prioritaet}
 * steuert die Sortierung im Cockpit.
 */
@Entity
@Table(name = "wiedervorlage", schema = "mdm")
public class Wiedervorlage extends PanacheEntity {

    public enum Prioritaet {
        NIEDRIG, MITTEL, HOCH
    }

    @Column(name = "betreff", nullable = false, length = 200)
    public String betreff;

    @Column(name = "faellig_am", nullable = false)
    public LocalDate faelligAm;

    @Column(name = "erledigt", nullable = false)
    public boolean erledigt = false;

    @Column(name = "erledigt_am")
    public LocalDateTime erledigtAm;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioritaet", nullable = false, length = 16)
    public Prioritaet prioritaet = Prioritaet.MITTEL;

    // ── Zuweisung: Mitarbeiter ODER Gruppe ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zugewiesen_mitarbeiter_id")
    public Mitarbeiter zugewiesenMitarbeiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zugewiesen_gruppe_id")
    public Mitarbeiter.Gruppe zugewiesenGruppe;

    // ── Bezug (echte FKs): Person und/oder Organisation ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;
}
