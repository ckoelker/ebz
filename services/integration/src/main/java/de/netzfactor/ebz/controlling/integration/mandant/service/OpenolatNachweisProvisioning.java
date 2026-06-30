package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatApi;
import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatException;

/**
 * OpenOLAT-Adapter des <b>Nachweis-Seams</b> (M6): baut/teilt den per REST trackbaren WBT-Kurs und liest/
 * schreibt die Completion (AssessableResultsVO). Hinter einem CDI-Bean, damit der Service-Test ihn per
 * {@code @io.quarkus.test.Mock} ersetzen kann (kein OpenOLAT-Call im Test) — Projekt-Konvention wie
 * {@code FakeOpenolatProvisioning}/{@link OpenolatOrganisationProvisioning}.
 * <p>
 * Endpunkte gegen {@code restapi/openapi.json} verifiziert: {@code PUT/GET /repo/courses},
 * {@code PUT …/elements/assessment}, {@code POST …/publish}, {@code PUT …/organisations/{key}},
 * {@code POST/GET …/assessments/{nodeId}…}. Der Kurs ist <b>idempotent</b> über die {@code externalId}.
 */
@ApplicationScoped
public class OpenolatNachweisProvisioning {

    private static final Logger LOG = Logger.getLogger(OpenolatNachweisProvisioning.class);

    @RestClient
    OpenolatApi api;

    @ConfigProperty(name = "openolat.admin.username", defaultValue = "administrator")
    String adminUser;

    @ConfigProperty(name = "openolat.admin.password", defaultValue = "openolat")
    String adminPass;

    /** Referenz des trackbaren Nachweis-Kurses: {@code courseId} (olatResourceId) + Assessment-{@code nodeId}. */
    public record KursRef(long courseId, String nodeId) {
    }

    /** Gelesene Completion eines Lernenden zum Nachweis-Knoten. */
    public record CompletionVO(boolean vorhanden, boolean bestanden, Instant abgeschlossenAm) {
    }

    /**
     * Stellt den trackbaren Nachweis-Kurs sicher (idempotent über {@code externalId}) und liefert
     * {@link KursRef}. Existiert er, wird der bestehende Kurs + Assessment-Knoten wiederverwendet; sonst
     * Kurs anlegen → Assessment-Knoten anhängen → publizieren.
     */
    public KursRef ensureNachweisKurs(String externalId, String displayName) {
        String auth = basicAuth();
        try {
            // Idempotenz über externalId: bestehenden Kurs wiederverwenden, sonst neu anlegen. In beiden
            // Fällen ist editorRootNodeId der Eltern-Knoten (kein editortreemodel — das liefert kein JSON).
            long courseId;
            String rootNodeId;
            JsonNode vorhanden = api.findCoursesByExternalId(auth, externalId);
            if (vorhanden != null && vorhanden.isArray() && !vorhanden.isEmpty()) {
                courseId = vorhanden.get(0).path("key").asLong();
                rootNodeId = vorhanden.get(0).path("editorRootNodeId").asText(null);
                LOG.infof("Nachweis-Kurs für externalId %s vorhanden (courseId %d) → Knoten sicherstellen", externalId, courseId);
            } else {
                JsonNode created = api.createCourse(auth, kurz(displayName), displayName, displayName, externalId, "published", 3);
                courseId = created.path("key").asLong();
                if (courseId <= 0) {
                    throw new OpenolatException("Nachweis-Kurs-Anlage ohne key (externalId " + externalId + ")");
                }
                rootNodeId = created.path("editorRootNodeId").asText(null);
                LOG.infof("Nachweis-Kurs angelegt: courseId %d (externalId %s)", courseId, externalId);
            }
            JsonNode node = api.addAssessmentElement(auth, courseId, rootNodeId, "Nachweis", displayName);
            String nodeId = node.path("id").asText(null);
            if (nodeId == null) {
                throw new OpenolatException("Assessment-Knoten-Anlage ohne id (courseId " + courseId + ")");
            }
            api.publishCourse(auth, courseId, "published", "de");
            return new KursRef(courseId, nodeId);
        } catch (OpenolatException oe) {
            throw oe;
        } catch (RuntimeException re) {
            throw new OpenolatException("Nachweis-Kurs-Provisionierung fehlgeschlagen (" + externalId + "): " + re.getMessage(), re);
        }
    }

    /** Macht den Nachweis-Kurs einer Organisation sichtbar (org-skopiertes Sharing). */
    public void linkKursZuOrg(long courseId, long organisationKey) {
        api.linkCourseToOrganisation(basicAuth(), courseId, organisationKey);
    }

    /**
     * Schreibt die Completion eines Lernenden in OpenOLAT (System-of-Record): {@code passed} + Status
     * {@code done} am Nachweis-Knoten. Das ist der API-Weg, eine Completion ohne UI festzuhalten.
     */
    public void meldeCompletion(long courseId, String nodeId, long identityKey, boolean bestanden) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("identityKey", identityKey);
        vo.put("nodeIdent", nodeId);
        vo.put("passed", bestanden);
        vo.put("assessmentStatus", "done");
        vo.put("userVisible", true);
        vo.put("fullyAssessed", true);
        api.writeAssessmentResult(basicAuth(), courseId, nodeId, vo);
    }

    /** Liest die Completion eines Lernenden; {@code vorhanden=false}, solange kein Abschluss vorliegt. */
    public CompletionVO leseCompletion(long courseId, String nodeId, long identityKey) {
        JsonNode r = api.readAssessmentResult(basicAuth(), courseId, nodeId, identityKey);
        if (r == null || r.isNull()) {
            return new CompletionVO(false, false, null);
        }
        JsonNode done = r.get("assessmentDone");
        if (done == null || done.isNull()) {
            return new CompletionVO(false, false, null);
        }
        boolean bestanden = r.path("passed").asBoolean(false);
        return new CompletionVO(true, bestanden, Instant.ofEpochMilli(done.asLong()));
    }

    private static String kurz(String s) {
        return s == null ? null : (s.length() > 25 ? s.substring(0, 25) : s);
    }

    private String basicAuth() {
        String token = Base64.getEncoder()
                .encodeToString((adminUser + ":" + adminPass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
