package de.netzfactor.ebz.controlling.integration.outbox.webuntis;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Steuer- und Einsichts-Endpunkt für das {@link WebUntisMock} (Showcase-Drittsystem). Erlaubt der Demo,
 * eine WebUntis-Downtime zu simulieren und zu prüfen, welche Azubis tatsächlich übernommen wurden —
 * so wird das Outbox-Resilienz-Verhalten (Retry/Dead-Letter/manueller Neuversuch) vorführbar.
 */
@Path("/mock/webuntis")
@Produces(MediaType.APPLICATION_JSON)
public class WebUntisMockResource {

    @Inject
    WebUntisMock webuntis;

    /** Importierte Schüler (was WebUntis „sieht"). */
    @GET
    @Path("/schueler")
    public List<WebUntisMock.Schueler> schueler() {
        return webuntis.alle();
    }

    /** Showcase-Hebel: WebUntis (nicht) verfügbar schalten — {@code POST /mock/webuntis/verfuegbarkeit?verfuegbar=false}. */
    @POST
    @Path("/verfuegbarkeit")
    public Response verfuegbarkeit(@QueryParam("verfuegbar") boolean verfuegbar) {
        webuntis.setVerfuegbar(verfuegbar);
        return Response.ok(java.util.Map.of("verfuegbar", verfuegbar)).build();
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(java.util.Map.of(
                "verfuegbar", webuntis.istVerfuegbar(),
                "anzahlSchueler", webuntis.alle().size())).build();
    }
}
