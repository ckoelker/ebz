package de.netzfactor.ebz.controlling.integration.party.web;

import io.quarkus.security.Authenticated;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.AnmeldungWorkflowService;
import de.netzfactor.ebz.controlling.integration.party.service.BuchungService;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;

/**
 * Self-Service-Portal des Ausbildungsbetriebs (Realm {@code ebz-customers}, Schritt D): der
 * eingeloggte Ansprechpartner meldet Azubis an. Autorisierung ist <b>kontext-skopiert</b> — der
 * Aufrufer wird über den Token-{@code sub} aufgelöst und muss <i>buchungsberechtigtes Mitglied</i> der
 * Organisation sein (sonst 403); keine interne {@code rechnung-pflege}-Rolle nötig. Die entstehende
 * Anmeldung ist {@code ANGEFRAGT} (noch nicht abrechenbar).
 */
@Path("/party/portal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PortalResource {

    @Inject
    BuchungService buchung;

    @Inject
    PartyHoheitService party;

    @Inject
    AnmeldungWorkflowService workflow;

    public record AzubiAnmeldungDto(@NotNull Long organisationId, @NotBlank @Email String azubiEmail,
            @NotBlank String azubiName, @NotBlank String schuljahr, int halbjahr,
            @NotNull Zimmerart zimmerart, int unterrichtBetragCent, Integer uebernachtungBetragCent) {
    }

    public record AzubiAnmeldungView(Long anmeldungId, Long teilnehmerPersonId, Long kontextOrganisationId,
            Long zahlungspflichtigerDebitorId, String status, String teilnehmerName) {
    }

    /**
     * Meldet einen Azubi im Kontext der Organisation an. Der Aufrufer (Token-{@code sub}) muss
     * buchungsberechtigtes Mitglied sein (sonst 403); er ist zugleich der Besteller, über den der
     * Firmen-Debitor projiziert wird.
     */
    @Authenticated
    @POST
    @Path("/azubi-anmeldung")
    @Transactional
    public Response azubiAnmelden(@Valid AzubiAnmeldungDto dto, @Context SecurityContext ctx) {
        Person aufrufer = party.findeNachSub(ctx.getUserPrincipal().getName());
        if (aufrufer == null || !party.istBuchungsberechtigt(aufrufer.id, dto.organisationId())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Anmeldung a = buchung.meldeAzubiAn(new BuchungService.AzubiAnmeldung(
                aufrufer.id, dto.organisationId(), dto.azubiEmail(), dto.azubiName(),
                dto.schuljahr(), dto.halbjahr(), dto.zimmerart(),
                dto.unterrichtBetragCent(), dto.uebernachtungBetragCent()));
        AzubiAnmeldungView view = new AzubiAnmeldungView(a.id, a.teilnehmerPersonId(), a.kontextOrganisationId(),
                a.zahlungspflichtigerDebitorId(), a.status.name(), a.teilnehmerName);
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * Die Firma bestätigt abschließend den Ausbildungsvertrag → Anmeldung {@code AKTIV} (jetzt
     * abrechenbar). Kontext-skopiert: der Aufrufer (Token-{@code sub}) muss buchungsberechtigtes
     * Mitglied der Organisation der Anmeldung sein (sonst 403). Audit über den bestätigenden Aufrufer.
     */
    @Authenticated
    @POST
    @Path("/anmeldungen/{id}/vertrag-bestaetigen")
    @Transactional
    public Response vertragBestaetigen(@PathParam("id") Long id, @Context SecurityContext ctx) {
        Person aufrufer = party.findeNachSub(ctx.getUserPrincipal().getName());
        Anmeldung a = Anmeldung.findById(id);
        if (a == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Long orgId = a.kontextOrganisationId();
        if (aufrufer == null || orgId == null || !party.istBuchungsberechtigt(aufrufer.id, orgId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Anmeldung b = workflow.bestaetigeVertrag(id, aufrufer.id);
        AzubiAnmeldungView view = new AzubiAnmeldungView(b.id, b.teilnehmerPersonId(), b.kontextOrganisationId(),
                b.zahlungspflichtigerDebitorId(), b.status.name(), b.teilnehmerName);
        return Response.ok(view).build();
    }
}
