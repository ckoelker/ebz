package de.netzfactor.ebz.controlling.integration.betrieb;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduler;

/**
 * <b>Betriebs-Steuerung der Hintergrund-Scheduler</b>: hält die {@code @Scheduled}-Jobs (Kommunikations-
 * Zustellung/Digest, Outbox, LMS, Shop-Sync) zur Laufzeit an bzw. setzt sie fort. Zweck:
 * <ul>
 *   <li><b>Test-Isolation:</b> vor einem Testlauf gegen die geteilte DB pausieren, damit der Container-
 *       Dispatcher die frisch eingereihten {@code ZustellAuftrag}-Zeilen der Test-JVM nicht per SKIP-LOCKED
 *       wegschnappt (sonst läuft die echte SMTP-Zustellung statt des Test-MockMailbox). Ersetzt das
 *       frühere {@code docker stop} — der Stack bleibt oben (siehe {@code DispatcherPauseExtension}).</li>
 *   <li><b>Ops:</b> Wartungsfenster (DB-Migration o. ä.) ohne laufende Hintergrundverarbeitung.</li>
 * </ul>
 * Bewusst <b>nicht</b> über OIDC, sondern über ein Config-Secret ({@code betrieb.steuer.token}) im Header
 * {@code X-Betrieb-Token} abgesichert — so kommt der Test-Hook ohne Keycloak-Token aus. Aus der OpenAPI
 * ausgeblendet (kein Client-Codegen).
 */
@Path("/betrieb/scheduler")
@Produces(MediaType.APPLICATION_JSON)
public class SchedulerSteuerResource {

    private static final Logger LOG = Logger.getLogger(SchedulerSteuerResource.class);

    @ConfigProperty(name = "betrieb.steuer.token", defaultValue = "showcase-betrieb-dev")
    String token;

    private final Scheduler scheduler;

    public SchedulerSteuerResource(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public record StatusView(boolean laufend) {
    }

    @Operation(hidden = true)
    @POST
    @Path("/pause")
    public Response pause(@HeaderParam("X-Betrieb-Token") String header) {
        if (!erlaubt(header)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        scheduler.pause();
        LOG.info("Hintergrund-Scheduler pausiert (Betriebs-Steuerung).");
        return Response.ok(new StatusView(scheduler.isRunning())).build();
    }

    @Operation(hidden = true)
    @POST
    @Path("/resume")
    public Response resume(@HeaderParam("X-Betrieb-Token") String header) {
        if (!erlaubt(header)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        scheduler.resume();
        LOG.info("Hintergrund-Scheduler fortgesetzt (Betriebs-Steuerung).");
        return Response.ok(new StatusView(scheduler.isRunning())).build();
    }

    @Operation(hidden = true)
    @GET
    @Path("/status")
    public StatusView status() {
        return new StatusView(scheduler.isRunning());
    }

    private boolean erlaubt(String header) {
        return token != null && !token.isBlank() && token.equals(header);
    }
}
