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
 * Aktivität / Kontakthistorie (Plan A9): ein protokollierter Kontakt (Telefonat/E-Mail/Termin/Notiz …).
 * {@link #typ} ist ein {@link Lookups.Aktivitaetstyp Lookup}; {@link #inhaltHtml} trägt Rich-Text
 * (fett/kursiv/Listen/Links) — Datei-Anhänge als {@link Anhang} (eigene Tabelle, FK). Bezug als echte
 * nullable FKs auf {@link #person}/{@link #organisation} (mind. einer im Service gesetzt).
 */
@Entity
@Table(name = "aktivitaet", schema = "mdm")
public class Aktivitaet extends PanacheEntity {

    public enum Richtung {
        EINGEHEND, AUSGEHEND, INTERN
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "typ_id", nullable = false)
    public Lookups.Aktivitaetstyp typ;

    @Enumerated(EnumType.STRING)
    @Column(name = "richtung", nullable = false, length = 16)
    public Richtung richtung = Richtung.AUSGEHEND;

    @Column(name = "betreff", nullable = false, length = 200)
    public String betreff;

    /** Rich-Text-Inhalt (HTML). */
    @Column(name = "inhalt_html", columnDefinition = "text")
    public String inhaltHtml;

    /** Bezug (echte FKs, polymorph): Person und/oder Organisation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bearbeiter_id")
    public Mitarbeiter bearbeiter;

    @Column(name = "zeitpunkt", nullable = false)
    public LocalDateTime zeitpunkt = LocalDateTime.now();

    /** Dauer in Minuten (z. B. Telefonat/Termin); {@code null} bei Notiz. */
    @Column(name = "dauer_minuten")
    public Integer dauerMinuten;

    /**
     * Datei-Anhang einer {@link Aktivitaet} — die Binärdaten liegen revisionssicher im Objektspeicher
     * (MinIO), hier nur Metadaten + Objektschlüssel.
     */
    @Entity(name = "AktivitaetAnhang")
    @Table(name = "aktivitaet_anhang", schema = "mdm")
    public static class Anhang extends PanacheEntity {

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "aktivitaet_id", nullable = false)
        public Aktivitaet aktivitaet;

        @Column(name = "dateiname", nullable = false, length = 255)
        public String dateiname;

        @Column(name = "content_type", length = 120)
        public String contentType;

        @Column(name = "groesse_bytes")
        public Long groesseBytes;

        /** Objektschlüssel im MinIO-Bucket. */
        @Column(name = "objektschluessel", length = 300)
        public String objektschluessel;
    }
}
