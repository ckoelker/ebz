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

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;

/**
 * Ein <b>Thread</b> echter, zweiseitiger Kommunikation (Admin↔Person, Person↔Person, Gruppe, Video).
 * Bewusste Abgrenzung (Modell-Entscheidung F28): <b>System→Person-Benachrichtigungen sind KEINE
 * Konversation</b>, sondern nur {@link PersonEreignis} (+opt. {@link Zustellung}); {@code Konversation}
 * trägt nur echte Threads. Der fachliche Kontext ist FK-frei polymorph ({@link #kontextTyp}/
 * {@link #kontextId}, „betrifft Ihre Prüfung/Rechnung"). Teilnehmer in K0 modelliert, Chat-Funktion ab K2.
 */
@Entity
@Table(name = "konversation", schema = "kommunikation")
public class Konversation extends PanacheEntity {

    public enum Typ {
        ADMIN, DIREKT, GRUPPE, VIDEO
    }

    public enum Status {
        OFFEN, GESCHLOSSEN
    }

    /** Teilnehmer-Art — {@code AGENT} ist von Anfang an erstklassig (KI-Assistent, EU-AI-Act-Kennzeichnung). */
    public enum TeilnehmerTyp {
        PERSON, MITARBEITER, AGENT
    }

    public enum TeilnehmerRolle {
        ABSENDER, EMPFAENGER, ADMIN
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false, length = 16)
    public Typ typ;

    @Column(name = "betreff", length = 200)
    public String betreff;

    @Enumerated(EnumType.STRING)
    @Column(name = "kontext_typ", nullable = false, length = 24)
    public KontextTyp kontextTyp = KontextTyp.KEINER;

    @Column(name = "kontext_id")
    public Long kontextId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.OFFEN;

    @Column(name = "erstellt_am", nullable = false)
    public LocalDateTime erstelltAm = LocalDateTime.now();

    /**
     * Teilnehmer einer {@link Konversation} — genau einer von {@link #personId} / {@link #mitarbeiterId} /
     * {@link #agentKennung} ist gesetzt (durch {@link #teilnehmerTyp} unterschieden). Cross-Realm fähig
     * (Person = ebz-customers, Mitarbeiter = ebz-staff); Party-Bezug bewusst FK-frei (nur ID, Auflösung
     * über Ports). {@code AGENT} trägt keine Entity (kein Agenten-Subsystem in K0), nur die logische Kennung.
     */
    @Entity(name = "KonversationsTeilnehmer")
    @Table(name = "konversations_teilnehmer", schema = "kommunikation")
    public static class Teilnehmer extends PanacheEntity {

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "konversation_id", nullable = false)
        public Konversation konversation;

        @Enumerated(EnumType.STRING)
        @Column(name = "teilnehmer_typ", nullable = false, length = 16)
        public TeilnehmerTyp teilnehmerTyp;

        /** Party-ID des Personen-Teilnehmers (kein FK über die Modulgrenze); sonst {@code null}. */
        @Column(name = "person_id")
        public Long personId;

        /** Party-ID des Mitarbeiter-Teilnehmers (kein FK über die Modulgrenze); sonst {@code null}. */
        @Column(name = "mitarbeiter_id")
        public Long mitarbeiterId;

        /** Logische Kennung eines AGENT-Teilnehmers (z. B. Keycloak-Service-Account-Sub); sonst {@code null}. */
        @Column(name = "agent_kennung", length = 120)
        public String agentKennung;

        @Enumerated(EnumType.STRING)
        @Column(name = "rolle", nullable = false, length = 16)
        public TeilnehmerRolle rolle = TeilnehmerRolle.EMPFAENGER;

        /** Bis hierhin gelesen (Read-Receipt, 1× letzter Stand statt je Nachricht). */
        @Column(name = "gelesen_bis")
        public LocalDateTime gelesenBis;

        @Column(name = "stumm", nullable = false)
        public boolean stumm = false;
    }
}
