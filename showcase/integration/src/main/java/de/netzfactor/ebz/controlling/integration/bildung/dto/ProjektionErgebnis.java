package de.netzfactor.ebz.controlling.integration.bildung.dto;

/** Ergebnis einer Shop-Projektion (§11.6): die zurückgeschriebenen Vendure-IDs (Produkt + Variante). */
public record ProjektionErgebnis(Long id, String code, String vendureProductId, String vendureVariantId) {
}
