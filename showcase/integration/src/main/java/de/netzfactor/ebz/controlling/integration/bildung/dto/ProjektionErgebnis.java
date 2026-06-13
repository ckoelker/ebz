package de.netzfactor.ebz.controlling.integration.bildung.dto;

/** Ergebnis einer Shop-Projektion (§11.6): die zurückgeschriebene Vendure-Produkt-ID. */
public record ProjektionErgebnis(Long id, String code, String vendureProductId) {
}
