package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Übernachtung der Berufsschul-Anmeldung. {@code KEINE} ⇒ nur die Unterrichts-Position; sonst kommt
 * eine zweite Position (Übernachtung) hinzu — Doppel- oder Einzelzimmer.
 */
public enum Zimmerart {
    KEINE, DOPPEL, EINZEL
}
