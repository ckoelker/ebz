package de.netzfactor.ebz.controlling.integration.bildung.web;

import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import de.netzfactor.ebz.controlling.integration.bildung.dto.RegistryItemDto;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;

/**
 * Read-only-Registry über die STI-Tabelle (§11.2/§11.4): typ-übergreifende Header-Liste für die
 * Verwaltungsmaske. Bewusst eine SEPARATE JAX-RS-Resource (kein Resource-Interface), da
 * rest-data-panache Custom-Methoden non-reactive ignoriert (§11.9-B2).
 */
@Path("/bildung/angebote")
@Produces(MediaType.APPLICATION_JSON)
public class BildungsangebotRegistryResource {

    @GET
    @Transactional
    public List<RegistryItemDto> registry() {
        return Bildungsangebot.<Bildungsangebot>listAll().stream().map(RegistryItemDto::from).toList();
    }
}
