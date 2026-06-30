package de.netzfactor.ebz.controlling.integration.outbox.service;

import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Zielsystem;

/**
 * Adapter zu <b>einem</b> Drittsystem (WebUntis, Moodle, Schild-NRW …) — bewusst hinter einem Interface
 * (wie {@code LoginProvisionierung}), damit jeder Anschluss einzeln testbar/mockbar und austauschbar ist.
 * <p>
 * Der {@code OutboxDispatcher} wählt anhand von {@link #ziel()} den zuständigen Adapter. {@link #exportiere}
 * <b>muss idempotent</b> sein (At-least-once: Retries und Doppel-Zustellung sind möglich) und wirft bei
 * fachlichem/technischem Fehlschlag eine Exception — der Dispatcher übernimmt dann Backoff/Retry/Dead-Letter.
 */
public interface Zielsystemexport {

    /** Für welches Zielsystem dieser Adapter zuständig ist. */
    Zielsystem ziel();

    /** Überträgt die Daten des Auftrags ins Drittsystem (idempotent). Bei Fehlschlag: Exception. */
    void exportiere(OutboxAuftrag auftrag);
}
