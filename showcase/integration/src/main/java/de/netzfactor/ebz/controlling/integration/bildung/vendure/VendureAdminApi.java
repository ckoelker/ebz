package de.netzfactor.ebz.controlling.integration.bildung.vendure;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Typsicherer HTTP-Zugriff auf die Vendure-Admin-GraphQL-API (ein einziger POST-Endpunkt).
 * Basis-URL kommt aus {@code quarkus.rest-client.vendure-admin.url} (Container: {@code http://server:3000}).
 * <p>
 * Liefert die rohe {@link Response} zurück, damit der {@link VendureProjektion}-Service sowohl den
 * Body (GraphQL {@code data}/{@code errors}) als auch den Login-Antwort-Header
 * {@code vendure-auth-token} (Bearer-Token-Methode) auswerten kann.
 */
@Path("/admin-api")
@RegisterRestClient(configKey = "vendure-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface VendureAdminApi {

    /** {@code authorization} darf {@code null} sein (Login ist anonym) → Header wird dann weggelassen. */
    @POST
    Response execute(@HeaderParam("Authorization") String authorization, GraphQLRequest request);
}
