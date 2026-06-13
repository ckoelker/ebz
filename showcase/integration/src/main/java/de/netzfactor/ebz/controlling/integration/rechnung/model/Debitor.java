package de.netzfactor.ebz.controlling.integration.rechnung.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Debitor (Rechnungsempfänger) als eine autoritative Stammdaten-Quelle — löst die heutige
 * Nummernkreis-Überschneidung. R1: {@code debitorNr} wird beim Anlegen vergeben/geprüft (unique);
 * die automatische Nummernkreis-Hoheit + Match/Merge bestehender DATEV-Debitoren ist R3.
 * <p>
 * Persistenz-only (keine Bean-Validation hier; die liegt im {@code DebitorDto}, Stack B). Schema
 * {@code rechnung} explizit am {@code @Table} (analog {@code bildung}).
 */
@Entity
@Table(name = "debitor", schema = "rechnung")
public class Debitor extends PanacheEntity {

    @Version
    public long version;

    /** Eindeutige Debitorennummer (eine Quelle; Migration/Match/Merge = R3). */
    @Column(name = "debitor_nr", nullable = false, unique = true, length = 32)
    public String debitorNr;

    @Enumerated(EnumType.STRING)
    @Column(name = "bereich", nullable = false, length = 16)
    public Bereich bereich;

    @Enumerated(EnumType.STRING)
    @Column(name = "rolle", nullable = false, length = 8)
    public DebitorRolle rolle;

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

    /** USt-IdNr. (für STANDARD/ERMAESSIGT-Belege relevant; bei Bildungsbefreiung leer). */
    @Column(name = "ust_id", length = 20)
    public String ustId;

    /** SEPA-IBAN (Lastschrift bleibt in DATEV; hier nur Stammdatum). */
    @Column(name = "iban", length = 34)
    public String iban;

    @Column(name = "email", length = 200)
    public String email;
}
