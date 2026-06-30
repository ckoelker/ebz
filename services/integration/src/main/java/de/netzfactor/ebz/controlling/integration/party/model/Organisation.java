package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Organisation (Firma) als eigene Party. Adressen/Telefone/E-Mails hängen als {@link Kontaktpunkt}
 * (A3, nicht mehr inline). Die Abrechnung läuft nicht über ein Debitor-Feld hier, sondern wird je
 * Bereich aus der bestehenden Debitoren-Hoheit projiziert.
 * <p>
 * Immobilien-Spezifika (Plan A2): {@link #unternehmenstypen}/{@link #taetigkeitsschwerpunkte}/
 * {@link #verbandszugehoerigkeiten} als N:M-Lookups, {@link #branche} (WZ/NACE), {@link #bestandsgroesse},
 * §34c/§34i-Gewerbeerlaubnis, {@link #ausbildungsbetrieb}-Flag (→ Berufsschul-/Azubi-Prozesse),
 * {@link #ihkKammer}. Firma↔Firma als einfache Hierarchie ({@link #uebergeordnete}). Dubletten-Hoheit
 * analog {@link Person} ({@link #matchSchluessel} + {@link #status}/{@link #goldenOrganisationId}).
 */
@Audited
@Entity
@Table(name = "organisation", schema = "mdm")
public class Organisation extends PanacheEntity {

    /** Lebenszyklus der Firmen-Identität: self-service erfasst → geprüft/produktiv → in Golden-Record geführt. */
    public enum Status {
        ANGEFRAGT, AKTIV, ZUSAMMENGEFUEHRT
    }

    /** §34c/§34i GewO-Erlaubnis (Makler/Verwalter) — Voraussetzung u. a. der MaBV-Weiterbildungspflicht (A19). */
    public enum Gewerbeerlaubnis {
        KEINE, BEANTRAGT, VORHANDEN
    }

    @Version
    public long version;

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Column(name = "rechtsform", length = 60)
    public String rechtsform;

    @Column(name = "handelsregisternummer", length = 40)
    public String handelsregisternummer;

    @Column(name = "registergericht", length = 120)
    public String registergericht;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branche_id")
    public Lookups.Branche branche;

    @Column(name = "website", length = 200)
    public String website;

    /** USt-IdNr. — starker Identitätsschlüssel für die Debitor-Projektion (B2B). */
    @Column(name = "ust_id", length = 20)
    public String ustId;

    /** Firma↔Firma: einfache Mutter/Tochter-Hierarchie (A2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uebergeordnete_organisation_id")
    public Organisation uebergeordnete;

    // ── Immobilien-Klassifikationen (N:M-Lookups) ──
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "organisation_unternehmenstyp", schema = "mdm",
            joinColumns = @JoinColumn(name = "organisation_id"),
            inverseJoinColumns = @JoinColumn(name = "unternehmenstyp_id"))
    public Set<Lookups.Unternehmenstyp> unternehmenstypen = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "organisation_schwerpunkt", schema = "mdm",
            joinColumns = @JoinColumn(name = "organisation_id"),
            inverseJoinColumns = @JoinColumn(name = "schwerpunkt_id"))
    public Set<Lookups.Taetigkeitsschwerpunkt> taetigkeitsschwerpunkte = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "organisation_verband", schema = "mdm",
            joinColumns = @JoinColumn(name = "organisation_id"),
            inverseJoinColumns = @JoinColumn(name = "verband_id"))
    public Set<Lookups.Verband> verbandszugehoerigkeiten = new HashSet<>();

    /** Verwaltete Einheiten (Bestandsgröße) — Vertriebs-/Segmentierungsmerkmal. */
    @Column(name = "bestandsgroesse")
    public Integer bestandsgroesse;

    @Enumerated(EnumType.STRING)
    @Column(name = "gewerbeerlaubnis", nullable = false, length = 16)
    public Gewerbeerlaubnis gewerbeerlaubnis = Gewerbeerlaubnis.KEINE;

    @Column(name = "gewerbeerlaubnis_behoerde", length = 160)
    public String gewerbeerlaubnisBehoerde;

    @Column(name = "gewerbeerlaubnis_datum")
    public LocalDate gewerbeerlaubnisDatum;

    /** Ausbildungsbetrieb → speist die Berufsschul-/Azubi-Prozesse. */
    @Column(name = "ausbildungsbetrieb", nullable = false)
    public boolean ausbildungsbetrieb = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ihk_kammer_id")
    public Lookups.IhkKammer ihkKammer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_quelle_id")
    public Lookups.LeadQuelle leadQuelle;

    @Column(name = "lead_datum")
    public LocalDate leadDatum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public Status status = Status.AKTIV;

    /** Schwacher Dubletten-Schlüssel (USt-Id bzw. {@code name|plz}); füllt die Kandidatensuche. */
    @Column(name = "match_schluessel", length = 200)
    public String matchSchluessel;

    /** Bei {@code ZUSAMMENGEFUEHRT}: Zeiger auf die überlebende Organisation (Golden-Record). */
    @Column(name = "golden_organisation_id")
    public Long goldenOrganisationId;
}
