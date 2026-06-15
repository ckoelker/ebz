package de.netzfactor.ebz.controlling.integration.outbox.web;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Status;
import de.netzfactor.ebz.controlling.integration.outbox.service.OutboxService;

/**
 * Betriebssicht auf die Provisionierungs-Outbox (EBZ-Back-Office). Macht den asynchronen Versand
 * beobachtbar und steuerbar: offene/erledigte/<b>fehlgeschlagene</b> Aufträge listen, einen Dead-Letter
 * manuell neu anstoßen (HITL) und — für die Showcase-Demo — den Dispatcher sofort triggern.
 */
@Path("/outbox")
@Produces(MediaType.APPLICATION_JSON)
public class OutboxResource {

    @Inject
    OutboxService outbox;

    public record AuftragView(Long id, Long anmeldungId, String teilnehmerName, String zielsystem,
            String ereignis, String status, int versuche, Instant naechsterVersuchAm, String letzterFehler,
            Instant erstelltAm, Instant erledigtAm) {
    }

    /** Listet Aufträge, optional gefiltert per {@code ?status=OFFEN|ERLEDIGT|FEHLGESCHLAGEN}. */
    @GET
    @Transactional
    public List<AuftragView> liste(@QueryParam("status") Status status) {
        List<OutboxAuftrag> auftraege = (status == null)
                ? OutboxAuftrag.listAll()
                : OutboxAuftrag.list("status", status);
        return auftraege.stream().map(OutboxResource::view).toList();
    }

    /** Manueller Neuversuch eines (typisch fehlgeschlagenen) Auftrags → wieder fällig. */
    @POST
    @Path("/{id}/neu-versuch")
    @RolesAllowed("rechnung-pflege")
    public Response neuVersuch(@PathParam("id") Long id) {
        OutboxAuftrag a = outbox.neuVersuch(id);
        if (a == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(viewTx(id)).build();
    }

    /** Showcase-Trigger: verarbeitet sofort alle fälligen Aufträge (sonst übernimmt das der Scheduler). */
    @POST
    @Path("/dispatch")
    @RolesAllowed("rechnung-pflege")
    public Response dispatch() {
        int n = outbox.verarbeiteFaellige(50);
        return Response.ok(java.util.Map.of("verarbeitet", n)).build();
    }

    @Transactional
    AuftragView viewTx(Long id) {
        return view(OutboxAuftrag.findById(id));
    }

    private static AuftragView view(OutboxAuftrag a) {
        return new AuftragView(a.id, a.anmeldungId(),
                a.anmeldung == null ? null : a.anmeldung.teilnehmerName,
                a.zielsystem.name(), a.ereignis.name(), a.status.name(), a.versuche,
                a.naechsterVersuchAm, a.letzterFehler, a.erstelltAm, a.erledigtAm);
    }
}
