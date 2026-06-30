package de.netzfactor.ebz.controlling.integration.party.web;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import de.netzfactor.ebz.controlling.integration.party.model.DublettenReview;
import de.netzfactor.ebz.controlling.integration.party.service.DublettenReviewService;
import de.netzfactor.ebz.controlling.integration.party.service.DublettenReviewService.Art;
import de.netzfactor.ebz.controlling.integration.party.service.DublettenReviewService.Entscheidung;
import de.netzfactor.ebz.controlling.integration.party.service.DublettenReviewService.Fall;

/**
 * HITL-Dubletten-Review (Schema {@code party}): die <b>Queue</b> offener Fälle (mit KI-Vorschlägen) und
 * die <b>menschliche Entscheidung</b> (Merge vs. bestätigte Neuanlage), auditiert. Alle Endpunkte sind
 * der internen Sachbearbeitung vorbehalten (Rolle {@code rechnung-pflege}); der/die Entscheider:in wird
 * aus dem Token-{@code sub} gezogen (nicht aus dem Body — nicht fälschbar).
 */
@Path("/party/reviews")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DublettenReviewResource {

    @Inject
    DublettenReviewService service;

    public record EntscheidungDto(@NotNull Art art, @NotNull Long kandidatId,
            @NotNull Entscheidung entscheidung, Long zielId) {
    }

    public record ReviewView(Long id, String art, Long kandidatId, Long zielId, String entscheidung,
            Double kiAehnlichkeit, String kiEinschaetzung, String kiBegruendung, String entschiedenVon) {
    }

    /**
     * Queue offener Dubletten-Fälle (Firmen + Personen) mit KI-Bewertung je Merge-Ziel.
     * <b>Bewusst nicht {@code @Transactional}</b>: der Fan-out ruft pro Kandidatenpaar das LLM (langes
     * externes I/O); in einer Request-JTA-Transaktion würde die Summe der Aufrufe das Tx-Timeout reißen
     * (Rollback). Die DB-Zugriffe sind reine, id-basierte Lesezugriffe (auto-commit), der LLM-Aufruf läuft
     * ohne gehaltene Transaktion und ist über {@link de.netzfactor.ebz.controlling.integration.party.service.KiUrteilCache}
     * gecacht. Schreibende Entscheidungen bleiben in {@code entscheide(...)} transaktional.
     */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/queue")
    public List<Fall> queue() {
        return service.offeneFaelle();
    }

    /** Menschliche Entscheidung (Merge oder bestätigte Neuanlage) — auditiert, Entscheider aus dem Token. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/entscheidung")
    @Transactional
    public ReviewView entscheide(@Valid EntscheidungDto dto, @Context SecurityContext ctx) {
        DublettenReview r = service.entscheide(dto.art(), dto.kandidatId(), dto.entscheidung(),
                dto.zielId(), ctx.getUserPrincipal().getName());
        return new ReviewView(r.id, r.art, r.kandidatId, r.zielId, r.entscheidung,
                r.kiAehnlichkeit, r.kiEinschaetzung, r.kiBegruendung, r.entschiedenVon);
    }
}
