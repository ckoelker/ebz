package de.netzfactor.ebz.controlling.integration.lms.web;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.netzfactor.ebz.controlling.integration.lms.dto.LernnachweisDtos.LernleistungsFaktDto;
import de.netzfactor.ebz.controlling.integration.lms.dto.LernnachweisDtos.NachweisKursRefDto;
import de.netzfactor.ebz.controlling.integration.lms.model.LernleistungsFakt;
import de.netzfactor.ebz.controlling.integration.mandant.service.LernnachweisService;
import de.netzfactor.ebz.controlling.integration.mandant.service.OpenolatNachweisProvisioning.KursRef;

/**
 * Nachweis-Seam-API (M6): den trackbaren Nachweis-Kurs bereitstellen, einen Abschluss festhalten, die
 * Completion ins MDM synchronisieren und die {@link LernleistungsFakt}-Nachweise (Soll-Stunden) lesen (K6).
 * Schreibend/lesend nur mit Rolle {@code katalog-pflege} (RBAC, Realm ebz-staff). Eigener Tag → orval-Client.
 */
@Path("/lms/nachweise")
@Tag(name = "LMS Nachweis")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("katalog-pflege")
public class LernnachweisResource {

    @Inject
    LernnachweisService nachweis;

    /** Stellt den trackbaren Nachweis-Kurs zum WBT sicher (idempotent). */
    @POST
    @Path("/kurs/{wbtId}/sicherstellen")
    public NachweisKursRefDto kursSicherstellen(@PathParam("wbtId") Long wbtId) {
        KursRef r = nachweis.ensureNachweisKurs(wbtId);
        return new NachweisKursRefDto(r.courseId(), r.nodeId());
    }

    /** Hält den Abschluss einer Einschreibung in OpenOLAT fest (Completion-Event). */
    @POST
    @Path("/einschreibung/{id}/abschluss-melden")
    public Response abschlussMelden(@PathParam("id") Long id,
            @QueryParam("bestanden") @DefaultValue("true") boolean bestanden) {
        nachweis.meldeAbschluss(id, bestanden);
        return Response.noContent().build();
    }

    /** Liest die OpenOLAT-Completion und projiziert sie als {@link LernleistungsFakt} (idempotent). */
    @POST
    @Path("/einschreibung/{id}/synchronisieren")
    @Transactional
    public Response synchronisieren(@PathParam("id") Long id) {
        LernleistungsFakt f = nachweis.synchronisiere(id);
        if (f == null) {
            return Response.noContent().build();
        }
        return Response.ok(LernleistungsFaktDto.von(f)).build();
    }

    /** Nachweise als Report — optional je Mandant gefiltert (K6: „in MDM lesbar"). */
    @GET
    @Path("/fakten")
    @Transactional
    public List<LernleistungsFaktDto> fakten(@QueryParam("mandantId") Long mandantId) {
        List<LernleistungsFakt> fakten = mandantId != null ? nachweis.faktenJeMandant(mandantId) : nachweis.alleFakten();
        return fakten.stream().map(LernleistungsFaktDto::von).toList();
    }
}
