package de.netzfactor.ebz.controlling.integration.mandant.web;

import java.net.URI;
import java.time.Instant;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.IdpFoederationDto;
import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.LizenzvertragDto;
import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.MandantDto;
import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.MandantProjektionDto;
import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.SeatAufnahmeDto;
import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.SeatMeldungDto;
import de.netzfactor.ebz.controlling.integration.mandant.dto.MandantDtos.SeatStatusDto;
import de.netzfactor.ebz.controlling.integration.mandant.model.IdpFoederation;
import de.netzfactor.ebz.controlling.integration.mandant.model.Lizenzvertrag;
import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion;
import de.netzfactor.ebz.controlling.integration.mandant.model.SeatMeldung;
import de.netzfactor.ebz.controlling.integration.mandant.service.MandantProjektionService;
import de.netzfactor.ebz.controlling.integration.mandant.service.SeatLimitService;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Pflege der Mandanten-Landschaft (PoC, M1): CRUD über {@link Mandant} samt seiner B2B-Bausteine
 * {@link IdpFoederation} (Domain→IdP, M3) und {@link Lizenzvertrag} (Seat-Cap, M5). Immer über DTOs
 * (nie die rohe Entity → Mass-Assignment-Schutz), Optimistic Locking über {@code version},
 * {@code DELETE} = Soft-Delete (Status {@code BEENDET}). Schreibend nur mit Rolle {@code mandant-pflege}
 * (RBAC, Realm ebz-staff). Eigener Tag {@code Mandanten} → orval generiert den Client
 * (Filter in {@code mdm/orval.config.ts}).
 * <p>
 * {@code openolatOrganisationKey} ist server-verwaltet (Org-Projektion M2) und wird nie aus dem DTO
 * übernommen. Jeder Schreib-Schritt emittiert eine {@link Prozessspur} (Phase
 * {@link Phase#MANDANT_ONBOARDING}) → Mandanten-Onboarding erscheint in Jaeger und im BPMN.
 */
@Path("/mandant")
@Tag(name = "Mandanten")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MandantResource {

    @Inject
    Prozessspur prozess;

    @Inject
    MandantProjektionService projektion;

    @Inject
    SeatLimitService seats;

    // ── Mandant-CRUD ──

    @GET
    @Transactional
    public List<MandantDto> list() {
        return Mandant.<Mandant>listAll().stream().map(MandantResource::toDto).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@PathParam("id") Long id) {
        Mandant e = Mandant.findById(id);
        return e == null ? notFound() : Response.ok(toDto(e)).build();
    }

    @RolesAllowed("mandant-pflege")
    @POST
    @Transactional
    public Response create(@Valid MandantDto dto) {
        if (Mandant.find("schluessel", dto.schluessel()).firstResult() != null) {
            return conflict("Mandant mit Schlüssel '" + dto.schluessel() + "' existiert bereits.");
        }
        Mandant e = new Mandant();
        e.schluessel = dto.schluessel();
        e.erstelltAm = Instant.now();
        Response orgFehler = apply(dto, e);
        if (orgFehler != null) {
            return orgFehler;
        }
        e.persist();
        prozess.schritt("Mandant anlegen", Akteur.EBZ, Prozess.System.COCKPIT, Typ.USER_TASK, Phase.MANDANT_ONBOARDING);
        return Response.created(URI.create("/mandant/" + e.id)).entity(toDto(e)).build();
    }

    @RolesAllowed("mandant-pflege")
    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, @Valid MandantDto dto) {
        Mandant e = Mandant.findById(id);
        if (e == null) {
            return notFound();
        }
        if (dto.version() != null && dto.version() != e.version) {
            return Response.status(Response.Status.CONFLICT).build(); // Optimistic Locking
        }
        Response orgFehler = apply(dto, e);
        if (orgFehler != null) {
            return orgFehler;
        }
        prozess.schritt("Mandant pflegen", Akteur.EBZ, Prozess.System.COCKPIT, Typ.USER_TASK, Phase.MANDANT_ONBOARDING);
        return Response.ok(toDto(e)).build();
    }

    @RolesAllowed("mandant-pflege")
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response beenden(@PathParam("id") Long id) {
        Mandant e = Mandant.findById(id);
        if (e == null) {
            return notFound();
        }
        e.status = Mandant.Status.BEENDET; // Soft-Delete (kein Hard-Delete; Einschreibungen/Föderationen bleiben referenziert)
        prozess.schritt("Mandant beenden", Akteur.EBZ, Prozess.System.COCKPIT, Typ.USER_TASK, Phase.MANDANT_ONBOARDING);
        return Response.noContent().build();
    }

    // ── IdP-Föderationen eines Mandanten ──

    @GET
    @Path("/{id}/foederationen")
    @Transactional
    public Response foederationen(@PathParam("id") Long id) {
        Mandant m = Mandant.findById(id);
        if (m == null) {
            return notFound();
        }
        List<IdpFoederationDto> dtos = IdpFoederation.<IdpFoederation>list("mandant", m).stream()
                .map(MandantResource::toDto).toList();
        return Response.ok(dtos).build();
    }

    @RolesAllowed("mandant-pflege")
    @POST
    @Path("/{id}/foederationen")
    @Transactional
    public Response addFoederation(@PathParam("id") Long id, @Valid IdpFoederationDto dto) {
        Mandant m = Mandant.findById(id);
        if (m == null) {
            return notFound();
        }
        IdpFoederation f = new IdpFoederation();
        f.mandant = m;
        f.idpAlias = dto.idpAlias();
        f.emailDomains = dto.emailDomains();
        f.protokoll = dto.protokoll();
        f.status = dto.status() != null ? dto.status() : IdpFoederation.Status.ENTWURF;
        f.persist();
        prozess.schritt("IdP-Föderation anlegen", Akteur.EBZ, Prozess.System.KEYCLOAK,
                Typ.USER_TASK, Phase.MANDANT_IDP_FOEDERATION);
        return Response.created(URI.create("/mandant/" + id + "/foederationen/" + f.id)).entity(toDto(f)).build();
    }

    @RolesAllowed("mandant-pflege")
    @DELETE
    @Path("/foederationen/{fid}")
    @Transactional
    public Response loescheFoederation(@PathParam("fid") Long fid) {
        IdpFoederation f = IdpFoederation.findById(fid);
        if (f == null) {
            return notFound();
        }
        f.delete();
        return Response.noContent().build();
    }

    // ── Seat-Lizenzen eines Mandanten ──

    @GET
    @Path("/{id}/lizenzen")
    @Transactional
    public Response lizenzen(@PathParam("id") Long id) {
        Mandant m = Mandant.findById(id);
        if (m == null) {
            return notFound();
        }
        List<LizenzvertragDto> dtos = Lizenzvertrag.<Lizenzvertrag>list("mandant", m).stream()
                .map(MandantResource::toDto).toList();
        return Response.ok(dtos).build();
    }

    @RolesAllowed("mandant-pflege")
    @POST
    @Path("/{id}/lizenzen")
    @Transactional
    public Response addLizenz(@PathParam("id") Long id, @Valid LizenzvertragDto dto) {
        Mandant m = Mandant.findById(id);
        if (m == null) {
            return notFound();
        }
        if (!m.istSeatBegrenzt()) {
            return conflict("Nur ENTERPRISE_FLAT-Mandanten haben ein Seat-Limit (EBZ-Kontexte sind unbegrenzt).");
        }
        Lizenzvertrag l = new Lizenzvertrag();
        l.mandant = m;
        l.seatLimit = dto.seatLimit();
        l.gueltigVon = dto.gueltigVon();
        l.gueltigBis = dto.gueltigBis();
        l.aktiv = dto.aktiv();
        l.persist();
        prozess.schritt("Seat-Lizenz hinterlegen", Akteur.EBZ, Prozess.System.COCKPIT,
                Typ.USER_TASK, Phase.MANDANT_SEAT_VERWALTUNG);
        return Response.created(URI.create("/mandant/" + id + "/lizenzen/" + l.id)).entity(toDto(l)).build();
    }

    // ── Org-Projektion nach OpenOLAT (M2) ──

    /**
     * Fordert die OpenOLAT-Org-Projektion des Mandanten an (idempotent, Outbox). Der Dispatcher legt die
     * Organisation asynchron an und schreibt {@code openolatOrganisationKey} zurück. {@code 202 Accepted}.
     */
    @RolesAllowed("mandant-pflege")
    @POST
    @Path("/{id}/projizieren")
    @Consumes(MediaType.WILDCARD) // kein Request-Body
    @Transactional
    public Response projizieren(@PathParam("id") Long id) {
        if (Mandant.findById(id) == null) {
            return notFound();
        }
        MandantProjektion p = projektion.anfordern(id);
        return Response.accepted(toDto(p)).build();
    }

    /** Lese-Sicht: jüngster Projektions-Auftrag des Mandanten (Status/Fehler/erzeugter Org-Key). */
    @GET
    @Path("/{id}/projektion")
    @Transactional
    public Response projektion(@PathParam("id") Long id) {
        Mandant m = Mandant.findById(id);
        if (m == null) {
            return notFound();
        }
        MandantProjektion p = MandantProjektion.<MandantProjektion>find("mandant = ?1 order by id desc", m).firstResult();
        return p == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(toDto(p)).build();
    }

    // ── Seat-Cap (M5) ──

    /** Seat-Belegungs-Sicht: Limit, aktive Belegung (OpenOLAT-Org-Mitglieder), Überbuchung, offene Meldungen. */
    @GET
    @Path("/{id}/seats")
    @Transactional
    public Response seatStatus(@PathParam("id") Long id) {
        Mandant m = Mandant.findById(id);
        if (m == null) {
            return notFound();
        }
        Lizenzvertrag lz = seats.aktiveLizenz(m);
        boolean begrenzt = m.istSeatBegrenzt() && lz != null;
        Integer limit = begrenzt ? lz.seatLimit : null;
        int belegung = seats.belegung(m);
        boolean ueberbucht = begrenzt && belegung > limit;
        return Response.ok(new SeatStatusDto(id, begrenzt, limit, belegung, ueberbucht,
                seats.offeneMeldungenAnzahl(m))).build();
    }

    /**
     * Prüft die Aufnahme eines weiteren Org-Mitglieds (E1-Provisionierungs-Seam). Überbuchung wird
     * durchgelassen und erzeugt eine HITL-Meldung. {@code 200} mit der Entscheidung.
     */
    @RolesAllowed("mandant-pflege")
    @POST
    @Path("/{id}/seat-aufnahme")
    @Consumes(MediaType.WILDCARD)
    public Response seatAufnahme(@PathParam("id") Long id) {
        if (Mandant.findById(id) == null) {
            return notFound();
        }
        SeatLimitService.SeatAufnahme a = seats.aufnahmePruefen(id);
        return Response.ok(new SeatAufnahmeDto(a.entscheidung().name(), a.belegungVorher(),
                a.seatLimit(), a.meldungId())).build();
    }

    /** Offene HITL-Überbuchungs-Meldungen (alle Mandanten). */
    @GET
    @Path("/seat-meldungen")
    @Transactional
    public List<SeatMeldungDto> offeneSeatMeldungen() {
        return seats.offeneMeldungen().stream().map(MandantResource::toDto).toList();
    }

    /** Bestätigt eine Überbuchungs-Meldung (HITL). Bearbeiter = eingeloggter Staff-Principal. */
    @RolesAllowed("mandant-pflege")
    @POST
    @Path("/seat-meldungen/{mid}/bestaetigen")
    @Consumes(MediaType.WILDCARD)
    public Response bestaetigeSeatMeldung(@PathParam("mid") Long mid,
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext sec) {
        String bearbeiter = sec.getUserPrincipal() != null ? sec.getUserPrincipal().getName() : "unbekannt";
        SeatMeldung meld = seats.bestaetige(mid, bearbeiter);
        return meld == null ? notFound() : Response.ok(toDto(meld)).build();
    }

    // ── Mapping ──

    /** DTO→Mandant (nie id/version/openolatOrganisationKey). Liefert eine Fehler-Response oder {@code null} bei Erfolg. */
    private static Response apply(MandantDto dto, Mandant e) {
        e.anzeigeName = dto.anzeigeName();
        e.vertragstyp = dto.vertragstyp();
        e.status = dto.status() != null ? dto.status() : Mandant.Status.ENTWURF;
        e.logoUrl = dto.logoUrl();
        e.primaerFarbe = dto.primaerFarbe();
        e.sekundaerFarbe = dto.sekundaerFarbe();
        if (dto.organisationId() != null) {
            Organisation org = Organisation.findById(dto.organisationId());
            if (org == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new Fehler("Unbekannte Organisation " + dto.organisationId())).build();
            }
            e.organisation = org;
        } else {
            e.organisation = null;
        }
        return null;
    }

    private static MandantDto toDto(Mandant e) {
        return new MandantDto(e.id, e.version, e.schluessel, e.anzeigeName, e.vertragstyp, e.status,
                e.organisation == null ? null : e.organisation.id, e.openolatOrganisationKey,
                e.logoUrl, e.primaerFarbe, e.sekundaerFarbe);
    }

    private static IdpFoederationDto toDto(IdpFoederation f) {
        return new IdpFoederationDto(f.id, f.version, f.idpAlias, f.emailDomains, f.protokoll, f.status);
    }

    private static LizenzvertragDto toDto(Lizenzvertrag l) {
        return new LizenzvertragDto(l.id, l.version, l.seatLimit, l.gueltigVon, l.gueltigBis, l.aktiv);
    }

    private static MandantProjektionDto toDto(MandantProjektion p) {
        return new MandantProjektionDto(p.id, p.mandant.id, p.operation, p.status, p.versuche,
                p.letzterFehler, p.mandant.openolatOrganisationKey);
    }

    private static SeatMeldungDto toDto(SeatMeldung s) {
        return new SeatMeldungDto(s.id, s.mandant.id, s.belegungBeiMeldung, s.seatLimit, s.status, s.bestaetigtVon);
    }

    /** Schlanke Fehler-Payload für die Cockpit-Anzeige. */
    public record Fehler(String message) {
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private static Response conflict(String msg) {
        return Response.status(Response.Status.CONFLICT).entity(new Fehler(msg)).build();
    }
}
