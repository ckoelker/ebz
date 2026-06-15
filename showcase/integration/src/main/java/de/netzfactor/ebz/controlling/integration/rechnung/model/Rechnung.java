package de.netzfactor.ebz.controlling.integration.rechnung.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Rechnungskopf (Beleg). Lebenszyklus {@code ENTWURF → AUSGESTELLT (Festschreibung) → BEZAHLT/STORNIERT}.
 * Die {@code nummer} bleibt {@code null}, bis ausgestellt wird — so entstehen keine Lücken durch
 * verworfene Entwürfe. Nach Festschreibung unveränderbar; Korrektur nur über einen Folge-Beleg
 * ({@code GUTSCHRIFT}/{@code STORNO}/{@code NACHBERECHNUNG}) mit {@code originalRechnungId}.
 */
@Entity
@Table(name = "rechnung", schema = "mdm")
public class Rechnung extends PanacheEntity {

    @Version
    public long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "belegart", nullable = false, length = 16)
    public Belegart belegart;

    @Enumerated(EnumType.STRING)
    @Column(name = "bereich", nullable = false, length = 16)
    public Bereich bereich;

    /** Lückenlose Belegnummer; {@code null} bis zur Ausstellung, dann unveränderbar (Festschreibung). */
    @Column(name = "nummer", unique = true, length = 32)
    public String nummer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debitor_id", nullable = false)
    public Debitor debitor;

    /** Klartext-Abrechnungszeitraum, z. B. "Schuljahr 2025/2026, 2. Halbjahr". */
    @Column(name = "zeitraum_bezeichnung", length = 120)
    public String zeitraumBezeichnung;

    /** Idempotenz-Schlüssel des Rechnungslaufs (Bereich+Zeitraum+Debitor) → kein Doppel-Entwurf. */
    @Column(name = "lauf_schluessel", length = 200)
    public String laufSchluessel;

    @Column(name = "ausstellungsdatum")
    public LocalDate ausstellungsdatum;

    @Column(name = "zahlungsziel_tage", nullable = false)
    public int zahlungszielTage = 14;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public RechnungStatus status;

    /** Pflicht bei GUTSCHRIFT/STORNO/NACHBERECHNUNG — Bezug auf die Originalrechnung (Self-FK). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_rechnung_id")
    public Rechnung originalRechnung;

    /**
     * Versand der E-Rechnung an den Debitor. {@code null} auf Altbeständen = {@code NICHT_VERSENDET}
     * (Spalte nullable, damit Hibernate-{@code update} sie auf der Live-DB ohne Default ergänzen kann).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "versand_status", length = 16)
    public RechnungVersandStatus versandStatus;

    /** Zeitpunkt des erfolgreichen Versands (Mail an den Debitor). */
    @Column(name = "versendet_am")
    public Instant versendetAm;

    /** E-Mail-Adresse, an die zuletzt versendet wurde (Audit/Beleg-Sicht). */
    @Column(name = "versendet_an", length = 200)
    public String versendetAn;

    @OneToMany(mappedBy = "rechnung", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    public List<RechnungPosition> positionen = new ArrayList<>();

    /** Abgeleitete FK-IDs (View-/Mapping-Komfort). */
    public Long debitorId() {
        return debitor == null ? null : debitor.id;
    }

    public Long originalRechnungId() {
        return originalRechnung == null ? null : originalRechnung.id;
    }

    /** Summe aller Positionsbeträge in Cent (Gesamtbetrag des Belegs). */
    public long summeCent() {
        return positionen.stream().mapToLong(RechnungPosition::betragCent).sum();
    }
}
