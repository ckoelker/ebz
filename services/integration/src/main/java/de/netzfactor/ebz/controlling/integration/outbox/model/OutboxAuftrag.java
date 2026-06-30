package de.netzfactor.ebz.controlling.integration.outbox.model;

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

import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;

/**
 * <b>Transactional-Outbox-Auftrag</b> — die persistente Brücke zwischen einer lokalen
 * Geschäftsänderung (z. B. „Ausbildungsvertrag bestätigt") und der <i>asynchronen, garantierten</i>
 * Provisionierung eines unzuverlässigen Drittsystems (WebUntis, später Moodle/Schild-NRW …).
 * <p>
 * Der Auftrag wird in <b>derselben DB-Transaktion</b> wie die Statusänderung geschrieben (atomar: beides
 * oder nichts — kein verlorener Provisionierungs-Call bei Crash/Restart). Ein {@code OutboxDispatcher}
 * zieht {@code OFFEN}e, fällige Aufträge, ruft den passenden Adapter und führt Erfolg/Backoff-Retry/
 * Dead-Letter ({@code FEHLGESCHLAGEN} → HITL im Cockpit) nach. Jeder Versuch ist idempotent (s.
 * {@link #idempotenzSchluessel}); die Drittsysteme legen daher bei Doppel-Zustellung keinen Dublett an.
 * <p>
 * Echte FK-Kopplung im gemeinsamen Schema {@code mdm}: {@code anmeldung} → {@code mdm.anmeldung}.
 */
@Entity
@Table(name = "outbox_auftrag", schema = "mdm")
public class OutboxAuftrag extends PanacheEntity {

    /** Maximale Zustellversuche, bevor der Auftrag zur manuellen Klärung (HITL) eskaliert. */
    public static final int MAX_VERSUCHE = 5;

    @Version
    public long version;

    /** Auslösendes Geschäftsereignis (Provenienz/Audit). */
    @Enumerated(EnumType.STRING)
    @Column(name = "ereignis", nullable = false, length = 32)
    public Ereignis ereignis;

    /** Zielsystem, das provisioniert werden soll — bestimmt den zuständigen {@code Zielsystemexport}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "zielsystem", nullable = false, length = 24)
    public Zielsystem zielsystem;

    /** Betroffene Anmeldung (FK ins MDM); aus ihr liest der Adapter die zu übertragenden Daten. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anmeldung_id", nullable = false)
    public Anmeldung anmeldung;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status;

    @Column(name = "versuche", nullable = false)
    public int versuche;

    /** Frühester Zeitpunkt des nächsten Versuchs (Exponential Backoff); der Dispatcher ignoriert frühere. */
    @Column(name = "naechster_versuch_am", nullable = false)
    public Instant naechsterVersuchAm;

    @Column(name = "letzter_fehler", length = 1000)
    public String letzterFehler;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "erledigt_am")
    public Instant erledigtAm;

    /**
     * Eindeutiger Schlüssel ({@code zielsystem:ereignis:anmeldungId}) — verhindert Doppel-Aufträge beim
     * (idempotenten) Enqueue und ist zugleich der fachliche Idempotenz-Schlüssel der Zustellung.
     */
    @Column(name = "idempotenz_schluessel", nullable = false, unique = true, length = 120)
    public String idempotenzSchluessel;

    /**
     * Prozessdoku-Korrelation ({@code prozess.fall}), beim Enqueue aus dem Baggage abgegriffen. Da der
     * Dispatcher <b>asynchron ohne HTTP-Kontext</b> läuft, wird die Fall-Id hier mitgeführt, damit der
     * WebUntis-Sync-Span demselben BPMN-Fall zugeordnet wird wie der auslösende Anmeldungs-Durchlauf.
     */
    @Column(name = "prozess_fall", length = 120)
    public String prozessFall;

    /** Auslösendes Geschäftsereignis. */
    public enum Ereignis {
        AZUBI_VERTRAG_BESTAETIGT("Ausbildungsvertrag bestätigt");

        public final String label;

        Ereignis(String label) {
            this.label = label;
        }
    }

    /** Zu provisionierendes Drittsystem (je Bereich; aktuell als Showcase nur WebUntis implementiert). */
    public enum Zielsystem {
        WEBUNTIS("WebUntis (Stundenplan/Klassenbuch)"),
        SUITE8("Suite8 (Bezahlkarte Kiosk/Kantine)"),
        MOODLE("Moodle (LMS)"),
        SCHILD_NRW("Schild-NRW (Schulverwaltung)"),
        PRUEFUNGSSOFTWARE("Prüfungssoftware (Hochschule)"),
        VIDEOPLATTFORM("Videoplattform (Seminar)");

        public final String label;

        Zielsystem(String label) {
            this.label = label;
        }
    }

    /** Lebenszyklus eines Auftrags. {@code FEHLGESCHLAGEN} = Dead-Letter (manueller Neuversuch im Cockpit). */
    public enum Status {
        OFFEN, ERLEDIGT, FEHLGESCHLAGEN
    }

    /** Abgeleitete FK-ID (View-/Mapping-Komfort; greift nur die {@code id} ab, kein Proxy-Init). */
    public Long anmeldungId() {
        return anmeldung == null ? null : anmeldung.id;
    }
}
