package de.netzfactor.ebz.controlling.integration.bildung.vendure;

/** Fehler aus dem Vendure-Projektionspfad (Login fehlgeschlagen, GraphQL-{@code errors}, Transport). */
public class VendureException extends RuntimeException {
    public VendureException(String message) {
        super(message);
    }

    public VendureException(String message, Throwable cause) {
        super(message, cause);
    }
}
