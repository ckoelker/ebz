package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Showcase-Adapter für {@link PeppolVersand}: simuliert die Übergabe ans Peppol-/TRAFFIQX-Netz (DATEV
 * SmartTransfer) ohne echten Call und vergibt eine Übertragungs-ID. Damit ist der zweite Versandweg
 * architektonisch bewiesen; der reale SmartTransfer-Adapter (kostenpflichtiger Versand, ~€0,50/Rechnung)
 * tritt später an dieselbe Stelle.
 */
@ApplicationScoped
public class PeppolVersandMock implements PeppolVersand {

    private static final Logger LOG = Logger.getLogger(PeppolVersandMock.class);

    @Override
    public Quittung versende(byte[] zugferdPdf, String empfaengerId, String belegnummer) {
        if (zugferdPdf == null || zugferdPdf.length == 0) {
            throw new IllegalArgumentException("Leeres E-Rechnungs-Dokument — nichts zu versenden.");
        }
        String uebertragungsId = "TRX-" + UUID.randomUUID();
        LOG.infof("Peppol/TRAFFIQX (Mock): Beleg %s an %s übertragen, ID %s (%d Bytes)",
                belegnummer, empfaengerId, uebertragungsId, zugferdPdf.length);
        return new Quittung("Peppol/TRAFFIQX", uebertragungsId, empfaengerId,
                "Stellvertreter für DATEV SmartTransfer (TRAFFIQX/Peppol); Transport-API-Vertrag offen.");
    }
}
