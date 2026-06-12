package de.netzfactor.ebz.controlling.integration.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.hibernate.annotations.processing.Find;
import org.hibernate.annotations.processing.HQL;

import de.netzfactor.ebz.controlling.integration.model.HubSpotDeal;

/**
 * Compile-zeit-validierte Queries (statt stringbasierter Panache-Finder).
 * <p>
 * Hibernates Annotation-Processor prüft die HQL + Parameter zur Compile-Zeit gegen das
 * Entity-Modell und generiert die Implementierung {@code DealQueries_} (ein {@code @Dependent}
 * CDI-Bean) — daher wird hier das Interface injiziert, nicht statisch aufgerufen. Ein Tippfehler im
 * Feldnamen oder ein falscher Parametertyp bricht den <b>Build</b>, nicht erst die Laufzeit.
 */
public interface DealQueries {

    /** Idempotenter Upsert-Lookup (L5): leer, wenn der Deal noch nicht existiert. */
    @Find
    Optional<HubSpotDeal> byId(EntityManager em, String dealId);

    /** L10-Review-Queue: niedrige Konfidenz → manuelle Prüfung, kein Auto-Merge. */
    @HQL("where reviewRequired = true order by konfidenz")
    List<HubSpotDeal> reviewQueue(EntityManager em);

    /** Provenance-Kennzahl: wie viele Deals via LLM vs. Fallback angereichert wurden. */
    @HQL("select count(*) from HubSpotDeal where enrichedBy = :src")
    long countByEnrichedBy(EntityManager em, HubSpotDeal.EnrichedBy src);

    /** Gesamtzahl gelandeter Deals (für den Ingestion-Report). */
    @HQL("select count(*) from HubSpotDeal")
    long countAll(EntityManager em);
}
