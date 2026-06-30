package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Anstoß eines Berufsschul-Rechnungslaufs für einen Zeitraum. Erzeugt je zahlungspflichtigem Debitor
 * eine Sammelrechnung im Status ENTWURF (idempotent).
 */
public record RechnungslaufRequest(
        @NotNull @Pattern(regexp = "^\\d{4}/\\d{4}$") String schuljahr,
        @NotNull @Min(1) @Max(2) Integer halbjahr) {

    /** Reserviert für spätere Bereiche; R1 ist auf Berufsschule fixiert (siehe Service). */
    public RechnungslaufRequest {
    }
}
