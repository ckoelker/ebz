package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Organisation (Firma) als eigene Party. Bucht über ihre Ansprechpartner in allen Bereichen; die
 * Abrechnung läuft <b>nicht</b> über ein festes Debitor-Feld hier, sondern wird je Bereich aus der
 * bestehenden Debitoren-Hoheit projiziert ({@code DebitorHoheitService.findeOderLege}) — eine
 * Organisation kann so bis zu vier Debitoren (BS/HS/AK/SH) haben, ohne Stammdaten zu duplizieren.
 */
@Entity
@Table(name = "organisation", schema = "party")
public class Organisation extends PanacheEntity {

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
}
