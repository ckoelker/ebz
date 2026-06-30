package de.netzfactor.ebz.controlling.integration.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Roh-Deal aus der HubSpot-Antwort (results[].id + properties), bereits typisiert.
 * Finanzfelder bleiben hier unverändert deterministisch (L8).
 */
public record RawDeal(
        String id,
        String dealName,
        String company,
        String description,
        BigDecimal amount,
        String currency,
        String stage,
        String pipeline,
        Instant lastModified) {
}
