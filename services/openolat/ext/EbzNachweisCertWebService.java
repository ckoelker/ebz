package org.olat.ebz.restapi;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.olat.core.CoreSpringFactory;
import org.olat.core.id.Identity;
import org.olat.core.logging.Tracing;
import org.olat.course.CourseFactory;
import org.olat.course.ICourse;
import org.olat.course.certificate.CertificatesManager;
import org.olat.course.certificate.RepositoryEntryCertificateConfiguration;
import org.olat.course.nodes.CourseNode;
import org.olat.course.nodes.STCourseNode;
import org.olat.course.nodes.ScormCourseNode;
import org.olat.course.nodes.scorm.ScormEditController;
import org.olat.course.tree.CourseEditorTreeModel;
import org.olat.course.tree.CourseEditorTreeNode;
import org.olat.modules.ModuleConfiguration;
import org.olat.repository.RepositoryEntry;
import org.olat.repository.RepositoryEntryStatusEnum;
import org.olat.repository.RepositoryManager;
import org.olat.restapi.repository.course.AbstractCourseNodeWebService;
import org.olat.restapi.security.RestSecurityHelper;

/**
 * EBZ-Extension (offizieller Erweiterungsweg, KEIN Core-Fork): füllt die zwei Lücken, die die
 * OpenOLAT-Standard-REST-API NICHT kann, sodass die „Nugget-Abschluss → Zertifikat"-Strecke
 * <b>rein per API</b> (vom Integration-Backend, je Mandant/Kurs) eingerichtet werden kann:
 * <ol>
 *   <li>einen <b>SCORM-Kursknoten</b> anlegen, der ein bestehendes SCORM/CP-Repo-Entry (das
 *       „Nugget") referenziert und <b>assessment-relevant</b> ist (passed), und</li>
 *   <li>am Kurs die <b>automatische Zertifizierung bei Bestehen</b> aktivieren.</li>
 * </ol>
 * Danach stellt OpenOLAT <b>nativ</b> ein Zertifikat aus, sobald der Lernende das Nugget besteht —
 * kein Seed, kein manuelles Generieren, alles im OpenOLAT.
 *
 * <p><b>Registrierung:</b> Die Klasse liegt unter {@code org.olat.*} → der CXF-JAX-RS-Server
 * ({@code <jaxrs:server basePackages="org.olat">}) entdeckt die {@code @Path}-Resource automatisch,
 * exakt wie jeden OpenOLAT-Webservice (kein {@code _spring}, keine Registrierungs-API nötig).
 * Subklasst {@link AbstractCourseNodeWebService} und nutzt dessen geprüfte {@code attach(...)}-
 * Maschinerie (Edit-Session öffnen, Knoten einfügen, speichern + publizieren).
 */
@Path("ebz/nachweis")
public class EbzNachweisCertWebService extends AbstractCourseNodeWebService {

    /**
     * Hängt an einen bestehenden Kurs ({@code courseId}, vom Backend per Standard-REST erzeugt) einen
     * SCORM-Knoten für das Nugget ({@code nuggetKey}) an und schaltet die Auto-Zertifizierung ein.
     */
    @PUT
    @Path("courses/{courseId}/scorm-zertifikat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response konfiguriere(@PathParam("courseId") Long courseId,
            @QueryParam("nuggetKey") Long nuggetKey,
            @QueryParam("shortTitle") @DefaultValue("Nugget") String shortTitle,
            @QueryParam("longTitle") @DefaultValue("Lern-Nugget") String longTitle,
            @Context HttpServletRequest request) {

        RepositoryEntry nugget = CoreSpringFactory.getImpl(RepositoryManager.class)
                .lookupRepositoryEntry(nuggetKey, false);
        if (nugget == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"fehler\":\"nugget-Repo-Entry nicht gefunden: " + nuggetKey + "\"}").build();
        }

        // (1) SCORM-Knoten anhängen (assessment-relevant, passed). attach() öffnet die Edit-Session,
        //     fügt den Knoten unter der Wurzel ein und speichert+publiziert via saveAndCloseCourse().
        Response attach = attach(courseId, null, ScormCourseNode.TYPE, null,
                shortTitle, longTitle, null, null, null, null, null, null,
                new ScormNuggetConfig(nugget), request);
        if (attach.getStatus() >= 300) {
            return attach;
        }
        String nodeId = null;
        if (attach.getEntity() instanceof org.olat.restapi.support.vo.CourseNodeVO) {
            nodeId = ((org.olat.restapi.support.vo.CourseNodeVO) attach.getEntity()).getId();
        }

        // (1b) Wurzel-Bestehens-Regel: Kurs gilt als bestanden, sobald er zu 100 % ABGESCHLOSSEN ist
        //      (Lernpfad-Fortschritt). OHNE diese Regel bleibt die Kurs-Wurzel passed=false → der
        //      Auto-Zertifikat-Trigger (feuert nur bei Kurs-passed) löst nie aus. Bewusst
        //      COMPLETION-basiert (passed.progress) statt passed.all: das Nugget meldet via SCORM
        //      zuverlässig „completed", aber nicht zwingend „passed/success" → Abschluss = Nachweis.
        //      Eigene Edit-Session: Root-Config setzen, als publizierbar markieren, speichern+schließen.
        ICourse editCourse = CourseFactory.openCourseEditSession(courseId);
        CourseEditorTreeModel cetm = editCourse.getEditorTreeModel();
        CourseNode root = ((CourseEditorTreeNode) cetm.getRootNode()).getCourseNode();
        root.getModuleConfiguration().setBooleanEntry(STCourseNode.CONFIG_PASSED_PROGRESS, true);
        cetm.nodeConfigChanged(root);
        CourseFactory.saveCourseEditorTreeModel(courseId);
        CourseFactory.closeCourseEditSession(courseId, true);

        // (2) Auto-Zertifizierung am Kurs aktivieren → OpenOLAT stellt bei Bestehen selbst aus.
        ICourse course = CourseFactory.loadCourse(courseId);
        RepositoryEntry courseEntry = course.getCourseEnvironment().getCourseGroupManager().getCourseEntry();
        CertificatesManager cm = CoreSpringFactory.getImpl(CertificatesManager.class);
        RepositoryEntryCertificateConfiguration cfg = cm.getConfiguration(courseEntry);
        if (cfg == null) {
            cfg = cm.createConfiguration(courseEntry);
        }
        cfg.setAutomaticCertificationEnabled(true);
        cfg.setManualCertificationEnabled(true);
        cm.updateConfiguration(cfg);

        // (3) Publizieren: attach() speichert nur den Editor-Baum → publishCourse zieht die Änderungen
        //     (SCORM-Knoten) in die Run-Struktur (sonst nicht lauffähig) und setzt den Status published.
        Identity doer = RestSecurityHelper.getIdentity(request);
        CourseFactory.publishCourse(course, RepositoryEntryStatusEnum.published, doer, Locale.GERMAN);

        Tracing.createLoggerFor(EbzNachweisCertWebService.class).info(
                "EBZ-Nachweis: SCORM-Knoten (Nugget " + nuggetKey + ") + Auto-Zertifikat an Kurs " + courseId);
        return Response.ok("{\"status\":\"ok\",\"courseId\":" + courseId
                + ",\"nuggetKey\":" + nuggetKey + ",\"nodeId\":\"" + nodeId
                + "\",\"autoCertification\":true}").build();
    }

    /** Setzt am neu erzeugten Knoten die Nugget-Referenz und macht ihn assessment-relevant (passed). */
    private static final class ScormNuggetConfig implements AbstractCourseNodeWebService.CustomConfigDelegate {
        private final RepositoryEntry nugget;

        ScormNuggetConfig(RepositoryEntry nugget) {
            this.nugget = nugget;
        }

        @Override
        public boolean isValid() {
            return nugget != null;
        }

        @Override
        public void configure(ICourse course, CourseNode newNode, ModuleConfiguration mc, Identity doer) {
            ScormEditController.setScormCPReference(nugget, mc);
            mc.setBooleanEntry(ScormEditController.CONFIG_ISASSESSABLE, true);
            mc.setStringValue(ScormEditController.CONFIG_ASSESSABLE_TYPE,
                    ScormEditController.CONFIG_ASSESSABLE_TYPE_PASSED);
        }
    }
}
