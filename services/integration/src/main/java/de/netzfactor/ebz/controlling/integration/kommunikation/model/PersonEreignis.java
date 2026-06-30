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

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;

/**
 * Personenseitiger <b>Aktivitätslog</b> (Read-Model, append-only) — die <i>offengelegte</i> Schwester
 * des OpenTelemetry-Traces: jedes person-relevante Domänen-Event projiziert (sofern {@link #sichtbar})
 * genau einen Eintrag, den die Person im Portal als Pull-Zeitstrahl sieht (Art.-15-Transparenz). Reine
 * Log-Einträge bleiben Pull; nur die <b>Push</b>-Teilmenge bekommt zusätzlich {@link Zustellung}(Kanal).
 * <p>
 * Bewusst <b>ohne FK in den Party-Kern</b>: nur {@link #empfaengerPersonId} (Auflösung über den
 * {@code IdentitaetsPort}/{@code ErreichbarkeitPort}). So lebt das Modul im eigenen Schema
 * {@code kommunikation} und lässt sich als Communication-Core mit eigener DB herauslösen (split-ready).
 * Der fachliche <b>Kontext</b> ist ebenso FK-frei polymorph ({@link #kontextTyp}/{@link #kontextId}).
 */
@Entity
@Table(name = "person_ereignis", schema = "kommunikation")
public class PersonEreignis extends PanacheEntity {

    /** FK-freier, polymorpher Bezug auf ein Domänenobjekt (Decoupling von rechnung/bildung). */
    public enum KontextTyp {
        KEINER, BILDUNGSANGEBOT, ANMELDUNG, RECHNUNG, KONVERSATION
    }

    /** Empfänger als reine Party-ID (kein FK über die Schema-/Modulgrenze); {@code null} bei reinem
     *  Direkt-Empfänger ({@link #anEmail}, Bestands-Mail-Migration ohne Person-Bezug). */
    @Column(name = "empfaenger_person_id")
    public Long empfaengerPersonId;

    /** Direkt-Empfänger ohne Person (Azubi-/Debitor-Adresse): die E-Mail geht hierhin statt an die
     *  Person-Primäradresse; kein Portal-Log/Consent. {@code null} ⇒ Person-Primäradresse. */
    @Column(name = "an_email", length = 320)
    public String anEmail;

    /** Template-Variablen (JSON) für die Qute-Vorlage des E-Mail-Adapters — beim async Versand wird daraus
     *  der Body gerendert (zum Sendezeitpunkt ist das auslösende Event längst weg). */
    @Column(name = "variablen_json", columnDefinition = "text")
    public String variablenJson;

    /** Auslösender Ereignis-Typ (Single Source {@link EreignisTyp}: sichtbar/push/Rechtsgrundlage/Template). */
    @Enumerated(EnumType.STRING)
    @Column(name = "ereignis_typ", nullable = false, length = 48)
    public EreignisTyp ereignisTyp;

    @Column(name = "betreff", nullable = false, length = 200)
    public String betreff;

    /** Optionaler Thread-Bezug (gesetzt, wenn das Ereignis aus einer {@link Konversation} stammt). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nachricht_id")
    public Nachricht nachricht;

    @Enumerated(EnumType.STRING)
    @Column(name = "kontext_typ", nullable = false, length = 24)
    public KontextTyp kontextTyp = KontextTyp.KEINER;

    /** ID des Kontextobjekts (polymorph, FK-frei) — {@code null} bei {@link KontextTyp#KEINER}. */
    @Column(name = "kontext_id")
    public Long kontextId;

    @Column(name = "zeitpunkt", nullable = false)
    public LocalDateTime zeitpunkt = LocalDateTime.now();

    /** Prozessdoku-/Trace-Korrelation ({@code prozess.fall}) — verbindet Eintrag ↔ Trace/BPMN. */
    @Column(name = "prozess_fall", length = 120)
    public String prozessFall;

    /** Allowlist (Default {@code false} aus dem {@link EreignisTyp}): interne Vermerke bleiben unsichtbar. */
    @Column(name = "sichtbar", nullable = false)
    public boolean sichtbar = false;

    // ── Quittierung / Pflicht-Bestätigung (Felder seit K1; Durchsetzungs-Workflow K5) ──
    @Column(name = "bestaetigung_erforderlich", nullable = false)
    public boolean bestaetigungErforderlich = false;

    @Column(name = "bestaetigt_am")
    public LocalDateTime bestaetigtAm;

    /** Nachweis-Trio (analog Double-Opt-In auf {@code Einwilligung}): wer/IP/Zeit der Kenntnisnahme. */
    @Column(name = "bestaetigt_von", length = 64)
    public String bestaetigtVon;

    @Column(name = "nachweis_ip", length = 45)
    public String nachweisIp;

    @Column(name = "nachweis_zeit")
    public LocalDateTime nachweisZeit;

    // ── K5 Pflicht-Bestätigungs-Workflow: Frist, Erinnerung, Eskalation ──
    /** Frist für die Kenntnisnahme (aus {@code EreignisTyp.bestaetigungFristTage} bei Projektion); {@code null} = ohne Frist. */
    @Column(name = "bestaetigen_bis")
    public LocalDateTime bestaetigenBis;

    /** Zeitpunkt der zuletzt versendeten Erinnerung (Drosselung des Nachfass-Intervalls). */
    @Column(name = "erinnert_am")
    public LocalDateTime erinnertAm;

    /** Anzahl bereits versendeter Erinnerungen (Reporting/Idempotenz der Erinnerungs-Ereignisse). */
    @Column(name = "erinnerungen", nullable = false)
    public int erinnerungen = 0;

    /** Zeitpunkt der Eskalation (Frist überschritten, weiterhin unbestätigt) — sichtbar im Cockpit-Report. */
    @Column(name = "eskaliert_am")
    public LocalDateTime eskaliertAm;

    /** Status der Pflicht-Kenntnisnahme (für Gate & Cockpit-Report) — abgeleitet, nicht persistiert. */
    public enum BestaetigungsStatus {
        NICHT_ERFORDERLICH, BESTAETIGT, OFFEN, UEBERFAELLIG, ESKALIERT
    }

    /** Abgeleiteter Bestätigungs-Status zum gegebenen Zeitpunkt (Single Source für Gate & Reporting). */
    public BestaetigungsStatus status(LocalDateTime jetzt) {
        if (!bestaetigungErforderlich) {
            return BestaetigungsStatus.NICHT_ERFORDERLICH;
        }
        if (bestaetigtAm != null) {
            return BestaetigungsStatus.BESTAETIGT;
        }
        if (eskaliertAm != null) {
            return BestaetigungsStatus.ESKALIERT;
        }
        if (bestaetigenBis != null && jetzt.isAfter(bestaetigenBis)) {
            return BestaetigungsStatus.UEBERFAELLIG;
        }
        return BestaetigungsStatus.OFFEN;
    }

    /** Dedupe-Schlüssel (wie in der Outbox) — verhindert Doppel-Projektion bei Retry/Re-Delivery. */
    @Column(name = "idempotenz_schluessel", unique = true, length = 160)
    public String idempotenzSchluessel;
}
