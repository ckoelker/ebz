package de.netzfactor.ebz.controlling.integration.web;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.camel.ProducerTemplate;

import de.netzfactor.ebz.controlling.integration.model.HubSpotDeal;
import de.netzfactor.ebz.controlling.integration.pipeline.IngestionResult;
import de.netzfactor.ebz.controlling.integration.repository.DealQueries;

/**
 * REST-Trigger + Read-Endpunkte für die Demo (On-demand statt nur Timer).
 * <ul>
 *   <li>{@code POST /ingest/run} — stößt einen Lauf über {@code direct:ingest} an, liefert das Ergebnis.</li>
 *   <li>{@code GET  /ingest/review} — L10-Review-Queue (niedrige Konfidenz) via compile-safe Query.</li>
 *   <li>{@code GET  /ingest/stats}  — Provenance-Kennzahlen (LLM vs. Fallback).</li>
 * </ul>
 */
@Path("/ingest")
@Produces(MediaType.APPLICATION_JSON)
public class IngestionResource {

    @Inject
    ProducerTemplate producer;

    @Inject
    EntityManager em;

    @Inject
    DealQueries queries;

    @POST
    @Path("/run")
    public IngestionResult run() {
        return producer.requestBody("direct:ingest", null, IngestionResult.class);
    }

    @GET
    @Path("/review")
    @Transactional
    public List<HubSpotDeal> reviewQueue() {
        return queries.reviewQueue(em);
    }

    @GET
    @Path("/stats")
    @Transactional
    public Stats stats() {
        return new Stats(
                queries.countAll(em),
                queries.countByEnrichedBy(em, HubSpotDeal.EnrichedBy.LLM),
                queries.countByEnrichedBy(em, HubSpotDeal.EnrichedBy.FALLBACK));
    }

    public record Stats(long total, long viaLlm, long viaFallback) {
    }
}
