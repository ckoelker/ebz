package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Erlös-/Organisationsbereich für die Rechnungsstellung. Eigenes Enum (nicht das aus {@code bildung}),
 * weil die Rechnung zusätzlich den {@code SHOP}-Strom kennt (R7) und das Billing-SoR seine eigenen
 * Nummernkreise je Bereich führt.
 */
public enum Bereich {
    BERUFSSCHULE, HOCHSCHULE, AKADEMIE, SHOP
}
