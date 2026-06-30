package de.netzfactor.ebz.controlling.integration.rechnung.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/** Bildet fachliche {@link RegelVerletzung} auf den getragenen HTTP-Status (404/409) mit JSON-Fehler ab. */
@Provider
public class RegelVerletzungMapper implements ExceptionMapper<RegelVerletzung> {

    @Override
    public Response toResponse(RegelVerletzung ex) {
        return Response.status(ex.status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Fehler(ex.getMessage()))
                .build();
    }

    public record Fehler(String message) {
    }
}
