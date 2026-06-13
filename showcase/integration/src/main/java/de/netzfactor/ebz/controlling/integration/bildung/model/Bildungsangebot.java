package de.netzfactor.ebz.controlling.integration.bildung.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Supertyp der verkaufbaren Bildungsangebote (Katalog-/Stammdatenebene, §11.1).
 * <p>
 * <b>Persistenz = Single Table Inheritance</b> (§11.2 A): EINE Tabelle/ID/Registry für alle
 * Subtypen, Diskriminator-Spalte {@code typ}. Spätere Normalisierung auf {@code JOINED} ist ein
 * reiner DB-Refactor (API unverändert). Subtyp-spezifische Spalten sind nullable — die fachliche
 * Pflicht/Validierung liegt im App-Layer (Bean Validation am DTO, §11.9-C/F3), nicht als DB-Check.
 * <p>
 * Diese Klasse trägt <i>keine</i> Bean-Validation: die EINZIGE Validierungsquelle ist das jeweilige
 * per-Typ-DTO (z. B. {@code SeminarDto}), das auch smallrye-openapi ins {@code /q/openapi}-Schema
 * spiegelt. Hier nur Persistenz-Mapping. Identifier bewusst ASCII ({@code gueltigAb}, §11.9-C).
 */
@Entity
@Table(name = "bildungsangebot", schema = "bildung")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "typ", discriminatorType = DiscriminatorType.STRING, length = 32)
public abstract class Bildungsangebot extends PanacheEntity {

    /** Optimistic Locking (§11.9-C2) — gegen Lost-Update ab P1.0. */
    @Version
    public long version;

    // ── gemeinsame Stammdaten-Felder (§11.3) ──
    /** Natürlicher Schlüssel (§11.9-F): unique + Format, vom Pfleger vergeben. */
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

    /** Naht zu Vendure (§11.6): gesetzt, sobald das Angebot als Produkt projiziert wurde. */
    @Column(name = "vendure_product_id", length = 64)
    public String vendureProductId;

    @Column(name = "zielgruppe", length = 200)
    public String zielgruppe;

    /** Lesbarer Typ-Diskriminator je Subtyp (für Registry/Frontend-Union). */
    public abstract BildungsangebotTyp typ();
}
