package de.netzfactor.ebz.controlling.integration.shop;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Teilnehmer-Vorschläge für den Shop-Checkout: {@code GET /shop/teilnehmer-vorschlaege?email=&sub=}.
 * Liefert die Personen der Käufer-Organisation(en) (siehe {@link TeilnehmerVorschlagService}). Wird vom
 * Storefront-Server mit dem Service-Account ({@code katalog-pflege}) aufgerufen; die Identität der
 * angemeldeten Besteller:in wird als Parameter übergeben.
 */
@Path("/shop/teilnehmer-vorschlaege")
@Produces(MediaType.APPLICATION_JSON)
public class ShopTeilnehmerResource {

    @Inject
    TeilnehmerVorschlagService service;

    @RolesAllowed("katalog-pflege")
    @GET
    public List<TeilnehmerVorschlagService.Vorschlag> vorschlaege(@QueryParam("email") String email,
            @QueryParam("sub") String sub) {
        return service.fuerBesteller(email, sub);
    }
}
