package de.netzfactor.ebz.controlling.integration.lms.openolat;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
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

    // ── Mandanten-Schicht (Organisations / Content-Share / Membership) — gegen 20.1/openapi.json verifiziert ──

    /** Listet alle Organisationen flach (für die Idempotenz-Suche über {@code externalId}). */
    @GET
    @Path("/organisations")
    JsonNode listOrganisations(@HeaderParam("Authorization") String auth);

    /** Legt eine Organisation an (OrganisationVO) → Antwort enthält den vergebenen {@code key} (M2). */
    @PUT
    @Path("/organisations")
    JsonNode createOrganisation(@HeaderParam("Authorization") String auth, Map<String, Object> organisationVo);

    /** Aktualisiert eine Organisation (OrganisationVO mit {@code key}) — u. a. {@code cssClass}/{@code displayName} (M0). */
    @POST
    @Path("/organisations")
    JsonNode updateOrganisation(@HeaderParam("Authorization") String auth, Map<String, Object> organisationVo);

    /** Mitglieder einer Organisation in einer Rolle (z. B. {@code user}) — für die Seat-Zählung (M5). */
    @GET
    @Path("/organisations/{organisationKey}/{role}")
    JsonNode listOrganisationMembers(@HeaderParam("Authorization") String auth,
            @PathParam("organisationKey") long organisationKey, @PathParam("role") String role);

    /** Macht den User zum Mitglied der Organisation in der Rolle {@code role} (idempotent). */
    @PUT
    @Path("/organisations/{organisationKey}/{role}/{identityKey}")
    Response addOrganisationMember(@HeaderParam("Authorization") String auth,
            @PathParam("organisationKey") long organisationKey, @PathParam("role") String role,
            @PathParam("identityKey") long identityKey);

    /** Entfernt die Org-Mitgliedschaft wieder (idempotent). */
    @DELETE
    @Path("/organisations/{organisationKey}/{role}/{identityKey}")
    Response removeOrganisationMember(@HeaderParam("Authorization") String auth,
            @PathParam("organisationKey") long organisationKey, @PathParam("role") String role,
            @PathParam("identityKey") long identityKey);

    /**
     * Verknüpft einen Repository-Eintrag (SCORM) mit einer Organisation — der <b>content-share-once</b>-Hebel
     * (M4): die Ressource liegt <i>einmal</i> im Repo und wird n Organisationen sichtbar gemacht (Storage × 1).
     */
    @PUT
    @Path("/repo/entries/{repoEntryKey}/organisations/{organisationKey}")
    Response linkRepoEntryToOrganisation(@HeaderParam("Authorization") String auth,
            @PathParam("repoEntryKey") long repoEntryKey, @PathParam("organisationKey") long organisationKey);

    // ── Nachweis-Seam (M6): trackbarer WBT-Kurs als REST-lesbare Completion-Quelle ──
    // Begründung (openapi.json-Audit, F3): Completion ist in OpenOLAT-REST NUR kurs-skopiert lesbar
    // (assessments/statements/certificates unter /repo/courses/…). Das geteilte Nugget ist ein bares
    // FileResource.SCORMCP (kein Kurs) → kein REST-Completion. Ein per REST gebauter Kurs mit
    // Assessment-Knoten ist die offizielle, UI-freie Nachweis-Quelle (Nugget bleibt unberührt).

    /** Legt einen Kurs an (Query-Parameter statt Body, wie die OpenOLAT-REST-API) → CourseVO mit {@code key}/{@code editorRootNodeId}. */
    @PUT
    @Path("/repo/courses")
    JsonNode createCourse(@HeaderParam("Authorization") String auth,
            @QueryParam("shortTitle") String shortTitle, @QueryParam("title") String title,
            @QueryParam("displayName") String displayName, @QueryParam("externalId") String externalId,
            @QueryParam("status") String status, @QueryParam("access") Integer access);

    /** Sucht Kurse nach {@code externalId} (Idempotenz) — CourseVO trägt {@code key} + {@code editorRootNodeId}. */
    @GET
    @Path("/repo/courses")
    JsonNode findCoursesByExternalId(@HeaderParam("Authorization") String auth,
            @QueryParam("externalId") String externalId);

    /** Hängt einen Assessment-(Nachweis-)Knoten an → CourseNodeVO mit {@code id} (= nodeIdent). */
    @PUT
    @Path("/repo/courses/{courseId}/elements/assessment")
    JsonNode addAssessmentElement(@HeaderParam("Authorization") String auth,
            @PathParam("courseId") long courseId, @QueryParam("parentNodeId") String parentNodeId,
            @QueryParam("shortTitle") String shortTitle, @QueryParam("longTitle") String longTitle);

    /** Publiziert den Kurs (macht den neuen Knoten live). */
    @POST
    @Path("/repo/courses/{courseId}/publish")
    Response publishCourse(@HeaderParam("Authorization") String auth, @PathParam("courseId") long courseId,
            @QueryParam("status") String status, @QueryParam("locale") String locale);

    /** Macht den Nachweis-Kurs einer Organisation sichtbar (org-skopiertes Sharing, analog M4). */
    @PUT
    @Path("/repo/courses/{courseId}/organisations/{organisationKey}")
    Response linkCourseToOrganisation(@HeaderParam("Authorization") String auth,
            @PathParam("courseId") long courseId, @PathParam("organisationKey") long organisationKey);

    /** Schreibt das Assessment-Ergebnis eines Lernenden (AssessableResultsVO) — die Completion in OpenOLAT (SoR). */
    @POST
    @Path("/repo/courses/{courseId}/assessments/{nodeId}")
    Response writeAssessmentResult(@HeaderParam("Authorization") String auth,
            @PathParam("courseId") long courseId, @PathParam("nodeId") String nodeId, Map<String, Object> resultVo);

    /** Liest das Assessment-Ergebnis eines Lernenden zum Knoten → AssessableResultsVO ({@code passed}/{@code assessmentDone}). */
    @GET
    @Path("/repo/courses/{courseId}/assessments/{nodeId}/users/{identityKey}")
    JsonNode readAssessmentResult(@HeaderParam("Authorization") String auth,
            @PathParam("courseId") long courseId, @PathParam("nodeId") String nodeId,
            @PathParam("identityKey") long identityKey);
}
