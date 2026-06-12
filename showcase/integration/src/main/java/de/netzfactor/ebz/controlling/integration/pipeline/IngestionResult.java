package de.netzfactor.ebz.controlling.integration.pipeline;

/**
 * Ergebnis eines Ingestion-Laufs (für Logs + REST-Antwort).
 *
 * @param fetched     roh aus der Quelle gelesene Deals
 * @param skipped     verworfen (Nicht-Inhouse-Pipeline / Fremdwährung — L6)
 * @param upserted    in {@code stg_hubspot_deal} geschrieben (neu + aktualisiert)
 * @param reused      unverändert (Content-Hash gleich → keine Re-Anreicherung, L12)
 * @param viaLlm      davon via LLM (OpenAI/LangChain4j) angereichert
 * @param viaFallback davon via regelbasiertem Fallback
 * @param reviewRequired davon zur manuellen Prüfung markiert (L10)
 */
public record IngestionResult(
        int fetched,
        int skipped,
        int upserted,
        int reused,
        int viaLlm,
        int viaFallback,
        int reviewRequired) {
}
