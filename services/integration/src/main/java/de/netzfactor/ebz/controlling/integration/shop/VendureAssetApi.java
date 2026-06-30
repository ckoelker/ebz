package de.netzfactor.ebz.controlling.integration.shop;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.PartFilename;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

/**
 * Asset-Upload zur Vendure-Admin-API als GraphQL-<b>Multipart</b>-Request (Upload-Scalar) — das kann
 * der GraphQL-Dynamic-Client nicht, daher dieser separate {@code quarkus-rest-client}-Multipart-Pfad
 * (Quarkus-nativ, [[prefer-quarkus-quarkiverse-extension]]). Teilt die Basis-URL/Config mit
 * {@code VendureAdminApi} (configKey {@code vendure-admin}).
 * <p>
 * Aufbau nach graphql-multipart-request-spec: Teil {@code operations} (Query+Variablen mit
 * {@code file:null}), Teil {@code map} (Zuordnung Datei→Variable), Teil {@code 0} (die Datei).
 * Der Datei-Teil trägt einen <b>expliziten</b> MIME-Typ + Dateinamen (Auto-Detection würde
 * {@code application/octet-stream} senden, das Vendure ablehnt). Da nur zwei feste Platzhalter
 * hochgeladen werden (Bild/PDF), genügen zwei typisierte Varianten mit konstantem Dateinamen.
 */
@Path("/admin-api")
@RegisterRestClient(configKey = "vendure-admin")
public interface VendureAssetApi {

    String PLATZHALTER_BILD = "platzhalter-bild.png";
    String PLATZHALTER_PDF = "platzhalter-dokument.pdf";

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response createBildAsset(@HeaderParam("Authorization") String authorization, BildUpload form);

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response createPdfAsset(@HeaderParam("Authorization") String authorization, PdfUpload form);

    /** Multipart-Body für ein PNG-Platzhalterbild. */
    class BildUpload {
        @RestForm("operations")
        @PartType(MediaType.APPLICATION_JSON)
        public String operations;

        @RestForm("map")
        @PartType(MediaType.APPLICATION_JSON)
        public String map;

        @RestForm("0")
        @PartType("image/png")
        @PartFilename(PLATZHALTER_BILD)
        public byte[] file;
    }

    /** Multipart-Body für ein PDF-Platzhalterdokument. */
    class PdfUpload {
        @RestForm("operations")
        @PartType(MediaType.APPLICATION_JSON)
        public String operations;

        @RestForm("map")
        @PartType(MediaType.APPLICATION_JSON)
        public String map;

        @RestForm("0")
        @PartType("application/pdf")
        @PartFilename(PLATZHALTER_PDF)
        public byte[] file;
    }
}
