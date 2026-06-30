package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorRolle;

/**
 * Stammdaten zur <b>gouvernierten</b> Debitoren-Anlage (R3): hier wird KEINE Nummer mitgegeben — die
 * vergibt die Hoheits-Schicht zentral (idempotent). Bewusst getrennt vom R1-{@link DebitorDto}, das
 * für die manuelle CRUD-Pflege eine vom Aufrufer gelieferte Nummer trägt.
 */
public record DebitorAnlageDto(
        @NotNull Bereich bereich,
        @NotNull DebitorRolle rolle,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String strasse,
        @Size(max = 10) String plz,
        @Size(max = 120) String ort,
        @Size(max = 2) String land,
        @Size(max = 20) String ustId,
        @Size(max = 34) String iban,
        @Size(max = 200) String email) {
}
