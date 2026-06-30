package de.netzfactor.ebz.controlling.integration.rechnung.datev;

/**
 * D5-Port: Transport einer E-Rechnung über das <b>Peppol-/TRAFFIQX-Netz</b> (DATEV SmartTransfer) —
 * der zweite Versandweg <i>neben</i> dem E-Mail-Versand, für B2B-Empfänger im Netz. Die Erzeugung bleibt
 * eigen (ZUGFeRD); hier geht nur der Transport raus. Es gibt (anders als D1/D2) keine von uns verifizierte
 * Self-Service-API für SmartTransfer → Showcase über {@link PeppolVersandMock}; der reale SmartTransfer-/
 * TRAFFIQX-Adapter (Transport-Vertrag offen) tritt später an dieselbe Stelle.
 */
public interface PeppolVersand {

    /** Übergibt das ZUGFeRD-Dokument ans Netz und liefert eine Übertragungsquittung. */
    Quittung versende(byte[] zugferdPdf, String empfaengerId, String belegnummer);

    /** Quittung: Netzwerk (Peppol/TRAFFIQX), Übertragungs-ID, Empfänger-Peppol-ID, Hinweis. */
    record Quittung(String netzwerk, String uebertragungsId, String empfaengerId, String hinweis) {
    }
}
