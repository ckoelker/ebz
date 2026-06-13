package de.netzfactor.ebz.controlling.integration.bildung.model;

/**
 * Diskriminator der Bildungsangebot-Familie (STI, §11.2). Zugleich lesbares Enum-Feld in den
 * DTOs (§11.9-D: der {@code @DiscriminatorColumn} selbst ist nicht direkt anzeig-/validierbar) und
 * Frontend-Diskriminator der TS-Union {@code Seminar | Tagung | …}.
 * <p>P1.0 baut nur {@link Seminar}; die übrigen Subtypen folgen in P1.1 (per-Typ-API).
 */
public enum BildungsangebotTyp {
    SEMINAR,
    TAGUNG,
    BERUFSSCHULJAHR,
    STUDIENGANG
}
