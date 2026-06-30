package de.netzfactor.ebz.controlling.integration.hubspot.spi;

import java.util.Map;

/**
 * <b>Outbound-SPI nach HubSpot</b> (Adapter-Muster wie {@code KanalVersand}/{@code Zielsystemexport}) —
 * der einzige Berührungspunkt des Sync-Kerns mit dem Marketing-System. Hinter dem Port hängen zwei
 * Adapter: die {@code HubSpotMockSenke} (Default, ohne echtes HubSpot lauffähig, zeichnet Aufrufe für
 * Tests auf) und — ab H4 — die {@code HubSpotApiSenke} (Camel-HTTP gegen die echte CRM-API).
 * <p>
 * <b>Datenhoheit MDM→HubSpot:</b> die Methoden bilden ausschließlich die im MDM-Kern getroffenen
 * Entscheidungen ab. Operationen <b>müssen idempotent</b> sein (At-least-once durch die Outbox); Upserts
 * liefern die HubSpot-Objekt-ID zurück (→ {@code ExterneId}-Mapping). Fehlschläge werfen — der Dispatcher
 * übernimmt Backoff/Retry/Dead-Letter.
 */
public interface HubSpotSenke {

    /** HubSpot-Objektklasse, auf die sich eine Lösch-/Archiv-Operation bezieht. */
    enum ObjektTyp {
        CONTACT, COMPANY, ASSOCIATION
    }

    /**
     * Kontakt anlegen/aktualisieren (Upsert über die stabile externe ID {@code ebz_party_id}, <b>nicht</b>
     * E-Mail). Setzt nur MDM-eigene Felder; HubSpot-Marketer-Felder bleiben unberührt. Liefert die
     * HubSpot-Contact-ID.
     */
    String upsertContact(ContactDto contact);

    /** Firma anlegen/aktualisieren (Upsert über {@code ebz_org_id}). Liefert die HubSpot-Company-ID. */
    String upsertCompany(CompanyDto company);

    /** Verknüpft Kontakt↔Firma (v4-Association); idempotent. */
    void verknuepfe(String contactId, String companyId);

    /**
     * Spiegelt die Marketing-Einwilligung des Kunden: {@code erlaubt=false} schaltet Marketing ab
     * (Widerruf/Werbesperre — autonom erlaubt), {@code erlaubt=true} nur auf frisches MDM-Opt-in.
     * Property-basiert (tier-unabhängig); die Subscription-API ist optional und 403-sicher gekapselt.
     */
    void setzeMarketingStatus(String contactId, boolean erlaubt, ConsentNachweis nachweis);

    /** <b>Permanente</b> Art.-17-Löschung (GDPR-Delete); nicht wiederherstellbar. */
    void gdprLoesche(ObjektTyp typ, String hubspotId);

    /**
     * Nicht-permanenter Fallback (Archivieren) — genutzt, wenn der GDPR-Delete per Config abgeschaltet ist
     * ({@code hubspot.sync.gdpr-delete.enabled=false}); die endgültige Löschung übernimmt dann ein Mensch.
     */
    void archiviere(ObjektTyp typ, String hubspotId);

    /**
     * Kontakt-DTO (nur Marketing-relevante, datenminimierte Felder). {@code externeId} = stabiler
     * Upsert-Schlüssel; {@code marketingErlaubt} steuert den Marketing-Status; {@code weitere} = zusätzliche
     * HubSpot-Properties (offen erweiterbar, ohne den Port zu ändern).
     */
    record ContactDto(String externeId, String email, String vorname, String nachname,
                      String anrede, boolean marketingErlaubt, String leadQuelle,
                      Map<String, String> weitere) {
    }

    /** Firmen-DTO inkl. Segmentierungsmerkmale (Branche/Verband/Schwerpunkte/Bestandsgröße …). */
    record CompanyDto(String externeId, String name, String domain, String ustId,
                      String branche, String verbaende, String schwerpunkte, String unternehmenstyp,
                      Integer bestandsgroesse, String gewerbeerlaubnis, boolean ausbildungsbetrieb,
                      String ihkKammer, String leadQuelle, Map<String, String> weitere) {
    }

    /** Nachweis der Einwilligung (Rechtsgrundlage/Stand/Quelle) — für den HubSpot-Marketing-Status. */
    record ConsentNachweis(String rechtsgrundlage, String stand, String quelle) {
    }
}
