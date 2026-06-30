package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Lebenszyklus einer Anmeldung; der Rechnungslauf zieht <b>nur</b> {@code AKTIV}e Anmeldungen.
 * <p>
 * Self-Service-Anmeldungen (Anmeldung Berufsschule, Schritt D) starten als {@code ANGEFRAGT}
 * (nicht abrechenbar), werden vom EBZ zu {@code BESTAETIGT_EBZ} bestätigt (Schritt E) und erst mit
 * der Vertragsbestätigung der Firma (Schritt F) {@code AKTIV} — so kann nichts vor Vertragsabschluss
 * fakturiert werden, ohne den bestehenden Rechnungslauf anzufassen.
 */
public enum AnmeldungStatus {
    ANGEFRAGT, BESTAETIGT_EBZ, AKTIV, ABGEBROCHEN, ABGESCHLOSSEN
}
