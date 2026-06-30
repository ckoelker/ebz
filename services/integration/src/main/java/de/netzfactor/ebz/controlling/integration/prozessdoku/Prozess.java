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
        OPENOLAT("OpenOLAT"),
        HUBSPOT("HubSpot"),
        DATEV("DATEV");

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
        WBT_VERKAUF("WBT-Verkauf (Shop → OpenOLAT)"),
        RECHNUNGSSTELLUNG("Rechnungsstellung (Cockpit & E-Rechnung)"),
        HUBSPOT_SYNC("Marketing-Sync (HubSpot)"),
        MANDANTEN_VERMARKTUNG("Mandantenfähige eLearning-Vermarktung"),
        KOMMUNIKATION("Benachrichtigung & Bestätigung");

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
        WBT_NUTZUNG("Training nutzen", Verfahren.WBT_VERKAUF),
        // ── Verfahren: Rechnungsstellung (Cockpit & E-Rechnung) ──
        SONDERRECHNUNG_ANLAGE("Sonderrechnung anlegen", Verfahren.RECHNUNGSSTELLUNG),
        RECHNUNG_AUSSTELLEN("Rechnung ausstellen", Verfahren.RECHNUNGSSTELLUNG),
        RECHNUNG_VERSAND("E-Rechnung versenden", Verfahren.RECHNUNGSSTELLUNG),
        ZAHLUNGSEINGANG("Zahlungseingang verbuchen", Verfahren.RECHNUNGSSTELLUNG),
        DATEV_EXPORT("DATEV-Export", Verfahren.RECHNUNGSSTELLUNG),
        // ── Verfahren: Mandantenfähige eLearning-Vermarktung ──
        MANDANT_ONBOARDING("Mandant anlegen & konfigurieren", Verfahren.MANDANTEN_VERMARKTUNG),
        MANDANT_ORG_PROJEKTION("Org-Projektion nach OpenOLAT", Verfahren.MANDANTEN_VERMARKTUNG),
        MANDANT_IDP_FOEDERATION("IdP-Föderation (Keycloak Organizations)", Verfahren.MANDANTEN_VERMARKTUNG),
        MANDANT_CONTENT_FREIGABE("Content-Freigabe (Catalog-2.0-Offers)", Verfahren.MANDANTEN_VERMARKTUNG),
        MANDANT_SEAT_VERWALTUNG("Seat-Verwaltung (Cap & HITL)", Verfahren.MANDANTEN_VERMARKTUNG),
        MANDANT_NACHWEIS("Weiterbildungsnachweis (Soll-Stunden)", Verfahren.MANDANTEN_VERMARKTUNG),
        // ── Verfahren: Marketing-Sync (HubSpot) ──
        HUBSPOT_VORMERKEN("Sync vormerken (Outbox)", Verfahren.HUBSPOT_SYNC),
        HUBSPOT_UEBERTRAGUNG("Kontakt/Firma übertragen", Verfahren.HUBSPOT_SYNC),
        HUBSPOT_CONSENT("Marketing-Einwilligung spiegeln", Verfahren.HUBSPOT_SYNC),
        HUBSPOT_ERASURE("Recht auf Vergessen", Verfahren.HUBSPOT_SYNC),
        HUBSPOT_RUECKKANAL("Consent-Rückkanal (Webhook)", Verfahren.HUBSPOT_SYNC),
        // ── Verfahren: Benachrichtigung & Bestätigung ──
        BENACHRICHTIGUNG_AUSLOESEN("Benachrichtigung auslösen", Verfahren.KOMMUNIKATION),
        KANAL_ZUSTELLUNG("Portal-Inbox & Kanal-Zustellung", Verfahren.KOMMUNIKATION),
        PFLICHT_BESTAETIGUNG("Pflicht-Bestätigung", Verfahren.KOMMUNIKATION);

        public final String label;
        public final Verfahren verfahren;

        Phase(String label, Verfahren verfahren) {
            this.label = label;
            this.verfahren = verfahren;
        }
    }
}
