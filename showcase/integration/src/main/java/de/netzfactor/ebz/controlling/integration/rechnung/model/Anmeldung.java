package de.netzfactor.ebz.controlling.integration.rechnung.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Abrechnungsbasis der Vertrags-Ströme (R1: Berufsschule; Hochschule-Felder vorbereitet, Logik R6).
 * Der Rechnungslauf zieht die {@code AKTIV}en Anmeldungen eines Zeitraums, gruppiert sie je
 * {@code zahlungspflichtigerDebitor} (Sammelrechnung) und befüllt daraus die Positionen.
 * <p>
 * Lose Kopplung über IDs (kein JPA-Cross-Schema-Mapping): {@code bildungsangebotId} →
 * {@code bildung.bildungsangebot}, {@code zahlungspflichtigerDebitorId} → {@code rechnung.debitor}.
 * <p>
 * <b>Berufsschule:</b> Eine Rechnung besteht aus 1–2 Positionen. Die Beträge sind variable Werte je
 * Anmeldung ({@code unterrichtBetragCent} immer, {@code uebernachtungBetragCent} nur wenn
 * {@code zimmerart} ≠ KEINE) — Quelle der Positionsbeträge (Entscheidung a), keine Tarif-Tabelle.
 */
@Entity
@Table(name = "anmeldung", schema = "rechnung")
public class Anmeldung extends PanacheEntity {

    @Version
    public long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false, length = 16)
    public AnmeldungTyp typ;

    @Column(name = "teilnehmer_name", nullable = false, length = 200)
    public String teilnehmerName;

    @Column(name = "teilnehmer_email", length = 200)
    public String teilnehmerEmail;

    /** Bezug ins Bildungsangebot-MDM (Schema {@code bildung}); lose über ID. */
    @Column(name = "bildungsangebot_id")
    public Long bildungsangebotId;

    /** Wer die Rechnung erhält (Gruppierungs-Schlüssel der Sammelrechnung). */
    @Column(name = "zahlungspflichtiger_debitor_id", nullable = false)
    public Long zahlungspflichtigerDebitorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public AnmeldungStatus status;

    // ── BERUFSSCHULE (R1) ──
    /** Schuljahr im Format "JJJJ/JJJJ" (z. B. 2025/2026). */
    @Column(name = "schuljahr", length = 9)
    public String schuljahr;

    /** Halbjahr 1 oder 2 (halbjährlicher Takt). */
    @Column(name = "halbjahr")
    public Integer halbjahr;

    @Enumerated(EnumType.STRING)
    @Column(name = "zimmerart", length = 8)
    public Zimmerart zimmerart;

    /** Unterrichtsbetrag in Cent — immer eine Position (variabler Wert je Anmeldung). */
    @Column(name = "unterricht_betrag_cent")
    public Integer unterrichtBetragCent;

    /** Übernachtungsbetrag in Cent — nur wenn {@code zimmerart} ≠ KEINE (variabler Wert je Anmeldung). */
    @Column(name = "uebernachtung_betrag_cent")
    public Integer uebernachtungBetragCent;

    // ── HOCHSCHULE (R6, Felder vorbereitet) ──
    @Column(name = "semester", length = 6)
    public String semester;

    @Column(name = "semesterbetrag_cent")
    public Integer semesterbetragCent;
}
