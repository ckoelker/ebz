package de.netzfactor.ebz.controlling.integration.prozessdoku;

/**
 * Vokabular der Prozessdoku (Living Documentation): die Dimensionen, mit denen jeder fachliche
 * Prozess-Schritt als OpenTelemetry-Span beschrieben wird. Aus diesen Attributen erzeugt der
 * Generator ({@code showcase/prozessdoku/}) Lanes ({@link System}/{@link Akteur}), Subprozesse
 * ({@link Phase}) und Symbole ({@link Typ}) im BPMN. Siehe {@code prozessdoku-planung/README.md}.
 */
public final class Prozess {

    private Prozess() {
    }

    /** Wer den Schritt ausführt — wird (mit {@link System}) zur BPMN-Lane. */
    public enum Akteur {
        ANONYM("Interessent (anonym)"),
        FIRMA("Firma (Ansprechpartner)"),
        AZUBI("Azubi"),
        EBZ("EBZ-Sachbearbeitung"),
        SYSTEM("System");

        public final String label;

        Akteur(String label) {
            this.label = label;
        }
    }

    /** In welchem System der Schritt läuft — Teil der Lane-Beschriftung und der Datenfluss-Sicht. */
    public enum System {
        PORTAL("Portal-SPA"),
        COCKPIT("MDM-Cockpit"),
        BACKEND("integration-Backend"),
        KEYCLOAK("Keycloak"),
        MAIL("Mail"),
        VENDURE("Vendure"),
        RECHNUNGSLAUF("Rechnungslauf"),
        WEBUNTIS("WebUntis"),
        SUITE8("Suite8"),
        OPENOLAT("OpenOLAT");

        public final String label;

        System(String label) {
            this.label = label;
        }
    }

    /** BPMN-Aufgabentyp (steuert das Symbol im Diagramm). */
    public enum Typ {
        USER_TASK, SERVICE_TASK, MESSAGE, BUSINESS_RULE
    }

    /** Fachliche Phase — wird zum Subprozess bzw. zur Call-Activity in der Übersicht. */
    public enum Phase {
        ANFRAGE_DUBLETTEN("Anfrage & Dublettenprüfung"),
        EINLADUNG("Login-Einladung"),
        AZUBI_ANMELDUNG("Azubi-Anmeldung"),
        EBZ_BESTAETIGUNG("EBZ-Bestätigung"),
        VERTRAG("Vertragsbestätigung"),
        PROVISIONIERUNG("Provisionierung Drittsysteme"),
        RECHNUNGSLAUF("Rechnungslauf");

        public final String label;

        Phase(String label) {
            this.label = label;
        }
    }
}
