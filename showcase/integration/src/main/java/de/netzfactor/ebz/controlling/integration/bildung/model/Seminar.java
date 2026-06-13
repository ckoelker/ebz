package de.netzfactor.ebz.controlling.integration.bildung.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Subtyp {@code SEMINAR} der Bildungsangebot-Familie (P1.0). Trägt nur die seminar-spezifischen
 * Spalten; alle gemeinsamen Felder erbt es vom {@link Bildungsangebot}-Supertyp (eine STI-Tabelle).
 * Validierung der Felder liegt im {@code SeminarDto}.
 */
@Entity
@DiscriminatorValue("SEMINAR")
public class Seminar extends Bildungsangebot {

    @Enumerated(EnumType.STRING)
    @Column(name = "kategorie", length = 32)
    public SeminarKategorie kategorie;

    /** Dauer in Unterrichtseinheiten. */
    @Column(name = "dauer_ue")
    public int dauerUE;

    @Column(name = "abschluss", length = 120)
    public String abschluss;

    @Column(name = "zertifikat")
    public boolean zertifikat;

    @Column(name = "min_tn")
    public int minTN;

    @Column(name = "max_tn")
    public int maxTN;

    @Override
    public BildungsangebotTyp typ() {
        return BildungsangebotTyp.SEMINAR;
    }
}
