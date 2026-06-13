package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Zahlart einer externen Bestellung (R7). Im Showcase informativ: KARTE wird in Vendure sofort
 * beglichen (kein Mahnwesen), RECHNUNG/LASTSCHRIFT erzeugen im Billing die offene Forderung.
 */
public enum Zahlungsart {
    KARTE, RECHNUNG, LASTSCHRIFT
}
