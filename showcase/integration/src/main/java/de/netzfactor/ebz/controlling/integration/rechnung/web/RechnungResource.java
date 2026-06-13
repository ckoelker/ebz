package de.netzfactor.ebz.controlling.integration.rechnung.web;

import java.net.URI;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.AnmeldungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.DebitorDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.KorrekturRequest;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ManuellePositionDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.RechnungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.RechnungPositionDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.RechnungslaufRequest;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RechnungService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RechnungslaufService;

/**
 * Billing-API (R1, Schema {@code rechnung}). Stammdaten (Debitor/Anmeldung) als schlanke CRUD über
 * {@code @Valid}-DTOs (Mass-Assignment-Schutz: nie id/version/nummer aus dem Body). Der Beleg-
 * Lebenszyklus (Lauf → Ausstellen/Festschreibung → Storno/Gutschrift/Nachberechnung) delegiert an die
 * Services. Schreib-Ops verlangen die Realm-Rolle {@code rechnung-pflege} (RBAC). Lese-Listen offen
 * (analog {@code bildung}).
 */
@Path("/rechnung")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RechnungResource {

    @Inject
    RechnungslaufService rechnungslauf;

    @Inject
    RechnungService rechnungService;

    // ───────────────────────── Debitoren ─────────────────────────
    @GET
    @Path("/debitoren")
    @Transactional
    public List<DebitorDto> listDebitoren() {
        return Debitor.<Debitor>listAll().stream().map(RechnungResource::toDebitor).toList();
    }

    @GET
    @Path("/debitoren/{id}")
    @Transactional
    public Response getDebitor(@PathParam("id") Long id) {
        Debitor d = Debitor.findById(id);
        return d == null ? notFound() : Response.ok(toDebitor(d)).build();
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/debitoren")
    @Transactional
    public Response createDebitor(@Valid DebitorDto dto) {
        Debitor d = new Debitor();
        applyDebitor(dto, d);
        d.persist();
        return created("/rechnung/debitoren/" + d.id, toDebitor(d));
    }

    @RolesAllowed("rechnung-pflege")
    @PUT
    @Path("/debitoren/{id}")
    @Transactional
    public Response updateDebitor(@PathParam("id") Long id, @Valid DebitorDto dto) {
        Debitor d = Debitor.findById(id);
        if (d == null) {
            return notFound();
        }
        if (stale(dto.version(), d.version)) {
            return conflict();
        }
        applyDebitor(dto, d);
        return Response.ok(toDebitor(d)).build();
    }

    // ───────────────────────── Anmeldungen ─────────────────────────
    @GET
    @Path("/anmeldungen")
    @Transactional
    public List<AnmeldungDto> listAnmeldungen() {
        return Anmeldung.<Anmeldung>listAll().stream().map(RechnungResource::toAnmeldung).toList();
    }

    @GET
    @Path("/anmeldungen/{id}")
    @Transactional
    public Response getAnmeldung(@PathParam("id") Long id) {
        Anmeldung a = Anmeldung.findById(id);
        return a == null ? notFound() : Response.ok(toAnmeldung(a)).build();
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/anmeldungen")
    @Transactional
    public Response createAnmeldung(@Valid AnmeldungDto dto) {
        Anmeldung a = new Anmeldung();
        applyAnmeldung(dto, a);
        a.persist();
        return created("/rechnung/anmeldungen/" + a.id, toAnmeldung(a));
    }

    @RolesAllowed("rechnung-pflege")
    @PUT
    @Path("/anmeldungen/{id}")
    @Transactional
    public Response updateAnmeldung(@PathParam("id") Long id, @Valid AnmeldungDto dto) {
        Anmeldung a = Anmeldung.findById(id);
        if (a == null) {
            return notFound();
        }
        if (stale(dto.version(), a.version)) {
            return conflict();
        }
        applyAnmeldung(dto, a);
        return Response.ok(toAnmeldung(a)).build();
    }

    @RolesAllowed("rechnung-pflege")
    @DELETE
    @Path("/anmeldungen/{id}")
    @Transactional
    public Response abbrechenAnmeldung(@PathParam("id") Long id) {
        Anmeldung a = Anmeldung.findById(id);
        if (a == null) {
            return notFound();
        }
        a.status = AnmeldungStatus.ABGEBROCHEN; // Soft-Delete: aus dem Lauf, aber Historie bleibt
        return Response.noContent().build();
    }

    // ───────────────────────── Rechnungslauf ─────────────────────────
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/laeufe")
    @Transactional
    @APIResponse(responseCode = "200", description = "Erzeugte/idempotent wiederverwendete Entwürfe",
            content = @Content(schema = @Schema(implementation = RechnungDto.class)))
    public List<RechnungDto> rechnungslauf(@Valid RechnungslaufRequest req) {
        return rechnungslauf.erzeugeBerufsschulEntwuerfe(req.schuljahr(), req.halbjahr())
                .stream().map(RechnungResource::toRechnung).toList();
    }

    // ───────────────────────── Rechnungen lesen ─────────────────────────
    @GET
    @Path("/rechnungen")
    @Transactional
    public List<RechnungDto> listRechnungen(@QueryParam("status") RechnungStatus status,
            @QueryParam("bereich") Bereich bereich) {
        List<Rechnung> alle;
        if (status != null && bereich != null) {
            alle = Rechnung.list("status = ?1 and bereich = ?2", status, bereich);
        } else if (status != null) {
            alle = Rechnung.list("status", status);
        } else if (bereich != null) {
            alle = Rechnung.list("bereich", bereich);
        } else {
            alle = Rechnung.listAll();
        }
        return alle.stream().map(RechnungResource::toRechnung).toList();
    }

    @GET
    @Path("/rechnungen/{id}")
    @Transactional
    public Response getRechnung(@PathParam("id") Long id) {
        Rechnung r = Rechnung.findById(id);
        return r == null ? notFound() : Response.ok(toRechnung(r)).build();
    }

    // ───────────────────────── Lebenszyklus (Service) ─────────────────────────
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/positionen")
    @Transactional
    public RechnungDto addPosition(@PathParam("id") Long id, @Valid ManuellePositionDto dto) {
        return toRechnung(rechnungService.addManuellePosition(id, dto));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/ausstellen")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public RechnungDto ausstellen(@PathParam("id") Long id) {
        return toRechnung(rechnungService.ausstellen(id));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/storno")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public RechnungDto storno(@PathParam("id") Long id) {
        return toRechnung(rechnungService.storno(id));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/gutschrift")
    @Transactional
    public RechnungDto gutschrift(@PathParam("id") Long id, @Valid KorrekturRequest req) {
        return toRechnung(rechnungService.gutschrift(id, req.grund(), req.positionen()));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/nachberechnung")
    @Transactional
    public RechnungDto nachberechnung(@PathParam("id") Long id, @Valid KorrekturRequest req) {
        return toRechnung(rechnungService.nachberechnung(id, req.grund(), req.positionen()));
    }

    // ───────────────────────── Helfer ─────────────────────────
    private static boolean stale(Long dtoVersion, long entityVersion) {
        return dtoVersion != null && dtoVersion != entityVersion; // Optimistic Locking
    }

    private static Response created(String location, Object body) {
        return Response.created(URI.create(location)).entity(body).build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private static Response conflict() {
        return Response.status(Response.Status.CONFLICT).build();
    }

    private static void applyDebitor(DebitorDto dto, Debitor d) {
        d.debitorNr = dto.debitorNr();
        d.bereich = dto.bereich();
        d.rolle = dto.rolle();
        d.name = dto.name();
        d.strasse = dto.strasse();
        d.plz = dto.plz();
        d.ort = dto.ort();
        d.land = dto.land();
        d.ustId = dto.ustId();
        d.iban = dto.iban();
        d.email = dto.email();
    }

    private static DebitorDto toDebitor(Debitor d) {
        return new DebitorDto(d.id, d.version, d.debitorNr, d.bereich, d.rolle, d.name,
                d.strasse, d.plz, d.ort, d.land, d.ustId, d.iban, d.email);
    }

    private static void applyAnmeldung(AnmeldungDto dto, Anmeldung a) {
        a.typ = dto.typ();
        a.teilnehmerName = dto.teilnehmerName();
        a.teilnehmerEmail = dto.teilnehmerEmail();
        a.bildungsangebotId = dto.bildungsangebotId();
        a.zahlungspflichtigerDebitorId = dto.zahlungspflichtigerDebitorId();
        a.status = dto.status();
        a.schuljahr = dto.schuljahr();
        a.halbjahr = dto.halbjahr();
        a.zimmerart = dto.zimmerart();
        a.unterrichtBetragCent = dto.unterrichtBetragCent();
        a.uebernachtungBetragCent = dto.uebernachtungBetragCent();
        a.semester = dto.semester();
        a.semesterbetragCent = dto.semesterbetragCent();
    }

    private static AnmeldungDto toAnmeldung(Anmeldung a) {
        return new AnmeldungDto(a.id, a.version, a.typ, a.teilnehmerName, a.teilnehmerEmail,
                a.bildungsangebotId, a.zahlungspflichtigerDebitorId, a.status,
                a.schuljahr, a.halbjahr, a.zimmerart, a.unterrichtBetragCent, a.uebernachtungBetragCent,
                a.semester, a.semesterbetragCent);
    }

    private static RechnungDto toRechnung(Rechnung r) {
        List<RechnungPositionDto> pos = r.positionen.stream().map(RechnungResource::toPosition).toList();
        return new RechnungDto(r.id, r.version, r.belegart, r.bereich, r.nummer, r.debitorId,
                r.zeitraumBezeichnung, r.ausstellungsdatum, r.zahlungszielTage, r.status,
                r.originalRechnungId, r.summeCent(), pos);
    }

    private static RechnungPositionDto toPosition(RechnungPosition p) {
        return new RechnungPositionDto(p.id, p.teilnehmerName, p.beschreibung, p.menge,
                p.einzelbetragCent, p.betragCent(), p.steuerfall, p.steuersatz, p.befreiungsgrund,
                p.leistungsart, p.herkunft);
    }
}
