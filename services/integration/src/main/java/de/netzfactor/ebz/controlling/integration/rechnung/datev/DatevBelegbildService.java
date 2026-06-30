package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * D2: lädt das Belegbild (ZUGFeRD-PDF eines festgeschriebenen Belegs) in den DATEV-Belegbilderservice
 * ({@code accounting:documents}, Belege online). Pro Beleg wird eine RFC4122-{@code GUID} erzeugt
 * (idempotent — DATEV nimmt jede GUID nur einmal an); die <b>Belegnummer</b> reist in den Metadaten mit
 * (= Belegfeld 1), sodass DATEV das Bild automatisch mit der Buchung verknüpft. Access-Token + Pflicht-
 * Header {@code X-DATEV-Client-Id} kommen wie bei D1 vom {@link DatevTokenService}/{@link DatevKonten}.
 */
@ApplicationScoped
public class DatevBelegbildService {

    private static final Logger LOG = Logger.getLogger(DatevBelegbildService.class);

    @Inject
    DatevTokenService token;

    @Inject
    @RestClient
    DatevCloudApi.Documents documentsApi;

    @Inject
    DatevKonten konten;

    @Inject
    Prozessspur prozess;

    /** Überträgt das PDF als Belegbild und liefert die vergebene Dokument-GUID + Belegbezug. */
    public Beleg uebertrage(byte[] pdf, String belegnummer) {
        if (pdf == null || pdf.length == 0) {
            throw new IllegalArgumentException("Leeres Belegbild (PDF) — nichts zu übertragen.");
        }
        String guid = UUID.randomUUID().toString(); // RFC4122 8-4-4-4-12
        String bearer = "Bearer " + token.accessToken();
        DatevCloudApi.Belegbild bild = new DatevCloudApi.Belegbild(metadata(belegnummer), pdf);
        try (Response r = documentsApi.upload(konten.cloud().mandant(), guid, bearer,
                konten.cloud().clientId(), bild)) {
            int sc = r.getStatus();
            if (sc < 200 || sc >= 300) {
                throw new IllegalStateException("DATEV-Belegbild abgelehnt (HTTP " + sc + ").");
            }
        }
        LOG.infof("DATEV-Belegbild übertragen: Beleg %s → Dokument %s (Mandant %s)",
                belegnummer, guid, konten.cloud().mandant());
        prozess.schritt("DATEV-Belegbild übertragen", Prozess.Akteur.EBZ, Prozess.System.DATEV,
                Prozess.Typ.SERVICE_TASK, Prozess.Phase.DATEV_EXPORT);
        return new Beleg(guid, belegnummer);
    }

    /**
     * Minimale Belegbild-Metadaten. Schlüssel-Feld für die Verknüpfung ist die Belegnummer (= Belegfeld 1
     * der Buchung). <i>Das vollständige DATEV-Metadaten-Schema (Belegtyp/Datum/USt …) ist gegen die
     * Sandbox final zu verifizieren.</i>
     */
    private static String metadata(String belegnummer) {
        String nr = belegnummer == null ? "" : belegnummer.replace("\"", "");
        return "{\"voucherNumber\":\"" + nr + "\",\"note\":\"EBZ Beleg " + nr + "\"}";
    }

    /** Übertragungs-Ergebnis: DATEV-Dokument-GUID + der verknüpfte Belegbezug. */
    public record Beleg(String documentGuid, String belegnummer) {
    }
}
