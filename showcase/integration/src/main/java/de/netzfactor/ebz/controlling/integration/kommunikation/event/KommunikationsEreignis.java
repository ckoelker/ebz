package de.netzfactor.ebz.controlling.integration.kommunikation.event;

import java.io.Serializable;
import java.util.Map;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;

/**
 * <b>Domänen-Event als serialisierbares DTO</b> — der <i>einzige</i> Auslöser der Benachrichtigung
 * (Event-Spine). Andere Module feuern dieses CDI-Event in ihrer Geschäfts-Transaktion; ein
 * {@code @Observes}-Consumer ({@code BenachrichtigungService}, ab K1) projiziert daraus
 * {@code PersonEreignis} (Log) + ggf. {@code Zustellung} (Push) — parallel zum OTel-Trace, korreliert
 * über {@link #prozessFall}, nicht gekoppelt.
 * <p>
 * Bewusst <b>ohne Entity-Referenzen</b> (nur {@code Long}-IDs + Enums): so bleibt das Event über
 * Modul-/Prozessgrenzen transportierbar (Kafka-/Split-ready) — Gegenbeispiel: die an {@code Anmeldung}
 * gebundene {@code OutboxAuftrag}.
 *
 * @param ereignisTyp          Katalog-Eintrag (Single Source {@link EreignisTyp}).
 * @param empfaengerPersonId   Ziel-Person (Party-Kern); {@code null} bei reinem Direkt-Empfänger.
 * @param betreff              Anzeige-Betreff (aus Template/Aufrufer).
 * @param kontextTyp           polymorpher Kontext-Typ („betrifft …"), FK-frei.
 * @param kontextId            ID des Kontextobjekts; {@code null} bei {@link KontextTyp#KEINER}.
 * @param prozessFall          Trace-/BPMN-Korrelation ({@code prozess.fall}); darf {@code null} sein.
 * @param idempotenzSchluessel Dedupe-Schlüssel; verhindert Doppel-Projektion bei Retry/Re-Delivery.
 * @param anEmail              <b>Direkt-Empfänger</b> ohne Person-Bezug (Bestands-Mail-Migration: Azubi-
 *                             Adresse, Debitor-Postfach). Gesetzt ⇒ E-Mail geht an diese Adresse; ist keine
 *                             Person auflösbar, entsteht <b>kein</b> Portal-Log/Consent (rein transaktionale
 *                             Mail über Template + Zustell-Outbox). {@code null} ⇒ Person-Primäradresse.
 * @param variablen            Template-Variablen (z. B. {@code teilnehmerName}, {@code portalUrl},
 *                             {@code nummer}) für die Qute-Vorlage; serialisierbar (String/Number), nie {@code null}.
 */
public record KommunikationsEreignis(
        EreignisTyp ereignisTyp,
        Long empfaengerPersonId,
        String betreff,
        KontextTyp kontextTyp,
        Long kontextId,
        String prozessFall,
        String idempotenzSchluessel,
        String anEmail,
        Map<String, Object> variablen) implements Serializable {

    public KommunikationsEreignis {
        variablen = variablen == null ? Map.of() : Map.copyOf(variablen);
    }

    /** Schlanker Konstruktor ohne Kontext (z. B. allgemeiner System-Hinweis). */
    public static KommunikationsEreignis ohneKontext(EreignisTyp typ, Long personId, String betreff,
            String idempotenzSchluessel) {
        return new KommunikationsEreignis(typ, personId, betreff, KontextTyp.KEINER, null, null,
                idempotenzSchluessel, null, Map.of());
    }

    /** Konstruktor mit Kontext-Bezug („betrifft Ihre Rechnung/Anmeldung …"). */
    public static KommunikationsEreignis mitKontext(EreignisTyp typ, Long personId, String betreff,
            KontextTyp kontextTyp, Long kontextId, String prozessFall, String idempotenzSchluessel) {
        return new KommunikationsEreignis(typ, personId, betreff, kontextTyp, kontextId, prozessFall,
                idempotenzSchluessel, null, Map.of());
    }

    /** Wie {@link #mitKontext}, zusätzlich mit Template-Variablen (Bestands-Mail-Migration, Person-Empfänger). */
    public static KommunikationsEreignis mitVariablen(EreignisTyp typ, Long personId, String betreff,
            KontextTyp kontextTyp, Long kontextId, String idempotenzSchluessel, Map<String, Object> variablen) {
        return new KommunikationsEreignis(typ, personId, betreff, kontextTyp, kontextId, null,
                idempotenzSchluessel, null, variablen);
    }

    /**
     * <b>Direkt-Empfänger</b> (Bestands-Mail-Migration): E-Mail an {@code anEmail}, ohne Person-Bezug
     * (kein Portal-Log/Consent) — z. B. Azubi-Adresse oder Debitor-Postfach. Kontext/Anhang über
     * {@code kontextTyp}/{@code kontextId}.
     */
    public static KommunikationsEreignis anEmpfaenger(EreignisTyp typ, String anEmail, String betreff,
            KontextTyp kontextTyp, Long kontextId, String idempotenzSchluessel, Map<String, Object> variablen) {
        return new KommunikationsEreignis(typ, null, betreff, kontextTyp, kontextId, null,
                idempotenzSchluessel, anEmail, variablen);
    }
}
