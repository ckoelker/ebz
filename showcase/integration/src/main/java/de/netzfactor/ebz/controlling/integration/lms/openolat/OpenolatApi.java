package de.netzfactor.ebz.controlling.integration.lms.openolat;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Typsicherer Zugriff auf die OpenOLAT-REST-API ({@code /restapi}). Basis-URL aus
 * {@code quarkus.rest-client.openolat.url} (Container {@code http://openolat:8080}, Host {@code :8089}).
 * Authentifizierung = HTTP Basic des OpenOLAT-Administrators (Showcase) bzw. eines dedizierten
 * Service-Accounts (Prod) — als {@code Authorization}-Header je Aufruf übergeben (wie {@code VendureAdminApi}).
 * <p>
 * Nur die für die Provisionierung nötigen Endpunkte (gegen {@code /restapi/openapi.json} verifiziert):
 * User-Lookup nach OAuth-Identität, User-Anlage, OAuth-Authentifizierung anlegen, Ein-/Ausschreiben.
 */
@Path("/restapi")
@RegisterRestClient(configKey = "openolat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OpenolatApi {

    /** Sucht User nach OAuth-Authentifizierung ({@code authProvider=KEYCLOAK}, {@code authUsername=<sub>}). */
    @GET
    @Path("/users")
    JsonNode findUsersByAuth(@HeaderParam("Authorization") String auth,
            @QueryParam("authProvider") String authProvider, @QueryParam("authUsername") String authUsername);

    /** Legt einen neuen User an (UserVO) → Antwort enthält den vergebenen {@code key}. */
    @PUT
    @Path("/users")
    JsonNode createUser(@HeaderParam("Authorization") String auth, Map<String, Object> userVo);

    /** Legt (oder aktualisiert) eine Authentifizierung am User an (AuthenticationVO). */
    @PUT
    @Path("/users/{identityKey}/authentications")
    Response putAuthentication(@HeaderParam("Authorization") String auth,
            @PathParam("identityKey") long identityKey, Map<String, Object> authVo);

    /** Schreibt einen Teilnehmer in die Lernressource ein (idempotent: erneut = no-op). */
    @PUT
    @Path("/repo/entries/{repoEntryKey}/participants/{identityKey}")
    Response addParticipant(@HeaderParam("Authorization") String auth,
            @PathParam("repoEntryKey") long repoEntryKey, @PathParam("identityKey") long identityKey);

    /** Entfernt den Teilnehmer aus der Lernressource (idempotent). */
    @DELETE
    @Path("/repo/entries/{repoEntryKey}/participants/{identityKey}")
    Response removeParticipant(@HeaderParam("Authorization") String auth,
            @PathParam("repoEntryKey") long repoEntryKey, @PathParam("identityKey") long identityKey);
}
