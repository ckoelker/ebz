package de.netzfactor.ebz.controlling.integration.rechnung.dto;

/**
 * Protokoll einer DATEV-Übergabe (R4) für die API-Antwort: aktiver Weg, Referenz (Dateiname bzw.
 * Transfer-Id), Anzahl Buchungen und Größe des erzeugten EXTF-Artefakts (Download separat).
 */
public record DatevProtokollDto(String modus, String referenz, int anzahlBuchungen,
        int artefaktBytes, String hinweis) {
}
