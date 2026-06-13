package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Übernahme eines Altbestand-Debitors (R3): die alte Nummer ({@code externeNr}) aus dem {@code quelle}-
 * System wird als Alias auf den deduplizierten Golden-Record gehängt. So kollabieren mehrere
 * Alt-Debitoren derselben Identität auf einen Datensatz, ohne dass eine Altnummer verloren geht.
 */
public record BestandImportDto(
        @NotBlank @Size(max = 40) String quelle,
        @NotBlank @Size(max = 64) String externeNr,
        @Valid @NotNull DebitorAnlageDto debitor) {
}
