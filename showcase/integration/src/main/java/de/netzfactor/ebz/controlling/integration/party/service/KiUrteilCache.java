package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.cache.CacheResult;

import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil;

/**
 * Dünne Cache-Schicht vor dem {@link DublettenKlassifikator} (LLM): identische, bereits abstrahierte
 * Merkmals-Paare ({@code a}/{@code b}) liefern dasselbe Urteil und dürfen denselben (teuren) OpenAI-Call
 * teilen. Eigene Bean, weil CDI-Interceptoren ({@code @CacheResult}) nur bei <b>Aufrufen über Bean-Grenzen</b>
 * greifen — der {@link DublettenBerater} ruft hierher, nicht in sich selbst.
 *
 * <p>Wirkung: die HITL-Review-Queue bewertet pro Paar; gleichnamige Dubletten-Cluster (gleiche Merkmale)
 * kollabieren so auf einen einzigen Modell-Aufruf statt n·(n−1). Datensparsamkeit unverändert — gecacht
 * werden nur die schon abstrahierten Tokens, keine Roh-PII.
 */
@ApplicationScoped
public class KiUrteilCache {

    @Inject
    DublettenKlassifikator klassifikator;

    @CacheResult(cacheName = "dubletten-ki-urteil")
    public DublettenUrteil vergleiche(String a, String b) {
        return klassifikator.vergleiche(a, b);
    }
}
