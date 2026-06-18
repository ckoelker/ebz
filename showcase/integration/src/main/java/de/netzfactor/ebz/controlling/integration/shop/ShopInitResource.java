package de.netzfactor.ebz.controlling.integration.shop;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;

/**
 * Reproduzierbare, idempotente Initialisierung des Shop-Backends (Produktkatalog P1, §B).
 * Der „Aufbau" liegt als editierbarer Java-Code ({@link KatalogBeispiele} + {@link ShopInitService})
 * und treibt Vendure über die Admin-API (SmallRye Dynamic Client, §A0).
 * <p>
 * {@code POST /shop/init} ist mehrfach sicher aufrufbar (nur Upsert, kein Reset). Rolle
 * {@code katalog-pflege} (wie die übrigen Schreib-Endpunkte). Liefert eine Zusammenfassung
 * (angelegt/aktualisiert/übersprungen + Log).
 */
@Path("/shop")
@Produces(MediaType.APPLICATION_JSON)
public class ShopInitResource {

    @Inject
    ShopInitService service;

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/init")
    @Consumes(MediaType.WILDCARD) // kein Request-Body
    @APIResponse(responseCode = "200", description = "Shop initialisiert (idempotent)",
            content = @Content(schema = @Schema(implementation = ShopInitService.Ergebnis.class)))
    public Response init() {
        try {
            ShopInitService.Ergebnis r = service.initialisiere();
            return Response.ok(r).build();
        } catch (VendureException e) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(new Fehler(e.getMessage())).build();
        }
    }

    /** Schlanke Fehler-Payload (Vendure nicht erreichbar / GraphQL-Fehler). */
    public record Fehler(String message) {
    }
}
