package de.netzfactor.ebz.controlling.integration.party.model;

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
 * Anruf-/CTI-Log (Plan A13): protokollierter Anruf, gespeist aus einem <b>anbieter-neutralen</b>
 * WebSocket-Event. {@link #nummerE164} wird gegen {@link Kontaktpunkt#nummerE164} gematcht; ein Treffer
 * verknüpft {@link #person}/{@link #organisation}, eine unbekannte Nummer bleibt offen (UI bietet
 * KI-/Web-Such-Schnellauswahl).
 */
@Entity
@Table(name = "anruf_log", schema = "mdm")
public class AnrufLog extends PanacheEntity {

    public enum Richtung {
        EINGEHEND, AUSGEHEND
    }

    public enum Status {
        ANGENOMMEN, VERPASST, ABGEBROCHEN
    }

    @Column(name = "nummer_e164", nullable = false, length = 24)
    public String nummerE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "richtung", nullable = false, length = 16)
    public Richtung richtung = Richtung.EINGEHEND;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.ANGENOMMEN;

    @Column(name = "zeitpunkt", nullable = false)
    public LocalDateTime zeitpunkt = LocalDateTime.now();

    @Column(name = "dauer_sekunden")
    public Integer dauerSekunden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitarbeiter_id")
    public Mitarbeiter mitarbeiter;

    // ── Aufgelöster Bezug (echte FKs); null bei unbekannter Nummer ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;
}
