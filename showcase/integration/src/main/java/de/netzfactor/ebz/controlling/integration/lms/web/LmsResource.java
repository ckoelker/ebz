package de.netzfactor.ebz.controlling.integration.lms.web;

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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.netzfactor.ebz.controlling.integration.bildung.dto.ProjektionErgebnis;
import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;
import de.netzfactor.ebz.controlling.integration.lms.dto.WbtKursDto;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;
import de.netzfactor.ebz.controlling.integration.lms.vendure.WbtVendureProjektion;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Eine Resource für den WBT-Katalog (LMS-Anbindung L1, Pflege-Seite). CRUD über {@link WbtKursDto}
 * (nie die rohe Entity → Mass-Assignment-Schutz), Löschen = Soft-Delete (Status {@code ARCHIVIERT}),
 * Optimistic Locking über {@code version}. {@code /veroeffentlichen} = MDM→Vendure-Projektion (§7).
 * Schreibend nur mit Rolle {@code katalog-pflege} (RBAC, Realm ebz-staff). Eigener Tag {@code LMS
 * Resource} → orval generiert den Client (Filter in {@code mdm/orval.config.ts}).
 */
@Path("/lms/kurse")
@Tag(name = "LMS Katalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LmsResource {

    @Inject
    WbtVendureProjektion vendure;

    @Inject
    Prozessspur prozess;

    @GET
    @Transactional
    public List<WbtKursDto> list() {
        return WbtKurs.<WbtKurs>listAll().stream().map(LmsResource::toDto).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@PathParam("id") Long id) {
        WbtKurs e = WbtKurs.findById(id);
        return e == null ? notFound() : Response.ok(toDto(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @POST
    @Transactional
    public Response create(@Valid WbtKursDto dto) {
        WbtKurs e = new WbtKurs();
        apply(dto, e);
        e.persist();
        // USER_TASK in der Katalog-Phase: das EBZ pflegt den WBT-Katalog im Cockpit.
        prozess.schritt("WBT-Kurs anlegen", Akteur.EBZ, Prozess.System.COCKPIT, Typ.USER_TASK, Phase.WBT_KATALOG);
        return Response.created(URI.create("/lms/kurse/" + e.id)).entity(toDto(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, @Valid WbtKursDto dto) {
        WbtKurs e = WbtKurs.findById(id);
        if (e == null) {
            return notFound();
        }
        if (dto.version() != null && dto.version() != e.version) {
            return Response.status(Response.Status.CONFLICT).build(); // Optimistic Locking
        }
        apply(dto, e);
        return Response.ok(toDto(e)).build();
    }

    @RolesAllowed("katalog-pflege")
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response archive(@PathParam("id") Long id) {
        WbtKurs e = WbtKurs.findById(id);
        if (e == null) {
            return notFound();
        }
        e.status = AngebotStatus.ARCHIVIERT; // Soft-Delete
        return Response.noContent().build();
    }

    /**
     * Projiziert den (verkäuflichen, aktiven, importierten) Kurs nach Vendure und schreibt die IDs
     * zurück (§7). Idempotent: gesetztes {@code vendureProductId} → Update statt Neuanlegen.
     */
    @RolesAllowed("katalog-pflege")
    @POST
    @Path("/{id}/veroeffentlichen")
    @Consumes(MediaType.WILDCARD) // kein Request-Body
    @APIResponse(responseCode = "200", description = "Projiziert; Vendure-IDs zurückgeschrieben",
            content = @Content(schema = @Schema(implementation = ProjektionErgebnis.class)))
    @Transactional
    public Response veroeffentlichen(@PathParam("id") Long id) {
        WbtKurs e = WbtKurs.findById(id);
        if (e == null) {
            return notFound();
        }
        if (!e.shopVerkauf) {
            return conflict("Kurs ist nicht für den Shop-Verkauf markiert (shopVerkauf=false).");
        }
        if (e.status != AngebotStatus.AKTIV) {
            return conflict("Nur Kurse im Status AKTIV werden in den Shop projiziert.");
        }
        if (e.openolatKey == null) {
            return conflict("Kurs hat keinen openolatKey (nicht in OpenOLAT importiert) — nicht auslieferbar.");
        }
        try {
            WbtVendureProjektion.Ergebnis r = vendure.projiziere(e); // dirty → Flush am Tx-Commit
            e.vendureProductId = r.productId();
            e.vendureVariantId = r.variantId();
            // SERVICE_TASK in der Katalog-Phase: der Kurs wird als Produkt im Shop (Vendure) gelistet.
            prozess.schritt("WBT im Shop veröffentlichen", Akteur.EBZ, Prozess.System.VENDURE,
                    Typ.SERVICE_TASK, Phase.WBT_KATALOG);
            return Response.ok(new ProjektionErgebnis(e.id, e.code, e.vendureProductId, e.vendureVariantId)).build();
        } catch (VendureException ex) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(new Fehler(ex.getMessage())).build();
        }
    }

    /** Schlanke Fehler-Payload für die Cockpit-Anzeige (Conflict/Bad-Gateway). */
    public record Fehler(String message) {
    }

    // ── Helfer ──
    /** Übernimmt DTO→Entity (nie id/version — Mass-Assignment-Schutz). */
    private static void apply(WbtKursDto dto, WbtKurs e) {
        e.code = dto.code();
        e.titel = dto.titel();
        e.kurzbeschreibung = dto.kurzbeschreibung();
        e.openolatKey = dto.openolatKey();
        e.scormVersion = dto.scormVersion();
        e.preisCent = dto.preisCent();
        e.shopVerkauf = dto.shopVerkauf();
        e.status = dto.status();
        e.vendureProductId = dto.vendureProductId();
    }

    private static WbtKursDto toDto(WbtKurs e) {
        return new WbtKursDto(e.id, e.version, e.code, e.titel, e.kurzbeschreibung, e.openolatKey,
                e.scormVersion, e.preisCent, e.shopVerkauf, e.status, e.vendureProductId);
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private static Response conflict(String msg) {
        return Response.status(Response.Status.CONFLICT).entity(new Fehler(msg)).build();
    }
}
