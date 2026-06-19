package de.netzfactor.ebz.controlling.integration.kommunikation.event;

import java.io.Serializable;

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
 * @param empfaengerPersonId   Ziel-Person (Party-Kern).
 * @param betreff              Anzeige-Betreff (aus Template/Aufrufer).
 * @param kontextTyp           polymorpher Kontext-Typ („betrifft …"), FK-frei.
 * @param kontextId            ID des Kontextobjekts; {@code null} bei {@link KontextTyp#KEINER}.
 * @param prozessFall          Trace-/BPMN-Korrelation ({@code prozess.fall}); darf {@code null} sein.
 * @param idempotenzSchluessel Dedupe-Schlüssel; verhindert Doppel-Projektion bei Retry/Re-Delivery.
 */
public record KommunikationsEreignis(
        EreignisTyp ereignisTyp,
        Long empfaengerPersonId,
        String betreff,
        KontextTyp kontextTyp,
        Long kontextId,
        String prozessFall,
        String idempotenzSchluessel) implements Serializable {

    /** Schlanker Konstruktor ohne Kontext (z. B. allgemeiner System-Hinweis). */
    public static KommunikationsEreignis ohneKontext(EreignisTyp typ, Long personId, String betreff,
            String idempotenzSchluessel) {
        return new KommunikationsEreignis(typ, personId, betreff, KontextTyp.KEINER, null, null,
                idempotenzSchluessel);
    }

    /** Konstruktor mit Kontext-Bezug („betrifft Ihre Rechnung/Anmeldung …"). */
    public static KommunikationsEreignis mitKontext(EreignisTyp typ, Long personId, String betreff,
            KontextTyp kontextTyp, Long kontextId, String prozessFall, String idempotenzSchluessel) {
        return new KommunikationsEreignis(typ, personId, betreff, kontextTyp, kontextId, prozessFall,
                idempotenzSchluessel);
    }
}
