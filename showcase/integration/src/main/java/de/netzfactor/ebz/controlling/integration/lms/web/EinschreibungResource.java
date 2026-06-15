package de.netzfactor.ebz.controlling.integration.lms.web;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.netzfactor.ebz.controlling.integration.lms.model.EinschreibungStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.service.KurseinschreibungService;

/**
 * Einschreibungs-Naht (LMS L2). {@code POST /lms/einschreibungen} = interner Provisionierungs-Eingang
 * (in L3 von der bezahlten Vendure-Order gerufen, hier mit Rolle {@code katalog-pflege} als Platzhalter
 * für den Service-Account): legt je Kurs eine {@link Kurseinschreibung} an (Outbox-Anforderung), der
 * Dispatcher schreibt asynchron in OpenOLAT ein. Dazu Cockpit-Sichten: Liste (inkl. Dead-Letter),
 * Storno und manueller Neuversuch (HITL).
 */
@Path("/lms/einschreibungen")
@Tag(name = "LMS Resource")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EinschreibungResource {

    @Inject
    KurseinschreibungService service;

    /** Provisionierungs-Anforderung: ein Lernender, n WBT-Kurse (MDM-Ids). Idempotent je Kurs×Sub. */
    public record Anforderung(
            @NotBlank String keycloakSub,
            String email,
            String anzeigeName,
            String vendureOrderId,
            @NotEmpty List<Long> wbtKursIds) {
    }

    /** Cockpit-/Audit-Projektion einer Einschreibung. */
    public record EinschreibungDto(Long id, Long wbtKursId, String kursTitel, String keycloakSub, String email,
            EinschreibungStatus status, Long openolatIdentityKey, int versuche, String letzterFehler) {
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Transactional
    public Response anfordern(@Valid Anforderung req) {
        List<EinschreibungDto> ergebnis = req.wbtKursIds().stream()
                .map(kursId -> service.anfordern(req.keycloakSub(), req.email(), req.anzeigeName(),
                        kursId, req.vendureOrderId()))
                .map(EinschreibungResource::toDto)
                .toList();
        return Response.accepted(ergebnis).build(); // 202: angenommen, async-Zustellung folgt
    }

    @RolesAllowed("katalog-pflege")
    @GET
    @Transactional
    public List<EinschreibungDto> list() {
        return Kurseinschreibung.<Kurseinschreibung>listAll().stream().map(EinschreibungResource::toDto).toList();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/{id}/ausschreiben")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response ausschreiben(@PathParam("id") Long id) {
        Kurseinschreibung e = service.ausschreiben(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(toDto(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/{id}/neu-versuch")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response neuVersuch(@PathParam("id") Long id) {
        Kurseinschreibung e = service.neuVersuch(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(toDto(e)).build();
    }

    private static EinschreibungDto toDto(Kurseinschreibung e) {
        return new EinschreibungDto(e.id, e.wbtKurs.id, e.wbtKurs.titel, e.keycloakSub, e.email,
                e.status, e.openolatIdentityKey, e.versuche, e.letzterFehler);
    }
}
