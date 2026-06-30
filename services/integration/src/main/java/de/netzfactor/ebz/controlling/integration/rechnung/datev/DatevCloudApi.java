package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Typsicherer Zugriff auf die DATEV-Cloud (Buchungsdatenservice, {@code accounting:extf-files}) —
 * gegen die Sandbox live verifiziert (siehe {@code docs/planung/rechnungsstellung-planung/
 * DATEV-Sandbox-Onboarding.md}). Bewusst zwei schmale Clients, da Token- und Daten-Host verschieden sind:
 * <ul>
 *   <li>{@link Token} → {@code sandbox-api.datev.de/token} (OAuth, opaque Tokens, Refresh rotiert),</li>
 *   <li>{@link Extf} → {@code accounting-extf-files.api.datev.de/platform-sandbox/v3/…} (Upload + Job-Poll).</li>
 * </ul>
 * <b>Pflicht-Header auf jedem Daten-Call:</b> {@code Authorization: Bearer …} <i>und</i>
 * {@code X-DATEV-Client-Id: <OAuth-client_id>} (ohne → 401). Basis-URLs aus
 * {@code quarkus.rest-client.datev-token.url} / {@code .datev-extf.url}.
 */
public final class DatevCloudApi {

    private DatevCloudApi() {
    }

    /** OAuth-Token-Endpoint: tauscht den (rotierenden) Refresh-Token gegen einen Access-Token. */
    @RegisterRestClient(configKey = "datev-token")
    public interface Token {

        @POST
        @Path("/token")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Produces(MediaType.APPLICATION_JSON)
        TokenResponse refresh(@HeaderParam("Authorization") String basicAuth,
                @FormParam("grant_type") String grantType, @FormParam("refresh_token") String refreshToken);
    }

    /** Buchungsdatenservice: EXTF-Datei hochladen (→ Job) und den Job-Status pollen. */
    @RegisterRestClient(configKey = "datev-extf")
    @Path("/platform-sandbox/v3/clients/{clientId}/extf-files")
    public interface Extf {

        /** Lädt die EXTF-Datei hoch → 201 mit {@code Location}-Header auf den Job. */
        @POST
        @Path("/import")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        @Produces(MediaType.APPLICATION_JSON)
        Response importieren(@PathParam("clientId") String clientId,
                @HeaderParam("Authorization") String bearer,
                @HeaderParam("X-DATEV-Client-Id") String oauthClientId, byte[] extfDatei);

        /** Pollt den Verarbeitungs-Job (Feld {@code status}: processing → completed/failed). */
        @GET
        @Path("/import/jobs/{jobId}")
        @Produces(MediaType.APPLICATION_JSON)
        JsonNode jobStatus(@PathParam("clientId") String clientId, @PathParam("jobId") String jobId,
                @HeaderParam("Authorization") String bearer,
                @HeaderParam("X-DATEV-Client-Id") String oauthClientId);
    }

    /**
     * Belegbilderservice ({@code accounting:documents}, Belege online): lädt das Belegbild (ZUGFeRD-PDF)
     * per <b>PUT mit selbst erzeugter RFC4122-GUID</b> hoch — idempotent (jede GUID nur einmal). Body =
     * Multipart: ein {@code metadata}-JSON (trägt u. a. die Belegnummer = Belegfeld 1 → automatische
     * Verknüpfung mit der Buchung) + die Datei. <i>Metadaten-Schema minimal; final gegen Sandbox verifizieren.</i>
     */
    @RegisterRestClient(configKey = "datev-documents")
    public interface Documents {

        @PUT
        @Path("/platform-sandbox/v2/clients/{clientId}/documents/{guid}")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Produces(MediaType.APPLICATION_JSON)
        Response upload(@PathParam("clientId") String clientId, @PathParam("guid") String guid,
                @HeaderParam("Authorization") String bearer,
                @HeaderParam("X-DATEV-Client-Id") String oauthClientId, Belegbild belegbild);
    }

    /** Multipart-Körper des Belegbild-Uploads: {@code metadata}-JSON + die Datei (kanonisches Client-POJO). */
    public static class Belegbild {

        @RestForm("metadata")
        @PartType(MediaType.APPLICATION_JSON)
        public String metadata;

        @RestForm("file1")
        @PartType("application/pdf")
        public byte[] datei;

        public Belegbild() {
        }

        public Belegbild(String metadata, byte[] datei) {
            this.metadata = metadata;
            this.datei = datei;
        }
    }

    /** OAuth-Token-Antwort (opaque Tokens). {@code expires_in} in Sekunden; {@code refresh_token} rotiert. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
