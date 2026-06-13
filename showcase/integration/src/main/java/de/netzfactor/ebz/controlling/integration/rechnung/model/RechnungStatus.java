package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Belegstatus. {@code ENTWURF} ist editierbar und trägt noch keine Nummer; mit {@code AUSGESTELLT}
 * erfolgt die Festschreibung (lückenlose Nummer, unveränderbar). {@code BEZAHLT}/{@code STORNIERT}
 * sind Folgezustände.
 */
public enum RechnungStatus {
    ENTWURF, AUSGESTELLT, BEZAHLT, STORNIERT
}
