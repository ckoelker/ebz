package de.netzfactor.ebz.controlling.integration.bildung.web;

import java.net.URI;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
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

import jakarta.inject.Inject;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import de.netzfactor.ebz.controlling.integration.bildung.dto.BerufsschuljahrDto;
import de.netzfactor.ebz.controlling.integration.bildung.dto.GemeinsamesAngebot;
import de.netzfactor.ebz.controlling.integration.bildung.dto.ProjektionErgebnis;
import de.netzfactor.ebz.controlling.integration.bildung.dto.RegistryItemDto;
import de.netzfactor.ebz.controlling.integration.bildung.dto.SeminarDto;
import de.netzfactor.ebz.controlling.integration.bildung.dto.StudiengangDto;
import de.netzfactor.ebz.controlling.integration.bildung.dto.TagungDto;
import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureProjektion;

/**
 * Eine Resource für die gesamte Bildungsangebot-Familie (schlank: eine Klasse statt fünf + Mapper).
 * Pro Subtyp ein eigener FLACHER per-Typ-Pfad (§11.2: kein {@code oneOf}) über {@code SeminarDto} …
 * statt roher Entity (Mass-Assignment-Schutz, §11.9-B); dazu die read-only Registry-Liste. Löschen =
 * Soft-Delete (Status {@code ARCHIVIERT}, §11.9-F). Validierung allein über die {@code @Valid}-DTOs;
 * Cross-Field-Regeln folgen in P1.2.
 */
@Path("/bildung")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BildungResource {

    @Inject
    VendureProjektion vendure;

    // ── Registry (typ-übergreifend) ──
    @GET
    @Path("/angebote")
    @Transactional
    public List<RegistryItemDto> registry() {
        return Bildungsangebot.<Bildungsangebot>listAll().stream().map(BildungResource::toRegistry).toList();
    }

    /**
     * Projiziert das (verkäufliche, aktive) Angebot nach Vendure und schreibt die Produkt-ID zurück
     * (Naht §11.6). Typ-übergreifend (gemeinsames Feld {@code shopVerkauf}). Idempotent: ein bereits
     * gesetztes {@code vendureProductId} führt zum Update statt zum Neuanlegen.
     */
    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/angebote/{id}/shop-projektion")
    @Consumes(MediaType.WILDCARD) // kein Request-Body → klassenweites @Consumes(JSON) hier nicht erzwingen
    @APIResponse(responseCode = "200", description = "Projiziert; vendureProductId zurückgeschrieben",
            content = @Content(schema = @Schema(implementation = ProjektionErgebnis.class)))
    @Transactional
    public Response projiziereInShop(@PathParam("id") Long id) {
        Bildungsangebot e = Bildungsangebot.findById(id);
        if (e == null) {
            return notFound();
        }
        if (!e.shopVerkauf) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new Fehler("Angebot ist nicht für den Shop-Verkauf markiert (shopVerkauf=false).")).build();
        }
        if (e.status != AngebotStatus.AKTIV) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new Fehler("Nur Angebote im Status AKTIV werden in den Shop projiziert.")).build();
        }
        try {
            e.vendureProductId = vendure.projiziere(e); // dirty → Flush am Tx-Commit (Zurückschreiben)
            return Response.ok(new ProjektionErgebnis(e.id, e.code, e.vendureProductId)).build();
        } catch (VendureException ex) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(new Fehler(ex.getMessage())).build();
        }
    }

    /** Schlanke Fehler-Payload für die Cockpit-Anzeige (Conflict/Bad-Gateway-Fälle der Projektion). */
    public record Fehler(String message) {
    }

    // ── SEMINAR ──
    @GET
    @Path("/seminare")
    @Transactional
    public List<SeminarDto> listSeminare() {
        return byTyp(BildungsangebotTyp.SEMINAR).stream().map(BildungResource::toSeminar).toList();
    }

    @GET
    @Path("/seminare/{id}")
    @Transactional
    public Response getSeminar(@PathParam("id") Long id) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.SEMINAR);
        return e == null ? notFound() : Response.ok(toSeminar(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/seminare")
    @Transactional
    public Response createSeminar(@Valid SeminarDto dto) {
        Bildungsangebot e = new Bildungsangebot();
        applySeminar(dto, e);
        e.persist();
        return created("/bildung/seminare/" + e.id, toSeminar(e));
    }

    @RolesAllowed("katalog-pflege")
    @PUT
    @Path("/seminare/{id}")
    @Transactional
    public Response updateSeminar(@PathParam("id") Long id, @Valid SeminarDto dto) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.SEMINAR);
        if (e == null) {
            return notFound();
        }
        if (stale(dto.version(), e)) {
            return conflict();
        }
        applySeminar(dto, e);
        return Response.ok(toSeminar(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @DELETE
    @Path("/seminare/{id}")
    @Transactional
    public Response archiveSeminar(@PathParam("id") Long id) {
        return archive(id, BildungsangebotTyp.SEMINAR);
    }

    // ── TAGUNG ──
    @GET
    @Path("/tagungen")
    @Transactional
    public List<TagungDto> listTagungen() {
        return byTyp(BildungsangebotTyp.TAGUNG).stream().map(BildungResource::toTagung).toList();
    }

    @GET
    @Path("/tagungen/{id}")
    @Transactional
    public Response getTagung(@PathParam("id") Long id) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.TAGUNG);
        return e == null ? notFound() : Response.ok(toTagung(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/tagungen")
    @Transactional
    public Response createTagung(@Valid TagungDto dto) {
        Bildungsangebot e = new Bildungsangebot();
        applyTagung(dto, e);
        e.persist();
        return created("/bildung/tagungen/" + e.id, toTagung(e));
    }

    @RolesAllowed("katalog-pflege")
    @PUT
    @Path("/tagungen/{id}")
    @Transactional
    public Response updateTagung(@PathParam("id") Long id, @Valid TagungDto dto) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.TAGUNG);
        if (e == null) {
            return notFound();
        }
        if (stale(dto.version(), e)) {
            return conflict();
        }
        applyTagung(dto, e);
        return Response.ok(toTagung(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @DELETE
    @Path("/tagungen/{id}")
    @Transactional
    public Response archiveTagung(@PathParam("id") Long id) {
        return archive(id, BildungsangebotTyp.TAGUNG);
    }

    // ── BERUFSSCHULJAHR ──
    @GET
    @Path("/berufsschuljahre")
    @Transactional
    public List<BerufsschuljahrDto> listBerufsschuljahre() {
        return byTyp(BildungsangebotTyp.BERUFSSCHULJAHR).stream().map(BildungResource::toBerufsschuljahr).toList();
    }

    @GET
    @Path("/berufsschuljahre/{id}")
    @Transactional
    public Response getBerufsschuljahr(@PathParam("id") Long id) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.BERUFSSCHULJAHR);
        return e == null ? notFound() : Response.ok(toBerufsschuljahr(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/berufsschuljahre")
    @Transactional
    public Response createBerufsschuljahr(@Valid BerufsschuljahrDto dto) {
        Bildungsangebot e = new Bildungsangebot();
        applyBerufsschuljahr(dto, e);
        e.persist();
        return created("/bildung/berufsschuljahre/" + e.id, toBerufsschuljahr(e));
    }

    @RolesAllowed("katalog-pflege")
    @PUT
    @Path("/berufsschuljahre/{id}")
    @Transactional
    public Response updateBerufsschuljahr(@PathParam("id") Long id, @Valid BerufsschuljahrDto dto) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.BERUFSSCHULJAHR);
        if (e == null) {
            return notFound();
        }
        if (stale(dto.version(), e)) {
            return conflict();
        }
        applyBerufsschuljahr(dto, e);
        return Response.ok(toBerufsschuljahr(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @DELETE
    @Path("/berufsschuljahre/{id}")
    @Transactional
    public Response archiveBerufsschuljahr(@PathParam("id") Long id) {
        return archive(id, BildungsangebotTyp.BERUFSSCHULJAHR);
    }

    // ── STUDIENGANG ──
    @GET
    @Path("/studiengaenge")
    @Transactional
    public List<StudiengangDto> listStudiengaenge() {
        return byTyp(BildungsangebotTyp.STUDIENGANG).stream().map(BildungResource::toStudiengang).toList();
    }

    @GET
    @Path("/studiengaenge/{id}")
    @Transactional
    public Response getStudiengang(@PathParam("id") Long id) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.STUDIENGANG);
        return e == null ? notFound() : Response.ok(toStudiengang(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/studiengaenge")
    @Transactional
    public Response createStudiengang(@Valid StudiengangDto dto) {
        Bildungsangebot e = new Bildungsangebot();
        applyStudiengang(dto, e);
        e.persist();
        return created("/bildung/studiengaenge/" + e.id, toStudiengang(e));
    }

    @RolesAllowed("katalog-pflege")
    @PUT
    @Path("/studiengaenge/{id}")
    @Transactional
    public Response updateStudiengang(@PathParam("id") Long id, @Valid StudiengangDto dto) {
        Bildungsangebot e = typed(id, BildungsangebotTyp.STUDIENGANG);
        if (e == null) {
            return notFound();
        }
        if (stale(dto.version(), e)) {
            return conflict();
        }
        applyStudiengang(dto, e);
        return Response.ok(toStudiengang(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @DELETE
    @Path("/studiengaenge/{id}")
    @Transactional
    public Response archiveStudiengang(@PathParam("id") Long id) {
        return archive(id, BildungsangebotTyp.STUDIENGANG);
    }

    // ───────────────────────── gemeinsame Helfer ─────────────────────────

    private static List<Bildungsangebot> byTyp(BildungsangebotTyp typ) {
        return Bildungsangebot.list("typ", typ);
    }

    /** Findet nach id UND Typ → kein Querlesen eines fremden Subtyps unter falschem Pfad. */
    private static Bildungsangebot typed(Long id, BildungsangebotTyp typ) {
        return Bildungsangebot.find("id = ?1 and typ = ?2", id, typ).firstResult();
    }

    private static Response archive(Long id, BildungsangebotTyp typ) {
        Bildungsangebot e = typed(id, typ);
        if (e == null) {
            return notFound();
        }
        e.status = AngebotStatus.ARCHIVIERT;
        return Response.noContent().build();
    }

    private static boolean stale(Long dtoVersion, Bildungsangebot e) {
        return dtoVersion != null && dtoVersion != e.version; // Optimistic Locking (§11.9-C2)
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

    /** Übernimmt die gemeinsamen Felder DTO→Entity (nie id/version/typ — Mass-Assignment-Schutz, §11.9-B). */
    private static void applyGemeinsam(GemeinsamesAngebot dto, Bildungsangebot e) {
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
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static boolean nz(Boolean v) {
        return v != null && v;
    }

    // ── SEMINAR mapping ──
    private static void applySeminar(SeminarDto dto, Bildungsangebot e) {
        e.typ = BildungsangebotTyp.SEMINAR;
        applyGemeinsam(dto, e);
        e.kategorie = dto.kategorie();
        e.dauerUE = dto.dauerUE();
        e.abschluss = dto.abschluss();
        e.zertifikat = dto.zertifikat();
        e.minTN = dto.minTN();
        e.maxTN = dto.maxTN();
    }

    private static SeminarDto toSeminar(Bildungsangebot e) {
        return new SeminarDto(e.id, e.version, BildungsangebotTyp.SEMINAR,
                e.code, e.titel, e.bereich, e.kurzbeschreibung, e.status, e.gueltigAb, e.gueltigBis,
                e.verantwortlich, e.preisModell, e.shopVerkauf, e.vendureProductId, e.zielgruppe,
                e.kategorie, nz(e.dauerUE), e.abschluss, nz(e.zertifikat), nz(e.minTN), nz(e.maxTN));
    }

    // ── TAGUNG mapping ──
    private static void applyTagung(TagungDto dto, Bildungsangebot e) {
        e.typ = BildungsangebotTyp.TAGUNG;
        applyGemeinsam(dto, e);
        e.thema = dto.thema();
        e.terminVon = dto.terminVon();
        e.terminBis = dto.terminBis();
        e.ort = dto.ort();
        e.programmUrl = dto.programmUrl();
        e.maxTN = dto.maxTN();
    }

    private static TagungDto toTagung(Bildungsangebot e) {
        return new TagungDto(e.id, e.version, BildungsangebotTyp.TAGUNG,
                e.code, e.titel, e.bereich, e.kurzbeschreibung, e.status, e.gueltigAb, e.gueltigBis,
                e.verantwortlich, e.preisModell, e.shopVerkauf, e.vendureProductId, e.zielgruppe,
                e.thema, e.terminVon, e.terminBis, e.ort, e.programmUrl, nz(e.maxTN));
    }

    // ── BERUFSSCHULJAHR mapping ──
    private static void applyBerufsschuljahr(BerufsschuljahrDto dto, Bildungsangebot e) {
        e.typ = BildungsangebotTyp.BERUFSSCHULJAHR;
        applyGemeinsam(dto, e);
        e.fachrichtung = dto.fachrichtung();
        e.schuljahr = dto.schuljahr();
        e.jahrgang = dto.jahrgang();
        e.beginn = dto.beginn();
        e.schildNrwSchluessel = dto.schildNrwSchluessel();
        e.plaetze = dto.plaetze();
    }

    private static BerufsschuljahrDto toBerufsschuljahr(Bildungsangebot e) {
        return new BerufsschuljahrDto(e.id, e.version, BildungsangebotTyp.BERUFSSCHULJAHR,
                e.code, e.titel, e.bereich, e.kurzbeschreibung, e.status, e.gueltigAb, e.gueltigBis,
                e.verantwortlich, e.preisModell, e.shopVerkauf, e.vendureProductId, e.zielgruppe,
                e.fachrichtung, e.schuljahr, nz(e.jahrgang), e.beginn, e.schildNrwSchluessel, nz(e.plaetze));
    }

    // ── STUDIENGANG mapping ──
    private static void applyStudiengang(StudiengangDto dto, Bildungsangebot e) {
        e.typ = BildungsangebotTyp.STUDIENGANG;
        applyGemeinsam(dto, e);
        e.studienAbschluss = dto.abschluss();
        e.studienform = dto.studienform();
        e.startsemester = dto.startsemester();
        e.regelstudienzeitSemester = dto.regelstudienzeitSemester();
        e.akkreditierungBis = dto.akkreditierungBis();
        e.ratenAnzahl = dto.ratenAnzahl();
        e.plaetze = dto.plaetze();
    }

    private static StudiengangDto toStudiengang(Bildungsangebot e) {
        return new StudiengangDto(e.id, e.version, BildungsangebotTyp.STUDIENGANG,
                e.code, e.titel, e.bereich, e.kurzbeschreibung, e.status, e.gueltigAb, e.gueltigBis,
                e.verantwortlich, e.preisModell, e.shopVerkauf, e.vendureProductId, e.zielgruppe,
                e.studienAbschluss, e.studienform, e.startsemester, nz(e.regelstudienzeitSemester),
                e.akkreditierungBis, nz(e.ratenAnzahl), nz(e.plaetze));
    }

    private static RegistryItemDto toRegistry(Bildungsangebot e) {
        return new RegistryItemDto(e.id, e.typ, e.code, e.titel, e.bereich, e.status, e.shopVerkauf,
                e.vendureProductId);
    }
}
