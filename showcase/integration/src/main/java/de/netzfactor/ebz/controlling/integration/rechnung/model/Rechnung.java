package de.netzfactor.ebz.controlling.integration.rechnung.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "rechnung", schema = "rechnung")
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

    @Column(name = "debitor_id", nullable = false)
    public Long debitorId;

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

    /** Pflicht bei GUTSCHRIFT/STORNO/NACHBERECHNUNG — Bezug auf die Originalrechnung. */
    @Column(name = "original_rechnung_id")
    public Long originalRechnungId;

    @OneToMany(mappedBy = "rechnung", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    public List<RechnungPosition> positionen = new ArrayList<>();

    /** Summe aller Positionsbeträge in Cent (Gesamtbetrag des Belegs). */
    public long summeCent() {
        return positionen.stream().mapToLong(RechnungPosition::betragCent).sum();
    }
}
