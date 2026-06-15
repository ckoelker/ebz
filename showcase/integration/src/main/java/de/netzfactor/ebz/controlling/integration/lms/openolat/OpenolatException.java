package de.netzfactor.ebz.controlling.integration.lms.openolat;

/** Fehler aus dem OpenOLAT-Provisionierungspfad (User-Anlage, Authentifizierung, Ein-/Ausschreiben, Transport). */
public class OpenolatException extends RuntimeException {
    public OpenolatException(String message) {
        super(message);
    }

    public OpenolatException(String message, Throwable cause) {
        super(message, cause);
    }
}
