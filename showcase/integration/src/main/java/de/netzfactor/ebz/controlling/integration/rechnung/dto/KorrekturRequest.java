package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Korrekturbeleg-Anstoß (Gutschrift / Nachberechnung) zu einer ausgestellten Rechnung. Die Positionen
 * werden manuell erfasst (variable Beträge); der Beleg trägt automatisch den Bezug auf das Original.
 * Storno benötigt keine Positionen (Vollumkehr) und nutzt diesen Request nicht.
 */
public record KorrekturRequest(
        @Size(max = 200) String grund,
        @NotEmpty @Valid List<ManuellePositionDto> positionen) {
}
