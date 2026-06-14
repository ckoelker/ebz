package de.netzfactor.ebz.controlling.integration.party.web;

import java.net.URI;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;

/**
 * Party-Kern-API (Schema {@code party}): Identität (Person + E-Mails), N:M-Verknüpfung zu
 * Organisationen (Mitgliedschaft) und — der Kern — die <b>Bestellkontexte einer Identität</b> plus die
 * kontextabhängige Abrechnungs-Projektion auf den Debitor. Schreib-Ops verlangen {@code rechnung-pflege}
 * (Stammdaten-Hoheit); Lesen offen. DTOs als schlanke nested Records (Mass-Assignment-Schutz: nie
 * id/version/status aus dem Body).
 */
@Path("/party")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PartyResource {

    @Inject
    PartyHoheitService party;

    // ───────────────────────── DTOs (gebündelt) ─────────────────────────

    public record SelbstRegistrierung(@NotBlank String keycloakSub, @NotBlank @Email String email,
            @NotBlank String anzeigeName) {
    }

    public record OrganisationDto(@NotBlank String name, String strasse, String plz, String ort,
            String land, String ustId) {
    }

    public record TeilnehmerAnlage(@NotBlank @Email String email, @NotBlank String anzeigeName,
            @NotNull Mitgliedschaft.Rolle rolle, boolean buchungsberechtigt) {
    }

    public record MergeRequest(@NotNull Long quellId, @NotNull Long zielId) {
    }

    public record PersonView(Long id, String keycloakSub, String anzeigeName, String plz, String ort,
            String status, Long goldenPersonId, List<String> emails, List<MitgliedschaftView> mitgliedschaften) {
    }

    public record MitgliedschaftView(Long organisationId, String organisation, Mitgliedschaft.Rolle rolle,
            boolean buchungsberechtigt) {
    }

    public record KontextView(String art, Long organisationId, String bezeichnung, List<Mitgliedschaft.Rolle> rollen) {
    }

    public record DebitorView(Long id, String debitorNr, String bereich, String rolle, String name) {
    }

    // ───────────────────────── Identität ─────────────────────────

    /** Selbstregistrierung/Login: legt eine neue aktive Person an (201) oder claimt eine vor-angelegte (200). */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/personen/selbstregistrieren")
    @Transactional
    public Response selbstRegistrieren(@Valid SelbstRegistrierung req) {
        long vorher = Person.count();
        Person p = party.selbstRegistrieren(req.keycloakSub(), req.email(), req.anzeigeName());
        boolean neu = Person.count() > vorher;
        return Response.status(neu ? Response.Status.CREATED : Response.Status.OK)
                .entity(toView(p)).build();
    }

    @GET
    @Path("/personen/{id}")
    @Transactional
    public Response getPerson(@PathParam("id") Long id) {
        Person p = Person.findById(id);
        return p == null ? notFound() : Response.ok(toView(p)).build();
    }

    /** Merge-Kandidaten (gleicher Namensschlüssel) zur Dublettenauflösung. */
    @GET
    @Path("/personen/{id}/kandidaten")
    @Transactional
    public List<PersonView> kandidaten(@PathParam("id") Long id) {
        return party.kandidaten(id).stream().map(PartyResource::toView).toList();
    }

    /** Führt eine Dublette ({@code quellId}) in die Ziel-Person zusammen. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/personen/merge")
    @Transactional
    public PersonView merge(@Valid MergeRequest req) {
        return toView(party.merge(req.quellId(), req.zielId()));
    }

    // ───────────────────────── Organisation & Mitgliedschaft ─────────────────────────

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/organisationen")
    @Transactional
    public Response createOrganisation(@Valid OrganisationDto dto) {
        Organisation o = new Organisation();
        o.name = dto.name();
        o.strasse = dto.strasse();
        o.plz = dto.plz();
        o.ort = dto.ort();
        o.land = dto.land();
        o.ustId = dto.ustId();
        o.persist();
        return Response.created(URI.create("/party/organisationen/" + o.id))
                .entity(o).build();
    }

    /** Firmenseitige Vor-Anlage eines Teilnehmers/Ansprechpartners per E-Mail (provisorisch + Mitgliedschaft). */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/organisationen/{id}/teilnehmer")
    @Transactional
    public Response teilnehmerAnlegen(@PathParam("id") Long organisationId, @Valid TeilnehmerAnlage req) {
        Person p = party.registriereTeilnehmer(organisationId, req.email(), req.anzeigeName(),
                req.rolle(), req.buchungsberechtigt());
        return Response.status(Response.Status.CREATED).entity(toView(p)).build();
    }

    // ───────────────────────── Bestellkontexte & Abrechnungs-Projektion ─────────────────────────

    /** Wählbare Bestellkontexte der Identität: PRIVAT + jede buchungsberechtigte Organisation. */
    @GET
    @Path("/personen/{id}/kontexte")
    @Transactional
    public List<KontextView> kontexte(@PathParam("id") Long id) {
        return party.kontexte(id).stream()
                .map(k -> new KontextView(k.art().name(), k.organisationId(), k.bezeichnung(), k.rollen()))
                .toList();
    }

    /**
     * Projiziert den Abrechnungs-Debitor für einen gewählten Kontext und Bereich: ohne
     * {@code organisationId} privat (eigener Debitor), sonst im Auftrag der Organisation (Org-Debitor).
     */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/personen/{id}/debitor")
    @Transactional
    public DebitorView debitor(@PathParam("id") Long id, @QueryParam("organisationId") Long organisationId,
            @QueryParam("bereich") Bereich bereich) {
        Debitor d = party.ermittleDebitor(id, organisationId, bereich);
        return new DebitorView(d.id, d.debitorNr, d.bereich.name(), d.rolle.name(), d.name);
    }

    // ───────────────────────── Helfer ─────────────────────────

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private static PersonView toView(Person p) {
        List<String> emails = PersonEmail.<PersonEmail>list("personId", p.id).stream()
                .map(e -> e.email).toList();
        List<MitgliedschaftView> ms = Mitgliedschaft.<Mitgliedschaft>list("personId", p.id).stream()
                .map(m -> {
                    Organisation o = Organisation.findById(m.organisationId);
                    return new MitgliedschaftView(m.organisationId, o == null ? null : o.name,
                            m.rolle, m.buchungsberechtigt);
                }).toList();
        return new PersonView(p.id, p.keycloakSub, p.anzeigeName, p.plz, p.ort, p.status.name(),
                p.goldenPersonId, emails, ms);
    }
}
