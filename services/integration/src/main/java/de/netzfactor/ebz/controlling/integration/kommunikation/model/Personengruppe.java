package de.netzfactor.ebz.controlling.integration.kommunikation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Verteiler für Broadcasts (K3): entweder <b>manuell</b> gepflegt ({@link Mitglied}) oder <b>abgeleitet</b>
 * aus einer Quelle (Kohorte/Klasse = {@code BILDUNGSANGEBOT}, Firmenkreis = {@code ORGANISATION}).
 * Abgeleitete Gruppen führen <b>keine</b> Mitglieder-Tabelle, sondern werden <i>zum Sendezeitpunkt</i>
 * aufgelöst (Query über Anmeldung/Mitgliedschaft) — die Quelle ist FK-frei ({@link #quelle}/
 * {@link #quelleRefId}), damit {@code kommunikation} nicht an {@code bildung} koppelt (Auflösung via Port).
 */
@Entity
@Table(name = "personengruppe", schema = "kommunikation")
public class Personengruppe extends PanacheEntity {

    public enum Quelle {
        MANUELL, BILDUNGSANGEBOT, ORGANISATION
    }

    @Column(name = "name", nullable = false, length = 160)
    public String name;

    @Column(name = "beschreibung", length = 500)
    public String beschreibung;

    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", nullable = false, length = 24)
    public Quelle quelle = Quelle.MANUELL;

    /** ID des Quellobjekts bei abgeleiteten Gruppen (polymorph, FK-frei); {@code null} bei {@code MANUELL}. */
    @Column(name = "quelle_ref_id")
    public Long quelleRefId;

    /**
     * Manuelles Mitglied einer {@link Personengruppe} (nur {@link Quelle#MANUELL}); abgeleitete Gruppen
     * lösen ihre Mitglieder dynamisch auf und haben keine Zeilen hier.
     */
    @Entity(name = "GruppenMitglied")
    @Table(name = "gruppen_mitglied", schema = "kommunikation")
    public static class Mitglied extends PanacheEntity {

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "gruppe_id", nullable = false)
        public Personengruppe gruppe;

        /** Party-ID des Mitglieds (kein FK über die Modulgrenze). */
        @Column(name = "person_id", nullable = false)
        public Long personId;
    }
}
