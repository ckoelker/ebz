package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Anstoß des Hochschul-Rechnungslaufs (R6) für ein Semester (z. B. {@code WS2026}). */
public record HochschulLaufRequest(
        @NotBlank @Pattern(regexp = "^(WS|SS)\\d{4}$") String semester) {
}
