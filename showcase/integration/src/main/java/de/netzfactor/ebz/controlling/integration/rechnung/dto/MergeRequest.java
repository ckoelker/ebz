package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Zusammenführen zweier Debitoren (R3): {@code quellId} (die Dublette) geht in {@code zielId} (den
 * überlebenden Golden-Record) auf. Forderungen/Anmeldungen werden umgehängt, Altnummern bleiben als
 * Alias erhalten.
 */
public record MergeRequest(
        @NotNull Long quellId,
        @NotNull Long zielId) {
}
