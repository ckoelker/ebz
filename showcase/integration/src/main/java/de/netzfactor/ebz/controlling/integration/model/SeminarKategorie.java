package de.netzfactor.ebz.controlling.integration.model;

/**
 * Allowlist der Seminar-Kategorien (L9: Enum-Allowlist gegen LLM-Halluzination).
 * Das LLM darf NUR diese Werte liefern; alles andere → UNBEKANNT (verworfen, nicht durchgereicht).
 */
public enum SeminarKategorie {
    FUEHRUNG,
    FACHKOMPETENZ,
    COMPLIANCE,
    IT_DIGITAL,
    SPRACHEN,
    SONSTIGE,
    /** Sentinel für nicht klassifizierbar / ungültige LLM-Antwort. */
    UNBEKANNT
}
