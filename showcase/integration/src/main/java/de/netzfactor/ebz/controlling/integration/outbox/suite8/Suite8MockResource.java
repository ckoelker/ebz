package de.netzfactor.ebz.controlling.integration.outbox.suite8;

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
 * Steuer- und Einsichts-Endpunkt für das {@link Suite8Mock} (Showcase-Drittsystem Kiosk/Kantine).
 * Erlaubt der Demo, eine Suite8-Downtime zu simulieren und die ausgegebenen Bezahlkarten einzusehen —
 * analog zur WebUntis-Mock-Steuerung, damit das Outbox-Resilienz-Verhalten vorführbar bleibt.
 */
@Path("/mock/suite8")
@Produces(MediaType.APPLICATION_JSON)
public class Suite8MockResource {

    @Inject
    Suite8Mock suite8;

    /** Angelegte Konten inkl. Bezahlkarten-Nr (was Suite8 „sieht"). */
    @GET
    @Path("/konten")
    public List<Suite8Mock.Konto> konten() {
        return suite8.alle();
    }

    /** Showcase-Hebel: Suite8 (nicht) verfügbar schalten — {@code POST /mock/suite8/verfuegbarkeit?verfuegbar=false}. */
    @POST
    @Path("/verfuegbarkeit")
    public Response verfuegbarkeit(@QueryParam("verfuegbar") boolean verfuegbar) {
        suite8.setVerfuegbar(verfuegbar);
        return Response.ok(java.util.Map.of("verfuegbar", verfuegbar)).build();
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(java.util.Map.of(
                "verfuegbar", suite8.istVerfuegbar(),
                "anzahlKonten", suite8.alle().size())).build();
    }
}
