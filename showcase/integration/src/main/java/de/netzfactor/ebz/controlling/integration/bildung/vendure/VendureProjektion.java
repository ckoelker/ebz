package de.netzfactor.ebz.controlling.integration.bildung.vendure;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.bildung.model.PreisModell;

/**
 * Projiziert ein verkäufliches Bildungsangebot ({@code shopVerkauf=true}) als Produkt <b>mit Variante</b>
 * in Vendure und gibt Produkt- und Varianten-ID zurück (Naht Kern→Shop, §11.6). Vendure bleibt
 * Geld-/Buchungs-SoR; der MDM-Kern ist Quelle der Katalogstammdaten und stößt die Projektion an.
 * <p>
 * <b>Zahlplan (F3–F6) datengetrieben:</b> {@code preisModell} + die gemeinsamen Felder
 * ({@code preisCent}, {@code abrechnungIntervallMonate}, {@code ratenGesamt}) werden 1:1 auf die
 * Varianten-Custom-Fields der {@code ShowcaseSubscriptionStrategy} gemappt:
 * <ul>
 *   <li>EINMALIG → schlichte Variante (Preis), keine Subscription (F3).</li>
 *   <li>ABO      → subscriptionInterval=month, intervalCount=abrechnungIntervallMonate, total=0 (unbefristet, F4).</li>
 *   <li>RATEN     → subscriptionInterval=month, intervalCount=abrechnungIntervallMonate, total=ratenGesamt
 *                  (z. B. Berufsschule 6×halbjährlich = 6/6 F5; Studiengang 36×monatlich = 1/36 F6).</li>
 * </ul>
 * Idempotent: ohne {@code vendureProductId}/{@code vendureVariantId} wird angelegt, sonst aktualisiert.
 * <p>
 * Auth: nativer Superadmin-Login über die Admin-API (Bearer-Token-Methode). <i>Produktion:</i> ein
 * dedizierter Keycloak-Service-Account, gemappt auf eine schreib-gescopte Vendure-Rolle (die bestehende
 * {@code KeycloakAdminAuthStrategy} mappt absichtlich nur read-only {@code sso-staff}).
 */
@ApplicationScoped
public class VendureProjektion {

    private static final String AUTH_TOKEN_HEADER = "vendure-auth-token";

    /** Rückgabe der Projektion: die zurückzuschreibenden Vendure-IDs. */
    public record Ergebnis(String productId, String variantId) {
    }

    @RestClient
    VendureAdminApi api;

    @ConfigProperty(name = "vendure.superadmin.username")
    String superadminUser;

    @ConfigProperty(name = "vendure.superadmin.password")
    String superadminPass;

    /** Legt Produkt + Variante an bzw. aktualisiert sie und liefert die Vendure-IDs. */
    public Ergebnis projiziere(Bildungsangebot e) {
        String token = login();
        String auth = "Bearer " + token;

        String productId = projiziereProdukt(auth, e);
        String variantId = e.preisCent == null ? e.vendureVariantId : projiziereVariante(auth, e, productId);
        return new Ergebnis(productId, variantId);
    }

    // ── Produkt ──
    private String projiziereProdukt(String auth, Bildungsangebot e) {
        Map<String, Object> translation = Map.of(
                "languageCode", "de",
                "name", e.titel,
                "slug", slug(e.code),
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

    // ── Variante (Preis + Zahlplan) ──
    private String projiziereVariante(String auth, Bildungsangebot e, String productId) {
        boolean subscription = e.preisModell == PreisModell.ABO || e.preisModell == PreisModell.RATEN;

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("fulfillmentType", subscription ? "subscription" : "seminar");
        if (subscription) {
            customFields.put("subscriptionInterval", "month");
            customFields.put("subscriptionIntervalCount", e.abrechnungIntervallMonate == null ? 1 : e.abrechnungIntervallMonate);
            customFields.put("subscriptionTotalCount", e.ratenGesamt == null ? 0 : e.ratenGesamt);
        }

        if (e.vendureVariantId == null || e.vendureVariantId.isBlank()) {
            Map<String, Object> input = new HashMap<>();
            input.put("productId", productId);
            input.put("sku", e.code);
            input.put("price", e.preisCent);
            input.put("translations", List.of(Map.of("languageCode", "de", "name", e.titel)));
            input.put("optionIds", List.of());
            input.put("customFields", customFields);
            JsonNode data = call(auth,
                    "mutation($input: [CreateProductVariantInput!]!){ createProductVariants(input: $input){ id } }",
                    Map.of("input", List.of(input)));
            return data.path("createProductVariants").path(0).path("id").asText();
        }

        Map<String, Object> input = new HashMap<>();
        input.put("id", e.vendureVariantId);
        input.put("price", e.preisCent);
        input.put("customFields", customFields);
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
