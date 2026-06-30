package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorRolle;

/**
 * Debitor-Stammdaten (Stack B: einzige Validierungsquelle → /q/openapi → SDK/zod). {@code debitorNr}
 * wird in R1 vom Aufrufer geliefert und unique geprüft; die automatische Nummernkreis-Hoheit +
 * Match/Merge ist R3.
 */
public record DebitorDto(
        Long id,
        Long version,
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{2,32}$") String debitorNr,
        @NotNull Bereich bereich,
        @NotNull DebitorRolle rolle,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String strasse,
        @Size(max = 10) String plz,
        @Size(max = 120) String ort,
        @Size(max = 2) String land,
        @Size(max = 20) String ustId,
        @Size(max = 34) String iban,
        @Size(max = 200) String email,
        /** Nur Ausgabe (R3): AKTIV / ZUSAMMENGEFUEHRT. */
        String status,
        /** Nur Ausgabe (R3): bei ZUSAMMENGEFUEHRT der überlebende Golden-Record. */
        Long goldenDebitorId) {
}
