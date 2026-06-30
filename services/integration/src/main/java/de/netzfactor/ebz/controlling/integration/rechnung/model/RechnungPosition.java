package de.netzfactor.ebz.controlling.integration.rechnung.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Rechnungsposition. Berufsschule (R1): Unterricht (immer) + Übernachtung (optional). Beträge sind
 * variable Werte (aus der {@code Anmeldung} vorbefüllt, Entscheidung a) — keine Tarif-Tabelle.
 * {@code leistungsart} wird vorgehalten, damit R4 das DATEV-Erlöskonto ableiten kann.
 */
@Entity
@Table(name = "rechnung_position", schema = "mdm")
public class RechnungPosition extends PanacheEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "rechnung_id", nullable = false)
    public Rechnung rechnung;

    /** Zeilenkontext der Sammelrechnung (welcher Teilnehmer); optional. */
    @Column(name = "teilnehmer_name", length = 200)
    public String teilnehmerName;

    @Column(name = "beschreibung", nullable = false, length = 300)
    public String beschreibung;

    @Column(name = "menge", nullable = false)
    public int menge = 1;

    /** Einzelbetrag in Cent. */
    @Column(name = "einzelbetrag_cent", nullable = false)
    public int einzelbetragCent;

    @Enumerated(EnumType.STRING)
    @Column(name = "steuerfall", nullable = false, length = 16)
    public Steuerfall steuerfall;

    /** Steuersatz in Prozent (0 bei BEFREIT). */
    @Column(name = "steuersatz", nullable = false)
    public int steuersatz;

    @Column(name = "befreiungsgrund", length = 200)
    public String befreiungsgrund;

    @Enumerated(EnumType.STRING)
    @Column(name = "leistungsart", nullable = false, length = 16)
    public Leistungsart leistungsart;

    /** Herkunft: aus dem Rechnungslauf ({@code AUTO}) oder manuell ergänzt ({@code MANUELL}). */
    @Column(name = "herkunft", nullable = false, length = 8)
    public String herkunft = "AUTO";

    /** Positionsbetrag = menge × einzelbetrag (Cent). */
    public long betragCent() {
        return (long) menge * einzelbetragCent;
    }
}
