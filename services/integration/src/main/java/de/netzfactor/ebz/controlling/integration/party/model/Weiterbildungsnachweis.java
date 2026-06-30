package de.netzfactor.ebz.controlling.integration.party.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Weiterbildungsnachweis (Plan A19) für die Immobilien-Weiterbildungspflicht (§34c GewO / §15b MaBV:
 * 20 Std. je 3 Jahre). Je {@link #person} ein Nachweis ({@link #stunden}/{@link #datum}); {@link #extern}
 * erfasst auch <i>fremde</i> Nachweise (nicht bei EBZ erworben). Stundenkonto + 3-Jahres-Zeitraum +
 * Frist-Ampel werden daraus im Service abgeleitet — starker Vertriebs-/Bindungshebel.
 */
@Entity
@Table(name = "weiterbildungsnachweis", schema = "mdm")
public class Weiterbildungsnachweis extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    public Person person;

    @Column(name = "titel", nullable = false, length = 250)
    public String titel;

    @Column(name = "anbieter", length = 200)
    public String anbieter;

    @Column(name = "stunden", nullable = false, precision = 6, scale = 2)
    public BigDecimal stunden;

    @Column(name = "datum", nullable = false)
    public LocalDate datum;

    /** {@code true} = außerhalb des EBZ erworben (fremder Nachweis), trotzdem auf das Stundenkonto angerechnet. */
    @Column(name = "extern", nullable = false)
    public boolean extern = false;

    /** Objektschlüssel des Nachweis-Dokuments im MinIO-Bucket. */
    @Column(name = "nachweis_objektschluessel", length = 300)
    public String nachweisObjektschluessel;
}
