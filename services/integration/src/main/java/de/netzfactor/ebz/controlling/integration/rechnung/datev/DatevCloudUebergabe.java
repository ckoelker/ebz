package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.common.annotation.Identifier;

/**
 * Cloud-Weg (real): überträgt die Buchungssätze an den DATEV-<b>Buchungsdatenservice</b>
 * ({@code accounting:extf-files}). Lädt dieselbe EXTF-Datei wie der {@code extf}-Weg hoch
 * ({@link ExtfBuchungsstapel}) — nur eben per API statt als CSV-Datei — und pollt den
 * Verarbeitungs-Job bis zum Ergebnis. Access-Token + Refresh-Rotation liefert der
 * {@link DatevTokenService}; Pflicht-Header {@code X-DATEV-Client-Id} (= OAuth-{@code client_id})
 * geht bei jedem Call mit. Tritt an dieselbe Port-Stelle wie {@code extf}/{@code cloud-mock}.
 */
@ApplicationScoped
@Identifier("cloud")
public class DatevCloudUebergabe implements DatevUebergabe {

    private static final Logger LOG = Logger.getLogger(DatevCloudUebergabe.class);
    private static final Set<String> IN_ARBEIT =
            Set.of("processing", "pending", "running", "new", "accepted", "queued", "in_progress", "received");
    private static final Set<String> FEHLER = Set.of("failed", "error", "rejected", "invalid");

    @Inject
    DatevTokenService token;

    @Inject
    @RestClient
    DatevCloudApi.Extf extfApi;

    @Inject
    DatevKonten konten;

    @Override
    public Protokoll uebergeben(List<Buchungssatz> saetze, ExtfBuchungsstapel.Kopf kopf) {
        byte[] extf = ExtfBuchungsstapel.bytes(saetze, kopf);
        String bearer = "Bearer " + token.accessToken();
        String app = konten.cloud().clientId();
        String mandant = konten.cloud().mandant();

        String jobId;
        try (Response r = extfApi.importieren(mandant, bearer, app, extf)) {
            int sc = r.getStatus();
            if (sc < 200 || sc >= 300) {
                throw new IllegalStateException("DATEV-Import abgelehnt (HTTP " + sc + ").");
            }
            jobId = jobReferenz(r);
        }

        String status = polleJob(mandant, jobId, bearer, app);
        if (FEHLER.contains(status.toLowerCase())) {
            throw new IllegalStateException("DATEV-Buchungsdatenservice-Job " + jobId + " fehlgeschlagen (Status " + status + ").");
        }
        LOG.infof("DATEV-Cloud: %d Buchungen übergeben, Job %s Status %s (Mandant %s)",
                saetze.size(), jobId, status, mandant);
        return new Protokoll("CLOUD", jobId, saetze.size(), null,
                "DATEV Buchungsdatenservice (accounting:extf-files) — Job " + jobId + ", Status " + status + ".");
    }

    /** Job-Referenz aus dem {@code Location}-Header (Fallback: {@code id} im Body). */
    private String jobReferenz(Response r) {
        String loc = r.getHeaderString("Location");
        if (loc != null && !loc.isBlank()) {
            int i = loc.lastIndexOf('/');
            return i >= 0 ? loc.substring(i + 1) : loc;
        }
        try {
            JsonNode body = r.readEntity(JsonNode.class);
            if (body != null) {
                for (String feld : List.of("id", "jobId", "job_id")) {
                    if (body.hasNonNull(feld)) {
                        return body.get(feld).asText();
                    }
                }
            }
        } catch (RuntimeException ignore) {
            // kein lesbarer Body → unten als fehlende Referenz behandeln
        }
        throw new IllegalStateException("DATEV-Import ohne Job-Referenz (kein Location-Header).");
    }

    /** Pollt den Job bis er nicht mehr „in Arbeit" ist (oder die Versuche erschöpft sind). */
    private String polleJob(String mandant, String jobId, String bearer, String app) {
        int versuche = Math.max(1, konten.cloud().jobPollVersuche());
        long pause = Math.max(0, konten.cloud().jobPollPauseMs());
        String status = "unknown";
        for (int i = 0; i < versuche; i++) {
            JsonNode job = extfApi.jobStatus(mandant, jobId, bearer, app);
            status = job != null && job.hasNonNull("status") ? job.get("status").asText() : "unknown";
            if (!IN_ARBEIT.contains(status.toLowerCase())) {
                return status;
            }
            if (i < versuche - 1 && pause > 0) {
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return status;
    }
}
