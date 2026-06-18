package de.netzfactor.ebz.controlling.integration.shop;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonObject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.bildung.vendure.GraphQLRequest;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureAdminApi;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.VariableType;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClientBuilder;

/**
 * Quarkus-nativer Zugang zur Vendure-Admin-GraphQL-API (Produktkatalog P1, §A0).
 * <p>
 * <b>Login</b> läuft über den bestehenden Raw-RestClient {@link VendureAdminApi}, weil Vendure das
 * Admin-Auth-Token <i>nur im Response-Header</i> {@code vendure-auth-token} liefert (Bearer-Methode) —
 * darauf kommt der GraphQL-Client nicht heran. Mit dem Token wird ein {@link DynamicGraphQLClient}
 * (SmallRye, {@code quarkus-smallrye-graphql-client}) gebaut, über den alle weiteren Operationen
 * als typisierte DSL-Dokumente (+ Variablen-Maps) laufen — keine handgeschriebenen Query-Strings,
 * keine Fremd-Runtime.
 */
@ApplicationScoped
public class VendureAdmin {

    private static final String AUTH_TOKEN_HEADER = "vendure-auth-token";

    @RestClient
    VendureAdminApi loginApi;

    @ConfigProperty(name = "quarkus.rest-client.vendure-admin.url")
    String baseUrl;

    @ConfigProperty(name = "vendure.superadmin.username")
    String superadminUser;

    @ConfigProperty(name = "vendure.superadmin.password")
    String superadminPass;

    /** Token + Client einer Admin-Sitzung. Das Token wird zusätzlich für den Multipart-Asset-Upload gebraucht. */
    public record Verbindung(String token, DynamicGraphQLClient client) {
    }

    /**
     * Wandelt einen GraphQL-Typnamen-String (z. B. {@code "CreateProductInput!"}, {@code "ID!"},
     * {@code "[CreateProductVariantInput!]!"}) in einen {@link VariableType} um. Nötig, weil
     * {@code Variable.var(name, String)} kein {@code !}/{@code []} im Namen erlaubt — Non-Null/List
     * müssen über {@code nonNull(...)}/{@code list(...)} ausgedrückt werden.
     */
    public static VariableType vt(String typ) {
        String t = typ.trim();
        if (t.endsWith("!")) {
            return VariableType.nonNull(vt(t.substring(0, t.length() - 1)));
        }
        if (t.startsWith("[") && t.endsWith("]")) {
            return VariableType.list(vt(t.substring(1, t.length() - 1)));
        }
        return VariableType.varType(t);
    }

    /** Loggt als Superadmin ein und liefert Token + authentifizierten Dynamic-GraphQL-Client (Bearer-Header). */
    public Verbindung anmelden() {
        String token = login();
        DynamicGraphQLClient client = DynamicGraphQLClientBuilder.newBuilder()
                .url(baseUrl + "/admin-api")
                .header("Authorization", "Bearer " + token)
                .build();
        return new Verbindung(token, client);
    }

    /** Führt ein DSL-Dokument (mit Variablen) aus, wirft bei GraphQL-Fehlern und liefert den {@code data}-Knoten. */
    public JsonObject fuehreAus(DynamicGraphQLClient client, Document doc, Map<String, Object> variables) {
        try {
            Response r = client.executeSync(doc, variables);
            if (r.hasError()) {
                throw new VendureException("Vendure-GraphQL-Fehler: " + r.getErrors());
            }
            return r.getData();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VendureException("Vendure-Admin-API-Aufruf unterbrochen: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new VendureException("Vendure-Admin-API-Aufruf fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Nativer Superadmin-Login → Bearer-Token aus dem {@code vendure-auth-token}-Antwort-Header. */
    private String login() {
        GraphQLRequest req = new GraphQLRequest(
                "mutation($u: String!, $p: String!){ login(username: $u, password: $p){"
                        + " __typename ... on CurrentUser { id } ... on ErrorResult { errorCode message } } }",
                Map.of("u", superadminUser, "p", superadminPass));
        try (jakarta.ws.rs.core.Response resp = loginApi.execute(null, req)) {
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
}
