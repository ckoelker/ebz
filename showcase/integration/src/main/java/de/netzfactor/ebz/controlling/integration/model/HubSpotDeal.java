package de.netzfactor.ebz.controlling.integration.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Lande-/Staging-Tabelle {@code stg_hubspot_deal} im Warehouse (DB {@code controlling}).
 * dbt (M3) liest sie als Source {@code stg_hubspot_deals}.
 * <p>
 * Finanzfelder ({@code amountCents}, {@code currency}, {@code stage}) sind deterministisch aus dem
 * Rohdatensatz (L8). Klassifikationsfelder + Provenance ({@code enrichedBy}, {@code modelId},
 * {@code promptVersion}, {@code enrichedAt}, {@code konfidenz}, {@code reviewRequired}) dokumentieren
 * die KI-Anreicherung (L10). PK = HubSpot-Deal-ID → idempotenter Upsert (L5).
 */
@Entity
@Table(name = "stg_hubspot_deal")
public class HubSpotDeal extends PanacheEntityBase {

    @Id
    @Column(name = "deal_id")
    public String dealId;

    // ── deterministisch aus HubSpot (L8) ──
    @Column(name = "deal_name", length = 512)
    public String dealName;

    @Column(name = "company_raw", length = 512)
    public String companyRaw;

    @Column(name = "amount_cents")
    public long amountCents;

    @Column(name = "currency", length = 3)
    public String currency;

    @Column(name = "stage")
    public String stage;

    @Column(name = "pipeline")
    public String pipeline;

    @Column(name = "source_modified_at")
    public Instant sourceModifiedAt;

    // ── KI-Anreicherung (L8: nur Text) ──
    @Column(name = "company_normalized", length = 512)
    public String companyNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "seminar_kategorie")
    public SeminarKategorie seminarKategorie;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type")
    public DeliveryType deliveryType;

    @Column(name = "konfidenz")
    public double konfidenz;

    // ── Provenance / Governance (L10, L12) ──
    @Enumerated(EnumType.STRING)
    @Column(name = "enriched_by")
    public EnrichedBy enrichedBy;

    @Column(name = "model_id")
    public String modelId;

    @Column(name = "prompt_version")
    public String promptVersion;

    @Column(name = "enriched_at")
    public Instant enrichedAt;

    /** L10: bei niedriger Konfidenz kein Auto-Merge in den Golden Record → manuelle Prüfung. */
    @Column(name = "review_required")
    public boolean reviewRequired;

    /** L12: Content-Hash der angereicherten Felder → Delta-Erkennung (Re-Enrichment nur bei Änderung). */
    @Column(name = "content_hash", length = 64)
    public String contentHash;

    @Column(name = "ingested_at")
    public Instant ingestedAt;

    /** Herkunft der Anreicherung (LLM = KI via LangChain4j/OpenAI; konkretes Modell in modelId). */
    public enum EnrichedBy { LLM, FALLBACK }
}
