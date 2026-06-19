package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import java.time.LocalDateTime;
import java.util.List;

import io.quarkus.security.Authenticated;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.IdentitaetsPort;

/**
 * Personenseitiger Zugriff auf den <b>Aktivitätslog</b> im Außenportal (Realm {@code ebz-customers}, via
 * {@code PortalTenantResolver} auf {@code /kommunikation/portal}). Autorisierung ist <b>kontext-skopiert</b>:
 * der Aufrufer wird über den Token-{@code sub} → Party-ID aufgelöst ({@link IdentitaetsPort}) und sieht/
 * ändert ausschließlich <i>eigene</i> Ereignisse (403 bei fremden) — keine interne Rolle nötig. K0:
 * Pull-Zeitstrahl, Badge, gelesen-markieren, Pflicht-Bestätigung. Threads/Nachrichten folgen ab K2.
 */
@Path("/kommunikation/portal")
@Produces(MediaType.APPLICATION_JSON)
public class KommunikationResource {

    @Inject
    KommunikationApi kommunikation;

    @Inject
    IdentitaetsPort identitaet;

    /** Lese-Sicht eines Aktivitätslog-Eintrags (ohne interne Felder). */
    public record EreignisView(Long id, String ereignisTyp, String kategorie, String betreff,
            LocalDateTime zeitpunkt, String kontextTyp, Long kontextId, boolean gelesen,
            boolean bestaetigungErforderlich, LocalDateTime bestaetigtAm) {
    }

    @Authenticated
    @GET
    @Path("/ereignisse")
    @Transactional
    public List<EreignisView> ereignisse(@Context SecurityContext ctx) {
        Long personId = mussAufrufer(ctx);
        return kommunikation.ereignisseFuer(personId).stream().map(KommunikationResource::toView).toList();
    }

    @Authenticated
    @GET
    @Path("/ungelesen")
    @Transactional
    public UngelesenView ungelesen(@Context SecurityContext ctx) {
        Long personId = mussAufrufer(ctx);
        return new UngelesenView(kommunikation.ungelesen(personId));
    }

    public record UngelesenView(long anzahl) {
    }

    @Authenticated
    @POST
    @Path("/ereignisse/{id}/gelesen")
    @Transactional
    public Response gelesen(@PathParam("id") Long id, @Context SecurityContext ctx) {
        Long personId = mussAufrufer(ctx);
        if (!eigenes(id, personId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        kommunikation.markiereGelesen(id);
        return Response.noContent().build();
    }

    @Authenticated
    @POST
    @Path("/ereignisse/{id}/bestaetigen")
    @Transactional
    public Response bestaetigen(@PathParam("id") Long id, @Context SecurityContext ctx) {
        Long personId = mussAufrufer(ctx);
        if (!eigenes(id, personId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String sub = ctx.getUserPrincipal() == null ? null : ctx.getUserPrincipal().getName();
        kommunikation.bestaetige(id, sub, null);
        return Response.noContent().build();
    }

    /** Token-{@code sub} → bekannte Party-ID; 403, wenn keine Identität dahinter steht. */
    private Long mussAufrufer(SecurityContext ctx) {
        String sub = ctx.getUserPrincipal() == null ? null : ctx.getUserPrincipal().getName();
        Long personId = identitaet.personIdFuerSub(sub);
        if (personId == null) {
            throw new jakarta.ws.rs.ForbiddenException("Zu Ihrem Login ist keine Kundenidentität bekannt.");
        }
        return personId;
    }

    /** Kontext-Schutz: das Ereignis muss dem Aufrufer gehören (sonst 403). */
    private static boolean eigenes(Long ereignisId, Long personId) {
        PersonEreignis pe = PersonEreignis.findById(ereignisId);
        return pe != null && personId.equals(pe.empfaengerPersonId);
    }

    private static EreignisView toView(PersonEreignis pe) {
        boolean gelesen = Zustellung.count(
                "personEreignis.id = ?1 and kanal = ?2 and gelesenAm is not null", pe.id, Kanal.PORTAL) > 0;
        return new EreignisView(pe.id, pe.ereignisTyp.name(), pe.ereignisTyp.kategorie.name(), pe.betreff,
                pe.zeitpunkt, pe.kontextTyp.name(), pe.kontextId, gelesen, pe.bestaetigungErforderlich,
                pe.bestaetigtAm);
    }
}
