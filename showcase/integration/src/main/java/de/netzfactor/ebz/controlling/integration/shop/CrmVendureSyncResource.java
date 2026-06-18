package de.netzfactor.ebz.controlling.integration.shop;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;

/**
 * CRM→Vendure-Personen-Sync (P7b): projiziert aktive EBZ-Mitarbeiter als Vendure-Ansprechpartner
 * (idempotent über {@code crmPersonId}). {@code POST /shop/crm-personen-sync}, Rolle
 * {@code katalog-pflege}.
 */
@Path("/shop")
@Produces(MediaType.APPLICATION_JSON)
public class CrmVendureSyncResource {

    @Inject
    CrmVendureSyncService service;

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/crm-personen-sync")
    @Consumes(MediaType.WILDCARD)
    public Response sync() {
        try {
            return Response.ok(service.syncAnsprechpartner()).build();
        } catch (VendureException e) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(new ShopInitResource.Fehler(e.getMessage())).build();
        }
    }
}
