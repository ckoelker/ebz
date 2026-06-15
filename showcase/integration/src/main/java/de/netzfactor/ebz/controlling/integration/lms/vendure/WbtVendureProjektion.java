package de.netzfactor.ebz.controlling.integration.lms.vendure;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.bildung.vendure.GraphQLRequest;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureAdminApi;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;

/**
 * Projiziert einen verkäuflichen {@link WbtKurs} als Vendure-Produkt <b>mit Variante</b> und schreibt
 * Produkt-/Varianten-ID zurück (gleiche Naht wie die Bildungs-Shop-Projektion, §11.6). Ein WBT ist ein
 * <b>digitales Einmalkauf-Produkt</b> → Varianten-Custom-Field {@code fulfillmentType=digital} (kein
 * Subscription-Plan). Idempotent: ohne {@code vendureProductId}/{@code vendureVariantId} wird angelegt,
 * sonst aktualisiert.
 * <p>
 * Wiederverwendet bewusst die generische Admin-API ({@link VendureAdminApi}, {@link GraphQLRequest},
 * {@link VendureException}); die Vendure-IDs sind die einzige Kopplung in Richtung Commerce. Auth =
 * nativer Superadmin-Login (Bearer-Token-Header), wie bei der Bildungs-Projektion; Prod = Keycloak-
 * Service-Account auf einer schreib-gescopten Vendure-Rolle.
 */
@ApplicationScoped
public class WbtVendureProjektion {

    private static final String AUTH_TOKEN_HEADER = "vendure-auth-token";

    /** Rückgabe: die zurückzuschreibenden Vendure-IDs. */
    public record Ergebnis(String productId, String variantId) {
    }

    @RestClient
    VendureAdminApi api;

    @ConfigProperty(name = "vendure.superadmin.username")
    String superadminUser;

    @ConfigProperty(name = "vendure.superadmin.password")
    String superadminPass;

    public Ergebnis projiziere(WbtKurs e) {
        String auth = "Bearer " + login();
        String productId = projiziereProdukt(auth, e);
        String variantId = e.preisCent == null ? e.vendureVariantId : projiziereVariante(auth, e, productId);
        return new Ergebnis(productId, variantId);
    }

    private String projiziereProdukt(String auth, WbtKurs e) {
        Map<String, Object> translation = Map.of(
                "languageCode", "de",
                "name", e.titel,
                "slug", e.code.toLowerCase(Locale.ROOT),
                "description", e.kurzbeschreibung == null ? "" : e.kurzbeschreibung);

        if (e.vendureProductId == null || e.vendureProductId.isBlank()) {
            JsonNode data = call(auth,
                    "mutation($input: CreateProductInput!){ createProduct(input: $input){ id } }",
                    Map.of("input", Map.of("enabled", true, "translations", List.of(translation))));
            return data.path("createProduct").path("id").asText();
        }
        JsonNode data = call(auth,
                "mutation($input: UpdateProductInput!){ updateProduct(input: $input){ id } }",
                Map.of("input", Map.of("id", e.vendureProductId, "enabled", true, "translations", List.of(translation))));
        return data.path("updateProduct").path("id").asText();
    }

    private String projiziereVariante(String auth, WbtKurs e, String productId) {
        // WBT = digital, Einmalkauf → fulfillmentType=digital (gültiger Custom-Field-Wert), kein Subscription-Plan.
        Map<String, Object> customFields = Map.of("fulfillmentType", "digital");

        if (e.vendureVariantId == null || e.vendureVariantId.isBlank()) {
            Map<String, Object> input = Map.of(
                    "productId", productId,
                    "sku", e.code,
                    "price", e.preisCent,
                    "translations", List.of(Map.of("languageCode", "de", "name", e.titel)),
                    "optionIds", List.of(),
                    "customFields", customFields);
            JsonNode data = call(auth,
                    "mutation($input: [CreateProductVariantInput!]!){ createProductVariants(input: $input){ id } }",
                    Map.of("input", List.of(input)));
            return data.path("createProductVariants").path(0).path("id").asText();
        }

        Map<String, Object> input = Map.of(
                "id", e.vendureVariantId,
                "price", e.preisCent,
                "customFields", customFields);
        JsonNode data = call(auth,
                "mutation($input: [UpdateProductVariantInput!]!){ updateProductVariants(input: $input){ id } }",
                Map.of("input", List.of(input)));
        return data.path("updateProductVariants").path(0).path("id").asText();
    }

    /** Nativer Superadmin-Login → Bearer-Token aus dem {@code vendure-auth-token}-Antwort-Header. */
    private String login() {
        GraphQLRequest req = new GraphQLRequest(
                "mutation($u: String!, $p: String!){ login(username: $u, password: $p){"
                        + " __typename ... on CurrentUser { id } ... on ErrorResult { errorCode message } } }",
                Map.of("u", superadminUser, "p", superadminPass));
        try (Response resp = api.execute(null, req)) {
            JsonNode body = resp.readEntity(JsonNode.class);
            JsonNode errors = body.get("errors");
            if (errors != null && errors.size() > 0) {
                throw new VendureException("Vendure-Login fehlgeschlagen: " + errors.toString());
            }
            JsonNode loginNode = body.path("data").path("login");
            if (!"CurrentUser".equals(loginNode.path("__typename").asText())) {
                throw new VendureException("Vendure-Login abgelehnt: " + loginNode.path("message").asText("unbekannt"));
            }
            String token = resp.getHeaderString(AUTH_TOKEN_HEADER);
            if (token == null || token.isBlank()) {
                throw new VendureException("Vendure lieferte keinen " + AUTH_TOKEN_HEADER + "-Header");
            }
            return token;
        } catch (VendureException ve) {
            throw ve;
        } catch (RuntimeException re) {
            throw new VendureException("Vendure-Admin-API nicht erreichbar: " + re.getMessage(), re);
        }
    }

    private JsonNode call(String auth, String query, Map<String, Object> variables) {
        try (Response resp = api.execute(auth, new GraphQLRequest(query, variables))) {
            JsonNode body = resp.readEntity(JsonNode.class);
            JsonNode errors = body.get("errors");
            if (errors != null && errors.size() > 0) {
                throw new VendureException("Vendure-GraphQL-Fehler: " + errors.toString());
            }
            return body.path("data");
        } catch (VendureException ve) {
            throw ve;
        } catch (RuntimeException re) {
            throw new VendureException("Vendure-Admin-API-Aufruf fehlgeschlagen: " + re.getMessage(), re);
        }
    }
}
