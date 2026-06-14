package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Organisation (Firma) als eigene Party. Bucht über ihre Ansprechpartner in allen Bereichen; die
 * Abrechnung läuft <b>nicht</b> über ein festes Debitor-Feld hier, sondern wird je Bereich aus der
 * bestehenden Debitoren-Hoheit projiziert ({@code DebitorHoheitService.findeOderLege}) — eine
 * Organisation kann so bis zu vier Debitoren (BS/HS/AK/SH) haben, ohne Stammdaten zu duplizieren.
 *
 * <p>Dubletten-Hoheit analog zu {@link Person}: {@link #matchSchluessel} (USt-Id bzw. {@code name|plz},
 * gleiche Normalisierung wie der Debitor) trägt die Kandidatensuche, {@link #status}/{@link #goldenOrganisationId}
 * den Merge auf einen Golden-Record. Eine self-service erfasste Firma startet {@code ANGEFRAGT} und wird
 * erst nach der HITL-/KI-Dublettenprüfung produktiv ({@code AKTIV}).
 */
@Entity
@Table(name = "organisation", schema = "mdm")
public class Organisation extends PanacheEntity {

    /** Lebenszyklus der Firmen-Identität: self-service erfasst → geprüft/produktiv → in Golden-Record geführt. */
    public enum Status {
        ANGEFRAGT, AKTIV, ZUSAMMENGEFUEHRT
    }

    @Version
    public long version;

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Column(name = "strasse", length = 200)
    public String strasse;

    @Column(name = "plz", length = 10)
    public String plz;

    @Column(name = "ort", length = 120)
    public String ort;

    @Column(name = "land", length = 2)
    public String land;

    /** USt-IdNr. — starker Identitätsschlüssel für die Debitor-Projektion (B2B). */
    @Column(name = "ust_id", length = 20)
    public String ustId;

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
