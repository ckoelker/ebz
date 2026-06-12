package de.netzfactor.ebz.controlling.integration.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.netzfactor.ebz.controlling.integration.enrichment.DealEnricher;
import de.netzfactor.ebz.controlling.integration.model.HubSpotDeal;
import de.netzfactor.ebz.controlling.integration.model.RawDeal;
import de.netzfactor.ebz.controlling.integration.repository.DealQueries;
import de.netzfactor.ebz.controlling.integration.source.DealSource;

/**
 * Orchestriert einen Ingestion-Lauf: Quelle lesen → parsen → filtern (L6) → anreichern → Upsert (L5).
 * Wird vom Camel-Timer und vom REST-Trigger über {@code direct:ingest} aufgerufen.
 */
@ApplicationScoped
public class IngestionService {

    private static final Logger LOG = Logger.getLogger(IngestionService.class);

    @Inject
    EntityManager em;

    @Inject
    DealSource dealSource;

    @Inject
    DealEnricher enricher;

    @Inject
    DealQueries queries;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "hubspot.inhouse-pipeline", defaultValue = "inhouse")
    String inhousePipeline;

    @Transactional
    public IngestionResult ingest() {
        String raw = dealSource.fetchDealsJson();
        int fetched = 0, skipped = 0, upserted = 0, reused = 0, viaLlm = 0, viaFallback = 0, review = 0;

        try {
            JsonNode results = mapper.readTree(raw).path("results");
            for (JsonNode node : results) {
                fetched++;
                RawDeal deal = parse(node);

                // ── L6: nur Inhouse-Pipeline, nur EUR ──
                if (!inhousePipeline.equalsIgnoreCase(deal.pipeline())) {
                    LOG.infof("Deal %s übersprungen: Pipeline '%s' ≠ Inhouse", deal.id(), deal.pipeline());
                    skipped++;
                    continue;
                }
                if (!"EUR".equalsIgnoreCase(deal.currency())) {
                    LOG.warnf("Deal %s verworfen: Fremdwährung '%s' (Warehouse erzwingt EUR)", deal.id(), deal.currency());
                    skipped++;
                    continue;
                }

                String hash = contentHash(deal);
                HubSpotDeal existing = queries.byId(em, deal.id()).orElse(null);

                if (existing != null && hash.equals(existing.contentHash)) {
                    // ── L12: unverändert → keine Re-Anreicherung, kein LLM-Aufruf ──
                    reused++;
                    if (existing.enrichedBy == HubSpotDeal.EnrichedBy.LLM) viaLlm++; else viaFallback++;
                    if (existing.reviewRequired) review++;
                    continue;
                }

                DealEnricher.Enrichment e = enricher.enrich(deal);
                HubSpotDeal entity = existing != null ? existing : new HubSpotDeal();
                apply(entity, deal, e, hash);
                entity.persist();   // L5: Upsert auf Deal-ID (neu = INSERT, vorhanden = managed UPDATE)

                upserted++;
                if (e.enrichedBy() == HubSpotDeal.EnrichedBy.LLM) viaLlm++; else viaFallback++;
                if (e.reviewRequired()) review++;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Ingestion fehlgeschlagen: " + ex.getMessage(), ex);
        }

        IngestionResult r = new IngestionResult(fetched, skipped, upserted, reused, viaLlm, viaFallback, review);
        LOG.infof("Ingestion fertig: %s", r);
        return r;
    }

    private RawDeal parse(JsonNode node) {
        String id = node.path("id").asText();
        JsonNode p = node.path("properties");
        BigDecimal amount = parseAmount(p.path("amount").asText(null));
        return new RawDeal(
                id,
                p.path("dealname").asText(null),
                p.path("company").asText(null),
                p.path("description").asText(null),
                amount,
                p.path("deal_currency_code").asText(null),
                p.path("dealstage").asText(null),
                p.path("pipeline").asText(null),
                parseInstant(p.path("hs_lastmodifieddate").asText(null)));
    }

    private void apply(HubSpotDeal d, RawDeal raw, DealEnricher.Enrichment e, String hash) {
        d.dealId = raw.id();
        // deterministisch (L8)
        d.dealName = raw.dealName();
        d.companyRaw = raw.company();
        d.amountCents = toCents(raw.amount());
        d.currency = raw.currency();
        d.stage = raw.stage();
        d.pipeline = raw.pipeline();
        d.sourceModifiedAt = raw.lastModified();
        // KI-Anreicherung (L8: nur Text) + Provenance (L10)
        d.companyNormalized = e.normalizedCompany();
        d.seminarKategorie = e.kategorie();
        d.deliveryType = e.deliveryType();
        d.konfidenz = e.konfidenz();
        d.enrichedBy = e.enrichedBy();
        d.modelId = e.modelId();
        d.promptVersion = DealEnricher.PROMPT_VERSION;
        d.enrichedAt = Instant.now();
        d.reviewRequired = e.reviewRequired();
        d.contentHash = hash;
        d.ingestedAt = Instant.now();
    }

    /** Content-Hash über die anreicherungsrelevanten Rohfelder (L12). */
    private String contentHash(RawDeal d) {
        String basis = String.join("",
                nz(d.dealName()), nz(d.company()), nz(d.description()),
                d.amount() == null ? "" : d.amount().toPlainString(), nz(d.stage()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long toCents(BigDecimal amount) {
        return amount == null ? 0L : amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException nfe) {
            return BigDecimal.ZERO;
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
