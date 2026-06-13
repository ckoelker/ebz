package de.netzfactor.ebz.controlling.integration.bildung.model;

/**
 * Lebenszyklus eines Bildungsangebots (§11.9-F: Status als Lebenszyklus, nicht frei;
 * Soft-Delete = {@code ARCHIVIERT} statt Hard-Delete, da Vendure/Controlling referenzieren).
 */
public enum AngebotStatus {
    ENTWURF,
    AKTIV,
    ARCHIVIERT
}
