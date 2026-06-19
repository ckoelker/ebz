package de.netzfactor.ebz.controlling.integration.kommunikation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * <b>Benachrichtigungs-Präferenz</b> einer Person je {@link Kanal} (K1 „basic": Kanal an/aus). Fehlt eine
 * Zeile, gilt der Kanal als <b>aktiv</b> (Opt-out-Modell) — eine Zeile mit {@code aktiv=false} schaltet ihn
 * ab. {@code PORTAL} ist nicht abschaltbar (Inbox/Art.-15-Transparenz) und wird hier nicht geführt.
 * <p>
 * Die feinere Matrix (Kanal × {@code Kategorie}), Digest und Quiet-Hours folgen in K1b. Party-frei
 * (nur {@link #personId}); lebt im Schema {@code kommunikation}.
 */
@Entity
@Table(name = "praeferenz", schema = "kommunikation",
        uniqueConstraints = @UniqueConstraint(name = "uk_praeferenz_person_kanal",
                columnNames = {"person_id", "kanal"}))
public class Praeferenz extends PanacheEntity {

    @Column(name = "person_id", nullable = false)
    public Long personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kanal", nullable = false, length = 16)
    public Kanal kanal;

    @Column(name = "aktiv", nullable = false)
    public boolean aktiv = true;
}
