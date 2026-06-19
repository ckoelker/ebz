package de.netzfactor.ebz.controlling.integration.kommunikation.model;

import java.time.LocalDateTime;

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
 * <b>Push-Zustellung</b> eines {@link PersonEreignis} über genau einen {@link Kanal} (je Empfänger×Kanal).
 * Nur die Push-Teilmenge des Aktivitätslogs erhält Zustellungen; reine Pull-Log-Einträge haben keine.
 * <p>
 * {@code PORTAL} wird <b>synchron</b> in der Geschäfts-Transaktion erzeugt (unverlierbare Inbox-Kopie,
 * Badge sofort); {@code EMAIL}/{@code SMS} laufen über einen {@link ZustellAuftrag} (Transactional Outbox:
 * Retry/Backoff/Dead-Letter). {@link #gelesenAm} setzt die PORTAL-Ansicht — der Lese-Zeitstempel ist
 * fachliches Kerndatum und liegt daher direkt hier.
 */
@Entity
@Table(name = "zustellung", schema = "kommunikation")
public class Zustellung extends PanacheEntity {

    /** Zustellkanal. {@code PORTAL} = synchron/Inbox; {@code EMAIL}/{@code SMS} = async über Outbox. */
    public enum Kanal {
        PORTAL, EMAIL, SMS
    }

    public enum Status {
        NEU, ZUGESTELLT, GELESEN, FEHLER
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_ereignis_id", nullable = false)
    public PersonEreignis personEreignis;

    @Enumerated(EnumType.STRING)
    @Column(name = "kanal", nullable = false, length = 16)
    public Kanal kanal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.NEU;

    @Column(name = "zeitpunkt", nullable = false)
    public LocalDateTime zeitpunkt = LocalDateTime.now();

    /** Lese-Zeitstempel (PORTAL-Ansicht setzt ihn); {@code null} = ungelesen → Badge. */
    @Column(name = "gelesen_am")
    public LocalDateTime gelesenAm;
}
