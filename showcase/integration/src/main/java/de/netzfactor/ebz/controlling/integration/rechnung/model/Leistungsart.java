package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Fachliche Leistungsart einer Position. Wird je Position vorgehalten, damit R4 daraus
 * (Bereich × Leistungsart × Steuerfall) das DATEV-Erlöskonto + den BU-Schlüssel ableiten kann.
 */
public enum Leistungsart {
    UNTERRICHT, UEBERNACHTUNG, KORREKTUR, SONSTIGE
}
