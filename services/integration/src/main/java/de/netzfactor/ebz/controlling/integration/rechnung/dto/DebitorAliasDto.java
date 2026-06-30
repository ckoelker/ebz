package de.netzfactor.ebz.controlling.integration.rechnung.dto;

/** Ansicht eines Debitor-Alias (R3): welche externe/alte Nummer auf welchen Golden-Record zeigt. */
public record DebitorAliasDto(Long id, Long debitorId, String quelle, String externeNr) {
}
