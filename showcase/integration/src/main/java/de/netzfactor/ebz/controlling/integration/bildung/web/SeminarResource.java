package de.netzfactor.ebz.controlling.integration.bildung.web;

import java.net.URI;
import java.util.List;

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

import de.netzfactor.ebz.controlling.integration.bildung.dto.SeminarDto;
import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;
import de.netzfactor.ebz.controlling.integration.bildung.model.Seminar;

/**
 * Per-Typ-CRUD für {@code SEMINAR} (§11.2/§11.4) — flaches Schema, kein {@code oneOf}.
 * Handgeschriebene JAX-RS-Resource über {@code SeminarDto} statt roher rest-data-panache-Entity:
 * Mass-Assignment-Schutz + saubere flache OpenAPI je Typ (§11.9-B). Validierung erfolgt allein über
 * die Bean-Validation am {@link SeminarDto} ({@code @Valid}); Cross-Field-Regeln folgen in P1.2.
 * Löschen = Soft-Delete (Status {@code ARCHIVIERT}, §11.9-F), kein Hard-Delete.
 */
@Path("/bildung/seminare")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SeminarResource {

    @GET
    @Transactional
    public List<SeminarDto> list() {
        return Seminar.<Seminar>listAll().stream().map(SeminarResource::toDto).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@PathParam("id") Long id) {
        Seminar e = Seminar.findById(id);
        return e == null ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.ok(toDto(e)).build();
    }

    @POST
    @Transactional
    public Response create(@Valid SeminarDto dto) {
        Seminar e = new Seminar();
        apply(dto, e);
        e.persist();
        return Response.created(URI.create("/bildung/seminare/" + e.id)).entity(toDto(e)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, @Valid SeminarDto dto) {
        Seminar e = Seminar.findById(id);
        if (e == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Optimistic Locking (§11.9-C2): stale Update → 409 statt Lost-Update.
        if (dto.version() != null && dto.version() != e.version) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        apply(dto, e);
        return Response.ok(toDto(e)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response archive(@PathParam("id") Long id) {
        Seminar e = Seminar.findById(id);
        if (e == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        e.status = AngebotStatus.ARCHIVIERT;
        return Response.noContent().build();
    }

    // ── Mapping (DTO ↔ Entity; Entity nie roh exponiert) ──

    private static SeminarDto toDto(Seminar e) {
        return new SeminarDto(
                e.id, e.version, BildungsangebotTyp.SEMINAR,
                e.code, e.titel, e.bereich, e.kurzbeschreibung, e.status,
                e.gueltigAb, e.gueltigBis, e.verantwortlich, e.preisModell,
                e.shopVerkauf, e.vendureProductId, e.zielgruppe,
                e.kategorie, e.dauerUE, e.abschluss, e.zertifikat, e.minTN, e.maxTN);
    }

    /** Übernimmt nur fachliche Felder (nie id/version/typ) — Mass-Assignment-Schutz (§11.9-B). */
    private static void apply(SeminarDto dto, Seminar e) {
        e.code = dto.code();
        e.titel = dto.titel();
        e.bereich = dto.bereich();
        e.kurzbeschreibung = dto.kurzbeschreibung();
        e.status = dto.status();
        e.gueltigAb = dto.gueltigAb();
        e.gueltigBis = dto.gueltigBis();
        e.verantwortlich = dto.verantwortlich();
        e.preisModell = dto.preisModell();
        e.shopVerkauf = dto.shopVerkauf();
        e.vendureProductId = dto.vendureProductId();
        e.zielgruppe = dto.zielgruppe();
        e.kategorie = dto.kategorie();
        e.dauerUE = dto.dauerUE();
        e.abschluss = dto.abschluss();
        e.zertifikat = dto.zertifikat();
        e.minTN = dto.minTN();
        e.maxTN = dto.maxTN();
    }
}
