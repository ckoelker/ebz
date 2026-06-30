package de.netzfactor.ebz.controlling.integration.shop;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;

/**
 * Lädt die beiden Platzhalter-Assets (Bild/PDF) als Vendure-Asset hoch und liefert die Asset-ID
 * (Produktkatalog P1, §B-Assets). Nutzt den GraphQL-Multipart-Pfad ({@link VendureAssetApi}); der
 * Bearer-Token kommt aus dem {@link VendureAdmin}-Login.
 */
@ApplicationScoped
public class VendureAssetUploader {

    // Query mit file:null + Map-Zuordnung gemäß graphql-multipart-request-spec.
    private static final String OPERATIONS = "{\"query\":\"mutation($input:[CreateAssetInput!]!){"
            + "createAssets(input:$input){__typename ... on Asset{id name} ... on MimeTypeError{message}}}\","
            + "\"variables\":{\"input\":[{\"file\":null}]}}";
    private static final String MAP = "{\"0\":[\"variables.input.0.file\"]}";

    @RestClient
    VendureAssetApi api;

    /** Lädt ein PNG-Platzhalterbild hoch und gibt die Vendure-Asset-ID zurück. */
    public String uploadBild(String token, byte[] bytes) {
        VendureAssetApi.BildUpload form = new VendureAssetApi.BildUpload();
        form.operations = OPERATIONS;
        form.map = MAP;
        form.file = bytes;
        return assetId(api.createBildAsset("Bearer " + token, form));
    }

    /** Lädt ein PDF-Platzhalterdokument hoch und gibt die Vendure-Asset-ID zurück. */
    public String uploadPdf(String token, byte[] bytes) {
        VendureAssetApi.PdfUpload form = new VendureAssetApi.PdfUpload();
        form.operations = OPERATIONS;
        form.map = MAP;
        form.file = bytes;
        return assetId(api.createPdfAsset("Bearer " + token, form));
    }

    private String assetId(Response resp) {
        try (resp) {
            JsonNode body = resp.readEntity(JsonNode.class);
            JsonNode errors = body.get("errors");
            if (errors != null && errors.size() > 0) {
                throw new VendureException("Asset-Upload fehlgeschlagen: " + errors.toString());
            }
            JsonNode asset = body.path("data").path("createAssets").path(0);
            if (!"Asset".equals(asset.path("__typename").asText())) {
                throw new VendureException("Asset-Upload abgelehnt: " + asset.path("message").asText("unbekannt"));
            }
            return asset.path("id").asText();
        } catch (VendureException ve) {
            throw ve;
        } catch (RuntimeException re) {
            throw new VendureException("Asset-Upload-Aufruf fehlgeschlagen: " + re.getMessage(), re);
        }
    }
}
