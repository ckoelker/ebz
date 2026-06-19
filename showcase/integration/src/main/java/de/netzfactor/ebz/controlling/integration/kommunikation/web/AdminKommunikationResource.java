package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Nachricht;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe.Quelle;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.GruppenService;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KonversationService;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.AgentPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.StaffIdentitaetsPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.web.KommunikationViews.KonversationView;
import de.netzfactor.ebz.controlling.integration.kommunikation.web.KommunikationViews.NachrichtView;
import de.netzfactor.ebz.controlling.integration.kommunikation.web.KommunikationViews.SendenDto;

/**
 * Admin-Seite der zweiseitigen Threads (K2): das EBZ-Backoffice (Realm {@code ebz-staff}, Default-Tenant,
 * Rolle {@code crm-pflege}) eröffnet Vorgänge an Personen und antwortet in ihnen. Die Person nimmt über
 * {@link KommunikationResource} im Außenportal (Realm {@code ebz-customers}) teil — <b>Cross-Realm in einer
 * Konversation</b> über getrennte, tenant-skopierte Pfade. Eine Staff-Nachricht wird zusätzlich ins
 * CRM-Kontaktlog gespiegelt (über den {@code CrmSpiegelPort}, „kein Doppelsystem"). Der Mitarbeiter wird
 * über den Token-{@code sub} aufgelöst ({@link StaffIdentitaetsPort}).
 */
@Path("/kommunikation/admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Kommunikation Resource")
@RolesAllowed("crm-pflege")
public class AdminKommunikationResource {

    @Inject
    KonversationService konversationen;

    @Inject
    StaffIdentitaetsPort staff;

    @Inject
    AgentPort coPilot;

    @Inject
    GruppenService gruppen;

    /** KI-Antwortvorschlag (EU-AI-Act Art. 50: in der UI als „KI-Vorschlag" zu kennzeichnen). */
    public record EntwurfView(String entwurf, boolean kiGeneriert) {
    }

    /** Alle Admin-Vorgänge (Support-Pool), neueste zuerst — mit Ungelesen-Flag aus Sicht des Mitarbeiters. */
    @GET
    @Path("/konversationen")
    @Transactional
    public List<KonversationView> konversationen(@Context SecurityContext ctx) {
        Long mid = mussMitarbeiter(ctx);
        return konversationen.konversationenFuerStaff().stream().map(k -> toStaffView(k, mid)).toList();
    }

    /** Nachrichten eines Vorgangs (chronologisch). */
    @GET
    @Path("/konversationen/{id}/nachrichten")
    @Transactional
    public List<NachrichtView> nachrichten(@PathParam("id") Long id) {
        return KommunikationViews.toViews(konversationen.nachrichten(id), staff);
    }

    /** Neuen Vorgang an eine Person eröffnen (erste Nachricht inklusive). */
    @POST
    @Path("/vorgaenge")
    @Transactional
    public KonversationView eroeffne(SendenDto dto, @Context SecurityContext ctx) {
        Long mid = mussMitarbeiter(ctx);
        KontextTyp kt = dto.kontextTyp() == null ? KontextTyp.KEINER : KontextTyp.valueOf(dto.kontextTyp());
        Konversation k = konversationen.eroeffneVorgang(mid, dto.personId(), dto.betreff(), kt,
                dto.kontextId(), dto.inhaltHtml());
        return toStaffView(k, mid);
    }

    /** Antwort des Mitarbeiters in einem Vorgang. */
    @POST
    @Path("/konversationen/{id}/nachrichten")
    @Transactional
    public NachrichtView antworten(@PathParam("id") Long id, SendenDto dto, @Context SecurityContext ctx) {
        Long mid = mussMitarbeiter(ctx);
        Nachricht n = konversationen.antworteAlsStaff(id, mid, dto == null ? null : dto.inhaltHtml());
        return KommunikationViews.toView(n, staff);
    }

    /**
     * Co-Pilot: KI-Antwortvorschlag aus dem bisherigen Verlauf (HITL — der Mitarbeiter prüft, bearbeitet
     * und sendet selbst über {@link #antworten}). Versendet nichts.
     */
    @POST
    @Path("/konversationen/{id}/entwurf")
    @Transactional
    public EntwurfView entwurf(@PathParam("id") Long id) {
        return new EntwurfView(coPilot.entwirfAntwort(id), true);
    }

    /** Markiert einen Vorgang für den Mitarbeiter als gelesen. */
    @POST
    @Path("/konversationen/{id}/gelesen")
    @Transactional
    public Response gelesen(@PathParam("id") Long id, @Context SecurityContext ctx) {
        Long mid = mussMitarbeiter(ctx);
        konversationen.markiereGelesenStaff(id, mid);
        return Response.noContent().build();
    }

    // ───────────────────────── Verteiler & Broadcast (K3, Person→Gruppe) ─────────────────────────

    public record GruppeView(Long id, String name, String beschreibung, String quelle, int anzahl) {
    }

    /** Anlegen/Pflege: {@code quelle}=MANUELL|ORGANISATION; {@code organisationId} nur bei ORGANISATION. */
    public record GruppeDto(String name, String beschreibung, String quelle, Long organisationId) {
    }

    public record MitgliedDto(Long personId) {
    }

    public record BroadcastDto(String nachricht) {
    }

    public record BroadcastErgebnis(int erreicht) {
    }

    /** Alle Verteiler mit aufgelöster Empfängerzahl. */
    @GET
    @Path("/gruppen")
    @Transactional
    public List<GruppeView> gruppen() {
        return gruppen.gruppen().stream().map(this::toGruppeView).toList();
    }

    /** Verteiler anlegen (manuell oder als Organisations-Kreis). */
    @POST
    @Path("/gruppen")
    @Transactional
    public GruppeView gruppeAnlegen(GruppeDto dto) {
        Quelle q = dto.quelle() == null ? Quelle.MANUELL : Quelle.valueOf(dto.quelle());
        Personengruppe g = q == Quelle.ORGANISATION
                ? gruppen.anlegenOrganisation(dto.name(), dto.beschreibung(), dto.organisationId())
                : gruppen.anlegenManuell(dto.name(), dto.beschreibung());
        return toGruppeView(g);
    }

    /** Verteiler löschen (UI bestätigt vorher). */
    @DELETE
    @Path("/gruppen/{id}")
    @Transactional
    public Response gruppeLoeschen(@PathParam("id") Long id) {
        gruppen.loeschen(id);
        return Response.noContent().build();
    }

    /** Manuelles Mitglied hinzufügen. */
    @POST
    @Path("/gruppen/{id}/mitglieder")
    @Transactional
    public Response mitgliedHinzu(@PathParam("id") Long id, MitgliedDto dto) {
        gruppen.mitgliedHinzu(id, dto.personId());
        return Response.noContent().build();
    }

    /** Manuelles Mitglied entfernen. */
    @DELETE
    @Path("/gruppen/{id}/mitglieder/{personId}")
    @Transactional
    public Response mitgliedEntfernen(@PathParam("id") Long id, @PathParam("personId") Long personId) {
        gruppen.mitgliedEntfernen(id, personId);
        return Response.noContent().build();
    }

    /** Broadcast an alle (aufgelösten) Mitglieder; liefert die Anzahl erreichter Empfänger. */
    @POST
    @Path("/gruppen/{id}/broadcast")
    @Transactional
    public BroadcastErgebnis broadcast(@PathParam("id") Long id, BroadcastDto dto) {
        return new BroadcastErgebnis(gruppen.broadcast(id, dto == null ? null : dto.nachricht()));
    }

    private GruppeView toGruppeView(Personengruppe g) {
        return new GruppeView(g.id, g.name, g.beschreibung, g.quelle.name(),
                gruppen.mitglieder(g.id).size());
    }

    private KonversationView toStaffView(Konversation k, Long mitarbeiterId) {
        Nachricht letzte = konversationen.letzteNachricht(k.id);
        return new KonversationView(k.id, k.typ.name(), k.betreff, k.status.name(), k.kontextTyp.name(),
                k.kontextId, KommunikationViews.partnerFuerStaff(k, staff), KommunikationViews.vorschau(letzte),
                letzte == null ? k.erstelltAm : letzte.zeitpunkt,
                konversationen.ungelesenImThreadStaff(k.id, mitarbeiterId));
    }

    private Long mussMitarbeiter(SecurityContext ctx) {
        String sub = ctx.getUserPrincipal() == null ? null : ctx.getUserPrincipal().getName();
        Long mid = staff.mitarbeiterIdFuerSub(sub);
        if (mid == null) {
            throw new jakarta.ws.rs.ForbiddenException("Zu Ihrem Login ist kein Mitarbeiter bekannt.");
        }
        return mid;
    }
}
