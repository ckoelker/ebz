package de.netzfactor.ebz.controlling.integration.mandant.model;

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
 * Transactional-Outbox der Mandanten-Projektion ins Drittsystem (OpenOLAT): bei einer Projektions-
 * Anforderung wird — in <b>derselben</b> DB-Transaktion wie der fachliche Auslöser — ein Auftrag
 * geschrieben (atomar); ein {@code @Scheduled}-Dispatcher zieht die fälligen Zeilen und stellt idempotent
 * zu (Backoff-Retry, Dead-Letter → HITL). Eigene Tabelle (G1) — nur das Dispatcher-<i>Muster</i> wird mit
 * {@code EnrollmentDispatcher}/HubSpot geteilt, nicht die Tabelle.
 * <p>
 * Echte {@code @ManyToOne}-FK auf {@link Mandant}. {@link Operation#ORG_ANLEGEN} legt die OpenOLAT-
 * Organisation an und schreibt {@code Mandant.openolatOrganisationKey} zurück (M2).
 */
@Entity
@Table(name = "mandant_projektion", schema = "mdm")
public class MandantProjektion extends PanacheEntity {

    /** Maximale Zustellversuche vor Eskalation zur manuellen Klärung (HITL). */
    public static final int MAX_VERSUCHE = 5;

    /** Was projiziert wird. Vorerst nur die Org-Anlage (M2); spätere Operationen erweitern hier. */
    public enum Operation {
        ORG_ANLEGEN
    }

    /** Outbox-Zustand: {@code ANGEFORDERT} = fällig; {@code FEHLGESCHLAGEN} = Dead-Letter (HITL). */
    public enum Status {
        ANGEFORDERT, ERLEDIGT, FEHLGESCHLAGEN
    }

    @Version
    public long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mandant_id", nullable = false)
    public Mandant mandant;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 24)
    public Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status;

    @Column(name = "versuche", nullable = false)
    public int versuche;

    /** Frühester Zeitpunkt des nächsten Versuchs (Exponential Backoff). */
    @Column(name = "naechster_versuch_am", nullable = false)
    public Instant naechsterVersuchAm;

    @Column(name = "letzter_fehler", length = 1000)
    public String letzterFehler;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "erledigt_am")
    public Instant erledigtAm;

    /** Prozessdoku-Korrelation ({@code prozess.fall}), beim Enqueue aus dem Baggage abgegriffen. */
    @Column(name = "prozess_fall", length = 120)
    public String prozessFall;

    /** Fällig für den Dispatcher? (offen + Zeit erreicht) */
    public boolean istFaellig(Instant jetzt) {
        return status == Status.ANGEFORDERT && !naechsterVersuchAm.isAfter(jetzt);
    }
}
