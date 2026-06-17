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

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Marketing-Einwilligung / Opt-In (Plan A6): je {@link #person}, optional {@link #organisation}
 * (→ global vs. Firmenkontext). {@link #status} startet {@code AUSSTEHEND}; voller Double-Opt-In über
 * {@link #nachweisToken}/{@link #nachweisIp}/{@link #nachweisZeit}. {@link Person#werbesperre}/
 * {@link Person#auskunftssperre} überstimmen jedes erteilte Opt-In (in der Verarbeitung geprüft).
 */
@Audited
@Entity
@Table(name = "einwilligung", schema = "mdm")
public class Einwilligung extends PanacheEntity {

    public enum Kanal {
        EMAIL, TELEFON, POST, SMS
    }

    public enum Zweck {
        NEWSLETTER, TELEFONWERBUNG, POSTWERBUNG, BEFRAGUNG, VERANSTALTUNGSEINLADUNG
    }

    public enum Status {
        AUSSTEHEND, ERTEILT, WIDERRUFEN
    }

    /** Rechtsgrundlage nach Art. 6 DSGVO. */
    public enum Rechtsgrundlage {
        EINWILLIGUNG_6_1_A, VERTRAG_6_1_B, BERECHTIGTES_INTERESSE_6_1_F
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    public Person person;

    /** {@code null} = globale Einwilligung; gesetzt = nur im Kontext dieser Organisation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(name = "kanal", nullable = false, length = 16)
    public Kanal kanal;

    @Enumerated(EnumType.STRING)
    @Column(name = "zweck", nullable = false, length = 32)
    public Zweck zweck;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.AUSSTEHEND;

    @Enumerated(EnumType.STRING)
    @Column(name = "rechtsgrundlage", nullable = false, length = 32)
    public Rechtsgrundlage rechtsgrundlage = Rechtsgrundlage.EINWILLIGUNG_6_1_A;

    /** Lead-/Erfassungsquelle (A8), für Marketing-Attribution. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quelle_id")
    public Lookups.LeadQuelle quelle;

    @Column(name = "ausstehend_seit")
    public LocalDateTime ausstehendSeit;

    @Column(name = "erteilt_am")
    public LocalDateTime erteiltAm;

    @Column(name = "widerrufen_am")
    public LocalDateTime widerrufenAm;

    // ── Double-Opt-In-Nachweis ──
    @Column(name = "nachweis_token", length = 80)
    public String nachweisToken;

    @Column(name = "nachweis_ip", length = 45)
    public String nachweisIp;

    @Column(name = "nachweis_zeit")
    public LocalDateTime nachweisZeit;
}
