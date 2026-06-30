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

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.TeilnehmerTyp;

/**
 * Eine einzelne Nachricht in einer {@link Konversation}. Absender ist genau einer von {@link #personId} /
 * {@link #mitarbeiterId} / {@link #agentKennung} (durch {@link #absenderTyp} unterschieden). Das Flag
 * {@link #kiGeneriert} erfüllt die <b>EU-AI-Act-Art.-50-Kennzeichnungspflicht</b> (ab 2.8.2026): sobald
 * ein Agent antwortet, ist die Nachricht als KI-generiert markiert und wird in der UI sichtbar gemacht.
 * Party-Bezug FK-frei (nur ID, split-ready). Datei-Anhänge als {@link Anhang} (MinIO-Metadaten).
 */
@Entity
@Table(name = "nachricht", schema = "kommunikation")
public class Nachricht extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "konversation_id", nullable = false)
    public Konversation konversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "absender_typ", nullable = false, length = 16)
    public TeilnehmerTyp absenderTyp;

    /** Party-ID des Personen-Absenders (kein FK über die Modulgrenze); sonst {@code null}. */
    @Column(name = "absender_person_id")
    public Long personId;

    /** Party-ID des Mitarbeiter-Absenders (kein FK über die Modulgrenze); sonst {@code null}. */
    @Column(name = "absender_mitarbeiter_id")
    public Long mitarbeiterId;

    /** Logische Kennung eines AGENT-Absenders; sonst {@code null}. */
    @Column(name = "absender_agent_kennung", length = 120)
    public String agentKennung;

    /** Rich-Text-Inhalt (HTML) — vor der Persistenz zu sanitizen (XSS), s. K2/Risiken. */
    @Column(name = "inhalt_html", columnDefinition = "text")
    public String inhaltHtml;

    @Column(name = "zeitpunkt", nullable = false)
    public LocalDateTime zeitpunkt = LocalDateTime.now();

    /** EU-AI-Act Art. 50: {@code true}, wenn von einem Agenten erzeugt → KI-Kennzeichnung in der UI. */
    @Column(name = "ki_generiert", nullable = false)
    public boolean kiGeneriert = false;

    /**
     * Datei-Anhang einer {@link Nachricht} — Binärdaten revisionssicher im Objektspeicher (MinIO), hier
     * nur Metadaten + Objektschlüssel (auch Meeting-Recording/Transkript ab der Video-Stufe).
     */
    @Entity(name = "NachrichtAnhang")
    @Table(name = "nachricht_anhang", schema = "kommunikation")
    public static class Anhang extends PanacheEntity {

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "nachricht_id", nullable = false)
        public Nachricht nachricht;

        @Column(name = "dateiname", nullable = false, length = 255)
        public String dateiname;

        @Column(name = "content_type", length = 120)
        public String contentType;

        @Column(name = "groesse_bytes")
        public Long groesseBytes;

        @Column(name = "objektschluessel", length = 300)
        public String objektschluessel;
    }
}
