package de.netzfactor.ebz.controlling.integration.party.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import de.netzfactor.ebz.controlling.integration.party.service.AnmeldungWorkflowService;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;

/**
 * Anmeldungs-Lebenszyklus (Anmeldung Berufsschule). Interne Übergänge der Sachbearbeitung
 * (Rolle {@code rechnung-pflege}): EBZ-Bestätigung (E) und später die Vertragsbestätigung (F).
 */
@Path("/party/anmeldungen")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnmeldungWorkflowResource {

    @Inject
    AnmeldungWorkflowService workflow;

    public record AnmeldungStatusView(Long anmeldungId, String status, String teilnehmerName,
            Long kontextOrganisationId) {
    }

    /** EBZ bestätigt eine angefragte Anmeldung ({@code ANGEFRAGT → BESTAETIGT_EBZ}) + benachrichtigt Azubi/Firma. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/{id}/bestaetigung")
    @Transactional
    public AnmeldungStatusView bestaetigen(@PathParam("id") Long id) {
        Anmeldung a = workflow.bestaetigeDurchEbz(id);
        return new AnmeldungStatusView(a.id, a.status.name(), a.teilnehmerName, a.kontextOrganisationId);
    }
}
