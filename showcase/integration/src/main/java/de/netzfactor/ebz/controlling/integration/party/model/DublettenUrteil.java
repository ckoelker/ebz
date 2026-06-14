package de.netzfactor.ebz.controlling.integration.party.model;

/**
 * Urteil des KI-Dubletten-Beraters zu <i>einem</i> Kandidatenpaar — ein <b>Vorschlag</b>, keine
 * Entscheidung. Die finale Auflösung (Merge vs. Neuanlage) trifft immer ein Mensch (HITL).
 *
 * @param aehnlichkeit 0.0–1.0, Selbsteinschätzung der Übereinstimmung (für die Queue-Priorisierung)
 * @param einschaetzung grobe Klasse; {@code UNSICHER} ist auch der Fallback ohne KI → immer in die
 *                      menschliche Queue
 * @param begruendung kurze, nachvollziehbare Begründung (Audit/Transparenz)
 */
public record DublettenUrteil(double aehnlichkeit, Einschaetzung einschaetzung, String begruendung) {

    public enum Einschaetzung {
        MATCH, UNSICHER, KEIN_MATCH
    }
}
