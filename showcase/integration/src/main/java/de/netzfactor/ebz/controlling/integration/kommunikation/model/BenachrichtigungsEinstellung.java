package de.netzfactor.ebz.controlling.integration.kommunikation.model;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Komfort-Einstellungen einer Person (K1b) — eine Zeile je Person:
 * <ul>
 *   <li><b>Digest</b> ({@link #digest}): E-Mail/SMS werden nicht sofort versandt, sondern gebündelt
 *       (ein periodischer Sammel-Versand statt Einzelmails).</li>
 *   <li><b>Quiet-Hours</b> ({@link #quietVon}/{@link #quietBis}): in diesem Zeitfenster wird der externe
 *       Versand zurückgestellt (Deferred-Send bis Fensterende); das Portal-Postfach bleibt unberührt.</li>
 *   <li><b>Rate-Limit</b> ({@link #maxProStunde}): höchstens N externe Zustellungen pro Stunde; darüber
 *       wird zurückgestellt. {@code 0} = unbegrenzt.</li>
 * </ul>
 * Party-frei (nur {@link #personId}); Schema {@code kommunikation}. Fehlt die Zeile, gelten die Defaults
 * (kein Digest, keine Quiet-Hours, kein Limit) — Standardverhalten von K1.
 */
@Entity
@Table(name = "benachrichtigung_einstellung", schema = "kommunikation")
public class BenachrichtigungsEinstellung extends PanacheEntity {

    @Column(name = "person_id", nullable = false, unique = true)
    public Long personId;

    @Column(name = "digest", nullable = false)
    public boolean digest = false;

    /** Beginn der Ruhezeit (lokale Uhrzeit); zusammen mit {@link #quietBis} gesetzt oder beide {@code null}. */
    @Column(name = "quiet_von")
    public LocalTime quietVon;

    @Column(name = "quiet_bis")
    public LocalTime quietBis;

    /** Max. externe Zustellungen pro Stunde; {@code 0} = unbegrenzt. */
    @Column(name = "max_pro_stunde", nullable = false)
    public int maxProStunde = 0;

    /** Liegt {@code zeit} in der (ggf. über Mitternacht laufenden) Ruhezeit? */
    public boolean inRuhezeit(LocalTime zeit) {
        if (quietVon == null || quietBis == null || quietVon.equals(quietBis)) {
            return false;
        }
        if (quietVon.isBefore(quietBis)) {
            return !zeit.isBefore(quietVon) && zeit.isBefore(quietBis); // selber Tag, z. B. 12–14 Uhr
        }
        return !zeit.isBefore(quietVon) || zeit.isBefore(quietBis); // über Mitternacht, z. B. 22–07 Uhr
    }
}
