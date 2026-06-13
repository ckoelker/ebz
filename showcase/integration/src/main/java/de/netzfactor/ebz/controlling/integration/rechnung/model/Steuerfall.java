package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Umsatzsteuer-Behandlung je Position. Berufsschule (R1) ist durchgängig {@code BEFREIT} (§4 UStG);
 * {@code STANDARD}/{@code ERMAESSIGT} sind für andere Ströme vorbereitet (mit StB zu klären).
 */
public enum Steuerfall {
    BEFREIT, STANDARD, ERMAESSIGT
}
