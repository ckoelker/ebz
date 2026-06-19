package de.netzfactor.ebz.controlling.integration.kommunikation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp.Kategorie;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * <b>Benachrichtigungs-Präferenz</b> einer Person je {@link Kanal} — K1 „basic" (Kanal an/aus) plus K1b
 * <b>Verfeinerung Kanal × {@link Kategorie}</b>: eine Zeile mit {@code kategorie = null} ist die globale
 * Kanal-Einstellung; eine Zeile mit gesetzter Kategorie überschreibt sie für genau diese Kategorie
 * (z. B. „E-Mail global an, aber für RECHNUNG aus"). Fehlt jede Zeile, gilt der Kanal als <b>aktiv</b>
 * (Opt-out). {@code PORTAL} ist nicht abschaltbar (Inbox/Art.-15-Transparenz).
 * <p>
 * Auflösungsreihenfolge im {@code PraeferenzService}: spezifische Kategorie → global → Default „aktiv".
 * Party-frei (nur {@link #personId}); Schema {@code kommunikation}.
 */
@Entity
@Table(name = "praeferenz", schema = "kommunikation",
        uniqueConstraints = @UniqueConstraint(name = "uk_praeferenz_person_kanal_kat",
                columnNames = {"person_id", "kanal", "kategorie"}))
public class Praeferenz extends PanacheEntity {

    @Column(name = "person_id", nullable = false)
    public Long personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kanal", nullable = false, length = 16)
    public Kanal kanal;

    /** {@code null} = globale Kanal-Einstellung; gesetzt = Override nur für diese Kategorie. */
    @Enumerated(EnumType.STRING)
    @Column(name = "kategorie", length = 24)
    public Kategorie kategorie;

    @Column(name = "aktiv", nullable = false)
    public boolean aktiv = true;
}
