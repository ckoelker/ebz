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
        KUNDE("Kunde"),
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

    /**
     * Fachliches <b>Verfahren</b> (ganzer End-to-End-Geschäftsprozess) — die oberste Gliederungsebene
     * der Prozessdoku. Jede {@link Phase} gehört genau zu einem Verfahren; der Generator
     * ({@code showcase/prozessdoku/}) erzeugt pro Verfahren eine eigene Übersicht/Gesamt-Sicht, statt
     * unzusammenhängende Prozesse in eine Kette zu zwingen.
     */
    public enum Verfahren {
        ANMELDUNG_BERUFSSCHULE("Anmeldung Berufsschule"),
        WBT_VERKAUF("WBT-Verkauf (Shop → OpenOLAT)");

        public final String label;

        Verfahren(String label) {
            this.label = label;
        }
    }

    /** Fachliche Phase — wird zum Subprozess bzw. zur Call-Activity in der Übersicht des {@link Verfahren}s. */
    public enum Phase {
        // ── Verfahren: Anmeldung Berufsschule ──
        ANFRAGE_DUBLETTEN("Anfrage & Dublettenprüfung", Verfahren.ANMELDUNG_BERUFSSCHULE),
        EINLADUNG("Login-Einladung", Verfahren.ANMELDUNG_BERUFSSCHULE),
        AZUBI_ANMELDUNG("Azubi-Anmeldung", Verfahren.ANMELDUNG_BERUFSSCHULE),
        EBZ_BESTAETIGUNG("EBZ-Bestätigung", Verfahren.ANMELDUNG_BERUFSSCHULE),
        VERTRAG("Vertragsbestätigung", Verfahren.ANMELDUNG_BERUFSSCHULE),
        PROVISIONIERUNG("Provisionierung Drittsysteme", Verfahren.ANMELDUNG_BERUFSSCHULE),
        RECHNUNGSLAUF("Rechnungslauf", Verfahren.ANMELDUNG_BERUFSSCHULE),
        // ── Verfahren: WBT-Verkauf (Shop → OpenOLAT) ──
        WBT_KATALOG("WBT-Katalog & Shop-Listung", Verfahren.WBT_VERKAUF),
        WBT_KAUF("WBT-Kauf im Shop", Verfahren.WBT_VERKAUF),
        WBT_AUSLIEFERUNG("Auslieferung in OpenOLAT", Verfahren.WBT_VERKAUF),
        WBT_NUTZUNG("Training nutzen", Verfahren.WBT_VERKAUF);

        public final String label;
        public final Verfahren verfahren;

        Phase(String label, Verfahren verfahren) {
            this.label = label;
            this.verfahren = verfahren;
        }
    }
}
