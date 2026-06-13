package de.netzfactor.ebz.controlling.integration.rechnung.service;

/**
 * Fachliche Regelverletzung. Trägt den HTTP-Status, auf den die {@code RechnungResource} sie abbildet:
 * {@code 409} (Conflict) für Verstöße gegen die Festschreibung/den Lebenszyklus, {@code 404} wenn der
 * Beleg nicht existiert.
 */
public class RegelVerletzung extends RuntimeException {
    public final int status;

    public RegelVerletzung(String message) {
        this(409, message);
    }

    public RegelVerletzung(int status, String message) {
        super(message);
        this.status = status;
    }

    public static RegelVerletzung nichtGefunden(String message) {
        return new RegelVerletzung(404, message);
    }
}
