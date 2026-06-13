package de.netzfactor.ebz.controlling.integration.rechnung.model;

/** Lebenszyklus einer Anmeldung; der Rechnungslauf zieht nur {@code AKTIV}e Anmeldungen. */
public enum AnmeldungStatus {
    AKTIV, ABGEBROCHEN, ABGESCHLOSSEN
}
