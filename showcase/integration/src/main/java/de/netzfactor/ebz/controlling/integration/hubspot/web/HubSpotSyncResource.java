package de.netzfactor.ebz.controlling.integration.hubspot.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.service.HubSpotSyncService;

/**
 * Staff-Cockpit-API des HubSpot-Sync ({@code ebz-staff}, Rolle {@code katalog-pflege}): Auftragsliste +
 * manuelles Triggern (einzelne Partei, Backfill, Lauf, Retry) und das Recht-auf-Vergessen-Anstoßen.
 * Routine-Sync läuft automatisch über den Dispatcher; diese Endpunkte sind Sicht + menschliche Einzelfälle.
 */
@Path("/hubspot/sync")
@RolesAllowed("katalog-pflege")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HubSpotSyncResource {

    @Inject
    HubSpotSyncService sync;

    /** Eine Auftragszeile fürs Cockpit (DTO, keine Entity an der Grenze). */
    public record AuftragDto(Long id, String objektTyp, String operation, String status, String partei,
                             int versuche, String letzterFehler, Instant erstelltAm, Instant erledigtAm) {
    }

    @GET
    @Path("/auftraege")
    @Transactional
    public List<AuftragDto> auftraege() {
        return HubSpotSyncAuftrag.<HubSpotSyncAuftrag>find("order by id desc").list()
                .stream().map(HubSpotSyncResource::zuDto).toList();
    }

    @POST
    @Path("/contacts/{personId}")
    public Map<String, Object> contact(@PathParam("personId") Long personId) {
        return Map.of("auftragId", sync.enqueueContact(personId).id);
    }

    @POST
    @Path("/consent/{personId}")
    public Map<String, Object> consent(@PathParam("personId") Long personId) {
        return Map.of("auftragId", sync.enqueueConsentChange(personId).id);
    }

    @POST
    @Path("/companies/{organisationId}")
    public Map<String, Object> company(@PathParam("organisationId") Long organisationId) {
        return Map.of("auftragId", sync.enqueueCompany(organisationId).id);
    }

    /** Recht auf Vergessen (Art. 17) anstoßen — im Cockpit mit Bestätigung. */
    @POST
    @Path("/erasure/{personId}")
    public Map<String, Object> erasure(@PathParam("personId") Long personId) {
        return Map.of("auftragId", sync.enqueueErasure(personId).id);
    }

    /** Backfill: alle aktiven Parteien einreihen. */
    @POST
    @Path("/backfill")
    public Map<String, Integer> backfill() {
        return sync.reconcileAlle();
    }

    /** Fällige Aufträge sofort verarbeiten (Demo/Sofort-Sync, statt auf den Dispatcher zu warten). */
    @POST
    @Path("/run")
    public Map<String, Object> run() {
        return Map.of("verarbeitet", sync.verarbeiteFaellige(200));
    }

    @POST
    @Path("/retry/{auftragId}")
    public Map<String, Object> retry(@PathParam("auftragId") Long auftragId) {
        HubSpotSyncAuftrag a = sync.neuVersuch(auftragId);
        return Map.of("status", a == null ? "unbekannt" : a.status.name());
    }

    private static AuftragDto zuDto(HubSpotSyncAuftrag a) {
        String partei = a.person != null ? (a.person.vorname + " " + a.person.nachname)
                : a.organisation != null ? a.organisation.name : "—";
        return new AuftragDto(a.id, a.objektTyp.name(), a.operation.name(), a.status.name(),
                partei, a.versuche, a.letzterFehler, a.erstelltAm, a.erledigtAm);
    }
}
