package de.netzfactor.ebz.controlling.integration.lms.model;

/**
 * Lebenszyklus einer {@link Kurseinschreibung} — zugleich der Outbox-Zustand (die Entity ist ihr
 * eigener Outbox-Datensatz). {@code ANGEFORDERT}/{@code STORNO_ANGEFORDERT} = fällig für den
 * Dispatcher; {@code FEHLGESCHLAGEN} = Dead-Letter (manueller Neuversuch im Cockpit, HITL).
 */
public enum EinschreibungStatus {
    ANGEFORDERT, EINGESCHRIEBEN, FEHLGESCHLAGEN, STORNO_ANGEFORDERT, AUSGESCHRIEBEN
}
