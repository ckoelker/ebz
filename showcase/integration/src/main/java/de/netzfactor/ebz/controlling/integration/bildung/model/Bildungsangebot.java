package de.netzfactor.ebz.controlling.integration.bildung.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * EINE flache Entity für alle Bildungsangebote (§11.1). Schlanke Realisierung des
 * Single-Table-Modells: statt JPA-Vererbung (Supertyp + 4 Subtypen) trägt sie eine
 * {@code typ}-Diskriminator-Spalte und alle typ-spezifischen Spalten nullable in EINER Tabelle —
 * physisch identisch zu Single-Table-Inheritance, nur ohne {@code @Inheritance}. Bleibt statisch
 * typisiert (echte Spalten, kein EAV/JSONB → F10 erfüllt).
 * <p>
 * Persistenz-only: KEINE Bean-Validation hier — die liegt allein in den per-Typ-DTOs
 * (Single Source, F3), die smallrye-openapi ins {@code /q/openapi}-Schema spiegelt. Identifier ASCII
 * (§11.9-C). Schema {@code bildung} explizit am {@code @Table} (kein globales default-schema, sonst
 * zöge es {@code stg_hubspot_deal} mit um).
 */
@Entity
@Table(name = "bildungsangebot", schema = "bildung")
public class Bildungsangebot extends PanacheEntity {

    /** Optimistic Locking (§11.9-C2). */
    @Version
    public long version;

    /** Diskriminator (welcher Subtyp) — von der jeweiligen Resource gesetzt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false, length = 32)
    public BildungsangebotTyp typ;

    // ── gemeinsame Felder (§11.3) ──
    @Column(name = "code", nullable = false, unique = true, length = 32)
    public String code;

    @Column(name = "titel", nullable = false, length = 200)
    public String titel;

    @Enumerated(EnumType.STRING)
    @Column(name = "bereich", nullable = false, length = 32)
    public Bereich bereich;

    @Column(name = "kurzbeschreibung", length = 2000)
    public String kurzbeschreibung;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public AngebotStatus status;

    @Column(name = "gueltig_ab", nullable = false)
    public LocalDate gueltigAb;

    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    @Column(name = "verantwortlich", length = 120)
    public String verantwortlich;

    @Enumerated(EnumType.STRING)
    @Column(name = "preis_modell", nullable = false, length = 16)
    public PreisModell preisModell;

    @Column(name = "shop_verkauf", nullable = false)
    public boolean shopVerkauf;

    /** Naht zu Vendure (§11.6). */
    @Column(name = "vendure_product_id", length = 64)
    public String vendureProductId;

    @Column(name = "zielgruppe", length = 200)
    public String zielgruppe;

    // ── SEMINAR ──
    @Enumerated(EnumType.STRING)
    @Column(name = "kategorie", length = 32)
    public SeminarKategorie kategorie;

    @Column(name = "dauer_ue")
    public Integer dauerUE;

    @Column(name = "abschluss", length = 120)
    public String abschluss;

    @Column(name = "zertifikat")
    public Boolean zertifikat;

    @Column(name = "min_tn")
    public Integer minTN;

    /** geteilt SEMINAR + TAGUNG (max. Teilnehmer). */
    @Column(name = "max_tn")
    public Integer maxTN;

    // ── TAGUNG ──
    @Column(name = "thema", length = 200)
    public String thema;

    @Column(name = "termin_von")
    public LocalDate terminVon;

    @Column(name = "termin_bis")
    public LocalDate terminBis;

    @Column(name = "ort", length = 200)
    public String ort;

    @Column(name = "programm_url", length = 300)
    public String programmUrl;

    // ── BERUFSSCHULJAHR ──
    @Column(name = "fachrichtung", length = 200)
    public String fachrichtung;

    @Column(name = "schuljahr", length = 7)
    public String schuljahr;

    @Column(name = "jahrgang")
    public Integer jahrgang;

    @Column(name = "beginn")
    public LocalDate beginn;

    /** nur Bezugsschlüssel zum Schild-NRW-Reporting (§11.8). */
    @Column(name = "schild_nrw_schluessel", length = 32)
    public String schildNrwSchluessel;

    /** geteilt BERUFSSCHULJAHR + STUDIENGANG (Plätze). */
    @Column(name = "plaetze")
    public Integer plaetze;

    // ── STUDIENGANG ──
    @Enumerated(EnumType.STRING)
    @Column(name = "studien_abschluss", length = 16)
    public Studienabschluss studienAbschluss;

    @Enumerated(EnumType.STRING)
    @Column(name = "studienform", length = 24)
    public Studienform studienform;

    @Column(name = "startsemester", length = 6)
    public String startsemester;

    @Column(name = "regelstudienzeit_semester")
    public Integer regelstudienzeitSemester;

    @Column(name = "akkreditierung_bis")
    public LocalDate akkreditierungBis;

    @Column(name = "raten_anzahl")
    public Integer ratenAnzahl;
}
