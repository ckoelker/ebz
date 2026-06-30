package de.netzfactor.ebz.controlling.integration.rechnung.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * Abrechnungsbasis der Vertrags-Ströme (R1: Berufsschule; Hochschule-Felder vorbereitet, Logik R6).
 * Der Rechnungslauf zieht die {@code AKTIV}en Anmeldungen eines Zeitraums, gruppiert sie je
 * {@code zahlungspflichtigerDebitor} (Sammelrechnung) und befüllt daraus die Positionen.
 * <p>
 * Echte FK-Kopplung im gemeinsamen Schema {@code mdm}: {@code bildungsangebot} → {@code mdm.bildungsangebot},
 * {@code zahlungspflichtigerDebitor} → {@code mdm.debitor} (Golden-/Merge-Zeiger bleiben FK-frei).
 * <p>
 * <b>Berufsschule:</b> Eine Rechnung besteht aus 1–2 Positionen. Die Beträge sind variable Werte je
 * Anmeldung ({@code unterrichtBetragCent} immer, {@code uebernachtungBetragCent} nur wenn
 * {@code zimmerart} ≠ KEINE) — Quelle der Positionsbeträge (Entscheidung a), keine Tarif-Tabelle.
 */
@Entity
@Table(name = "anmeldung", schema = "mdm")
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

    // ── Party-Kern-Provenienz (FK ins MDM-Schema; null bei Alt-/Direktanlage) ──
    /** Teilnehmer als {@code mdm.person} (Identität); {@code teilnehmerName/email} bleiben Anzeige-Snapshot. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teilnehmer_person_id")
    public Person teilnehmerPerson;

    /** Wer gebucht hat ({@code mdm.person}); aus dessen Kontext leitet sich der zahlungspflichtige Debitor ab. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "besteller_person_id")
    public Person bestellerPerson;

    /** Gewählter Bestellkontext: {@code mdm.organisation} (im Auftrag von …) bzw. {@code null} = privat. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kontext_organisation_id")
    public Organisation kontextOrganisation;

    /** Bezug ins Bildungsangebot-MDM ({@code mdm.bildungsangebot}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bildungsangebot_id")
    public Bildungsangebot bildungsangebot;

    /** Wer die Rechnung erhält (Gruppierungs-Schlüssel der Sammelrechnung). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zahlungspflichtiger_debitor_id", nullable = false)
    public Debitor zahlungspflichtigerDebitor;

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

    // ── HOCHSCHULE (R6) ──
    @Column(name = "semester", length = 6)
    public String semester;

    @Column(name = "semesterbetrag_cent")
    public Integer semesterbetragCent;

    /**
     * Optionaler Firmen-Mitzahler (z. B. duales Studium): trägt den {@code firmaAnteilCent}; dann
     * entstehen ZWEI getrennte Rechnungen (Firma + Studierende:r) als unabhängige Forderungen ohne
     * Restschuld-Haftung. {@code null} = die ganze Gebühr geht an {@code zahlungspflichtigerDebitorId}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firma_debitor_id")
    public Debitor firmaDebitor;

    @Column(name = "firma_anteil_cent")
    public Integer firmaAnteilCent;

    /** Anzahl Raten je Forderung (1/null = komplett in einer Rechnung). */
    @Column(name = "raten_anzahl")
    public Integer ratenAnzahl;

    // ── Vertrags-Audit (Anmeldung Berufsschule, Schritt F) ──
    /** Zeitpunkt der Vertragsbestätigung durch die Firma ({@code BESTAETIGT_EBZ → AKTIV}). */
    @Column(name = "vertrag_bestaetigt_am")
    public Instant vertragBestaetigtAm;

    /** {@code mdm.person} der bestätigenden Firmen-Ansprechperson (Audit). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vertrag_bestaetigt_von")
    public Person vertragBestaetiger;

    // ── Abgeleitete FK-IDs (View-/Mapping-Komfort; greifen nur die {@code id} ab, kein Proxy-Init) ──
    public Long teilnehmerPersonId() {
        return teilnehmerPerson == null ? null : teilnehmerPerson.id;
    }

    public Long bestellerPersonId() {
        return bestellerPerson == null ? null : bestellerPerson.id;
    }

    public Long kontextOrganisationId() {
        return kontextOrganisation == null ? null : kontextOrganisation.id;
    }

    public Long bildungsangebotId() {
        return bildungsangebot == null ? null : bildungsangebot.id;
    }

    public Long zahlungspflichtigerDebitorId() {
        return zahlungspflichtigerDebitor == null ? null : zahlungspflichtigerDebitor.id;
    }

    public Long firmaDebitorId() {
        return firmaDebitor == null ? null : firmaDebitor.id;
    }

    public Long vertragBestaetigtVon() {
        return vertragBestaetiger == null ? null : vertragBestaetiger.id;
    }
}
