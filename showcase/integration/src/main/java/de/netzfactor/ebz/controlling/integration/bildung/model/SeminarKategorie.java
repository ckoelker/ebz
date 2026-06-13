package de.netzfactor.ebz.controlling.integration.bildung.model;

/**
 * Inhaltliche Kategorie eines Seminar-Bildungsangebots (Stammdatum, vom Pfleger gesetzt).
 * Bewusst eigenständig zur HubSpot-Klassifikations-Allowlist
 * ({@code de.netzfactor.ebz.controlling.integration.model.SeminarKategorie}) — gleicher Name,
 * andere Bedeutung: hier kuratiertes Stammdatum, dort LLM-Allowlist.
 */
public enum SeminarKategorie {
    FUEHRUNG,
    FACHKOMPETENZ,
    COMPLIANCE,
    IT_DIGITAL,
    SPRACHEN,
    SONSTIGE
}
