package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * EBZ-Mitarbeiter:in (Plan A11) — schlanker Bearbeiter-Datensatz, Keycloak-{@code sub} als Anker.
 * Bündelt die Organisationsstruktur des CRM-Backoffice: {@link Gruppe} (Team/Abteilung <i>und</i>
 * Zuweisungsziel, N:M) sowie {@link LetzteAufruf} (server-seitige Quicklinks „Zuletzt aufgerufen").
 */
@Entity
@Table(name = "mitarbeiter", schema = "mdm")
public class Mitarbeiter extends PanacheEntity {

    @Column(name = "keycloak_sub", unique = true, length = 64)
    public String keycloakSub;

    @Column(name = "anzeige_name", nullable = false, length = 200)
    public String anzeigeName;

    @Column(name = "email", length = 200)
    public String email;

    @Column(name = "aktiv", nullable = false)
    public boolean aktiv = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "mitarbeiter_gruppe", schema = "mdm",
            joinColumns = @JoinColumn(name = "mitarbeiter_id"),
            inverseJoinColumns = @JoinColumn(name = "gruppe_id"))
    public Set<Gruppe> gruppen = new HashSet<>();

    /** Team/Abteilung — zugleich Zuweisungsziel für Wiedervorlagen (A10/A11). */
    @Entity(name = "MitarbeiterGruppe")
    @Table(name = "gruppe", schema = "mdm")
    public static class Gruppe extends PanacheEntity {

        public enum Art {
            TEAM, ABTEILUNG
        }

        @Column(name = "name", nullable = false, length = 160)
        public String name;

        @Enumerated(EnumType.STRING)
        @Column(name = "art", nullable = false, length = 16)
        public Art art = Art.TEAM;
    }

    /** Server-seitiger Quicklink „Zuletzt aufgerufen" je Mitarbeiter (Top-N), Bezug Person ODER Organisation. */
    @Entity(name = "LetzteAufruf")
    @Table(name = "letzte_aufrufe", schema = "mdm")
    public static class LetzteAufruf extends PanacheEntity {

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "mitarbeiter_id", nullable = false)
        public Mitarbeiter mitarbeiter;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "person_id")
        public Person person;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "organisation_id")
        public Organisation organisation;

        @Column(name = "zeitpunkt", nullable = false)
        public LocalDateTime zeitpunkt = LocalDateTime.now();
    }
}
