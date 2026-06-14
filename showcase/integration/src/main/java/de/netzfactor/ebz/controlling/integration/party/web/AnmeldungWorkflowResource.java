package de.netzfactor.ebz.controlling.integration.party.web;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import de.netzfactor.ebz.controlling.integration.party.service.AnmeldungWorkflowService;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;

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

    public record OffeneAnmeldungView(Long anmeldungId, String teilnehmerName, Long kontextOrganisationId,
            Long zahlungspflichtigerDebitorId, String schuljahr, Integer halbjahr, String status) {
    }

    /**
     * Offene Anmeldungen für das HITL-Cockpit (Schritt I). Ohne {@code status} die noch nicht
     * abgerechneten Vorstufen ({@code ANGEFRAGT} + {@code BESTAETIGT_EBZ}), sonst gezielt ein Status.
     */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Transactional
    public List<OffeneAnmeldungView> offene(@QueryParam("status") AnmeldungStatus status) {
        List<Anmeldung> anmeldungen = status == null
                ? Anmeldung.find("status in ?1 order by id desc",
                        List.of(AnmeldungStatus.ANGEFRAGT, AnmeldungStatus.BESTAETIGT_EBZ)).page(0, 200).list()
                : Anmeldung.find("status = ?1 order by id desc", status).page(0, 200).list();
        return anmeldungen.stream()
                .map(a -> new OffeneAnmeldungView(a.id, a.teilnehmerName, a.kontextOrganisationId(),
                        a.zahlungspflichtigerDebitorId(), a.schuljahr, a.halbjahr, a.status.name()))
                .toList();
    }

    /** EBZ bestätigt eine angefragte Anmeldung ({@code ANGEFRAGT → BESTAETIGT_EBZ}) + benachrichtigt Azubi/Firma. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/{id}/bestaetigung")
    @Transactional
    public AnmeldungStatusView bestaetigen(@PathParam("id") Long id) {
        Anmeldung a = workflow.bestaetigeDurchEbz(id);
        return new AnmeldungStatusView(a.id, a.status.name(), a.teilnehmerName, a.kontextOrganisationId());
    }
}
