package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Mitgliedschaft = die <b>N:M-Verknüpfung Person × Organisation</b> mit Rolle und Gültigkeit (Plan A4 —
 * der Kern). Mehrere Rollen je Firma = mehrere Mitgliedschafts-Zeilen; die {@link #rolle} ist ein
 * FK-{@link Lookups.Rolle Lookup} (erweiterbar: Geschäftsführung/Vorstand/Prokurist, WEG-/Mietverwalter,
 * Objektmanager …) statt eines starren Enums.
 * <p>
 * {@link #hauptzugehoerigkeit} (person-seitig, höchstens eine aktiv = Default-Kanal) und
 * {@link #hauptansprechpartner} (firmen-seitig, höchstens einer aktiv) sind <i>nicht</i> als „genau eine"
 * modelliert (Privatperson hat keine). {@link #buchungsberechtigt} speist die wählbaren Bestellkontexte,
 * {@link #rechnungsempfaenger} die Rechnungsadressierung. Ausscheiden = historisieren ({@link #gueltigBis}).
 */
@Audited
@Entity
@Table(name = "mitgliedschaft", schema = "mdm",
        uniqueConstraints = @UniqueConstraint(columnNames = {"person_id", "organisation_id", "rolle_id"}))
public class Mitgliedschaft extends PanacheEntity {

    @Version
    public long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    public Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rolle_id", nullable = false)
    public Lookups.Rolle rolle;

    /** Position/Funktionsbezeichnung im Firmenkontext (Freitext, z. B. „Leiter Bestandsmanagement"). */
    @Column(name = "position", length = 160)
    public String position;

    @Column(name = "abteilung", length = 160)
    public String abteilung;

    /** Person-seitige Hauptzugehörigkeit (Default-Kanal); höchstens eine aktiv je Person. */
    @Column(name = "hauptzugehoerigkeit", nullable = false)
    public boolean hauptzugehoerigkeit = false;

    /** Firmen-seitiger Hauptansprechpartner; höchstens einer aktiv je Organisation. */
    @Column(name = "hauptansprechpartner", nullable = false)
    public boolean hauptansprechpartner = false;

    /** Darf im Auftrag dieser Organisation bestellen → Bestellkontext „im Auftrag von …". */
    @Column(name = "buchungsberechtigt", nullable = false)
    public boolean buchungsberechtigt = false;

    /** Erhält die Rechnungen dieser Organisation (Rechnungsadressierung). */
    @Column(name = "rechnungsempfaenger", nullable = false)
    public boolean rechnungsempfaenger = false;

    @Column(name = "gueltig_von")
    public LocalDate gueltigVon;

    /** {@code null} = offen/unbefristet; gesetzt = ausgeschieden/historisiert. */
    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    /** Abgeleitete FK-IDs (View-/Mapping-Komfort; greifen ohne Proxy-Init nur die {@code id} ab). */
    public Long personId() {
        return person == null ? null : person.id;
    }

    public Long organisationId() {
        return organisation == null ? null : organisation.id;
    }
}
