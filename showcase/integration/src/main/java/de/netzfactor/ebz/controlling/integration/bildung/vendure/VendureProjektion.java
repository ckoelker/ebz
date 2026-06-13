package de.netzfactor.ebz.controlling.integration.bildung.vendure;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;

/**
 * Projiziert ein verkäufliches Bildungsangebot ({@code shopVerkauf=true}) als Produkt in Vendure
 * und gibt die Vendure-Produkt-ID zurück (Naht Kern→Shop, §11.6). Vendure bleibt Geld-/Buchungs-SoR;
 * der MDM-Kern ist Quelle der Katalogstammdaten und stößt die Projektion an.
 * <p>
 * Idempotent: ohne {@code vendureProductId} wird {@code createProduct} gerufen, sonst {@code updateProduct}
 * (Re-Projektion hält Titel/Beschreibung synchron). <b>Bewusst nur das Produkt</b> — Variante/Preis
 * (preisModell → SubscriptionStrategy) bleiben F3–F6.
 * <p>
 * Auth: nativer Superadmin-Login über die Admin-API (Bearer-Token-Methode). <i>Produktion:</i> ein
 * dedizierter Keycloak-Service-Account, gemappt auf eine schreib-gescopte Vendure-Rolle (die bestehende
 * {@code KeycloakAdminAuthStrategy} mappt absichtlich nur read-only {@code sso-staff}).
 */
@ApplicationScoped
public class VendureProjektion {

    private static final String AUTH_TOKEN_HEADER = "vendure-auth-token";

    @RestClient
    VendureAdminApi api;

    @ConfigProperty(name = "vendure.superadmin.username")
    String superadminUser;

    @ConfigProperty(name = "vendure.superadmin.password")
    String superadminPass;

    /** Legt das Produkt an bzw. aktualisiert es und liefert die Vendure-Produkt-ID. */
    public String projiziere(Bildungsangebot e) {
        String token = login();
        String auth = "Bearer " + token;

        Map<String, Object> translation = Map.of(
                "languageCode", "de",
                "name", e.titel,
                "slug", slug(e.code),
                "description", e.kurzbeschreibung == null ? "" : e.kurzbeschreibung);

        if (e.vendureProductId == null || e.vendureProductId.isBlank()) {
            Map<String, Object> input = Map.of(
                    "enabled", true,
                    "translations", List.of(translation));
            JsonNode data = call(auth,
                    "mutation($input: CreateProductInput!){ createProduct(input: $input){ id } }",
                    Map.of("input", input));
            return data.path("createProduct").path("id").asText();
        }

        Map<String, Object> input = Map.of(
                "id", e.vendureProductId,
                "enabled", true,
                "translations", List.of(translation));
        JsonNode data = call(auth,
                "mutation($input: UpdateProductInput!){ updateProduct(input: $input){ id } }",
                Map.of("input", input));
        return data.path("updateProduct").path("id").asText();
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

    /** Führt eine GraphQL-Mutation aus und gibt den {@code data}-Knoten zurück (wirft bei {@code errors}). */
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

    /** {@code code} (ASCII, ^[A-Z0-9-]{2,32}$) → URL-sicherer, eindeutiger Produkt-Slug. */
    private static String slug(String code) {
        return code.toLowerCase(Locale.ROOT);
    }
}
