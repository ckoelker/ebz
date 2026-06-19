package de.netzfactor.ebz.controlling.integration.kommunikation.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * <b>Transactional-Outbox-Auftrag der Kommunikation</b> — die wiederverwendbare, vom Auslöser entkoppelte
 * Dispatcher-Mechanik (Poll/Lock/Backoff/Dead-Letter), herausgelöst aus der an {@code Anmeldung} hart
 * gekoppelten {@code OutboxAuftrag}. Statt einer fachlichen FK auf den Auslöser trägt er eine echte FK auf
 * die {@link Zustellung} (Push-Einheit Empfänger×Kanal) — DRY + FK-Prinzip, ohne Domänen-Kopplung.
 * <p>
 * Wird in <b>derselben Geschäfts-Transaktion</b> wie die {@link Zustellung} geschrieben (atomar). Der
 * {@code ZustellDispatcher} zieht fällige, offene Aufträge, ruft den {@code KanalVersand}-Adapter des
 * Kanals und führt Erfolg/Backoff-Retry/Dead-Letter nach. {@code PORTAL} läuft synchron und braucht
 * keinen Auftrag — nur externe Kanäle ({@code EMAIL}/{@code SMS}) nutzen diese Outbox.
 */
@Entity
@Table(name = "zustell_auftrag", schema = "kommunikation")
public class ZustellAuftrag extends PanacheEntity {

    /** Maximale Zustellversuche, bevor der Auftrag zur manuellen Klärung (HITL) eskaliert. */
    public static final int MAX_VERSUCHE = 5;

    public enum Status {
        OFFEN, ERLEDIGT, FEHLGESCHLAGEN
    }

    @Version
    public long version;

    /** Die zuzustellende Push-Einheit (echte FK) — aus ihr liest der Adapter Kanal/Empfänger/Inhalt. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zustellung_id", nullable = false)
    public Zustellung zustellung;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.OFFEN;

    @Column(name = "versuche", nullable = false)
    public int versuche;

    /** Frühester Zeitpunkt des nächsten Versuchs (Exponential Backoff); frühere ignoriert der Dispatcher. */
    @Column(name = "naechster_versuch_am", nullable = false)
    public Instant naechsterVersuchAm = Instant.now();

    @Column(name = "letzter_fehler", length = 1000)
    public String letzterFehler;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm = Instant.now();

    @Column(name = "erledigt_am")
    public Instant erledigtAm;

    /** Idempotenz-/Dedupe-Schlüssel ({@code kanal:zustellungId}) — kein Doppelversand bei Retry/Re-Enqueue. */
    @Column(name = "idempotenz_schluessel", nullable = false, unique = true, length = 160)
    public String idempotenzSchluessel;

    /** Prozessdoku-Korrelation ({@code prozess.fall}); der async Dispatcher hat keinen HTTP-Kontext mehr. */
    @Column(name = "prozess_fall", length = 120)
    public String prozessFall;
}
