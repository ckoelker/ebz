package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.smallrye.common.annotation.Identifier;

/**
 * Cloud-Mock-Weg: Stellvertreter für den DATEV-Datenservice (Buchungsdatenservice in „DATEV
 * Unternehmen online" — R0-Klärung B1). Simuliert die API-Übergabe (kein echter Call), vergibt eine
 * Transfer-Referenz und protokolliert die Anzahl Buchungen. So ist die Architektur „DATEV hinter
 * Interface" bewiesen; die echte OAuth2-Anbindung tritt später an dieselbe Stelle.
 */
@ApplicationScoped
@Identifier("cloud-mock")
public class DatevCloudMockUebergabe implements DatevUebergabe {

    private static final Logger LOG = Logger.getLogger(DatevCloudMockUebergabe.class);

    @Override
    public Protokoll uebergeben(List<Buchungssatz> saetze, ExtfBuchungsstapel.Kopf kopf) {
        String transferId = "DATEV-CLOUD-" + UUID.randomUUID();
        LOG.infof("DATEV-Cloud (Mock): %d Buchungen übergeben, Transfer %s (Berater %s, Mandant %s)",
                saetze.size(), transferId, kopf.beraternummer(), kopf.mandantennummer());
        return new Protokoll("CLOUD-MOCK", transferId, saetze.size(), null,
                "Stellvertreter für den DATEV-Buchungsdatenservice (DATEV Unternehmen online).");
    }
}
