package de.netzfactor.ebz.controlling.integration.bildung.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;
import de.netzfactor.ebz.controlling.integration.bildung.model.PreisModell;
import de.netzfactor.ebz.controlling.integration.bildung.validation.GueltigerZeitraum;

/** Flaches per-Typ-DTO für {@code BERUFSSCHULJAHR} (§11.2). {@code schildNrwSchluessel} = nur Bezugsschlüssel (§11.8). Cross-Field {@link GueltigerZeitraum}. */
@GueltigerZeitraum
public record BerufsschuljahrDto(
        Long id,
        Long version,
        BildungsangebotTyp typ,
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{2,32}$") String code,
        @NotBlank @Size(max = 200) String titel,
        @NotNull Bereich bereich,
        @Size(max = 2000) String kurzbeschreibung,
        @NotNull AngebotStatus status,
        @NotNull LocalDate gueltigAb,
        LocalDate gueltigBis,
        @Size(max = 120) String verantwortlich,
        @NotNull PreisModell preisModell,
        @Min(0) Integer preisCent,
        @Min(1) Integer abrechnungIntervallMonate,
        @Min(0) Integer ratenGesamt,
        boolean shopVerkauf,
        @Size(max = 64) String vendureProductId,
        @Size(max = 200) String zielgruppe,
        // berufsschuljahr-spezifisch
        @NotBlank @Size(max = 200) String fachrichtung,
        @NotNull @Pattern(regexp = "^\\d{4}/\\d{2}$") String schuljahr,
        @Min(1) int jahrgang,
        LocalDate beginn,
        @Size(max = 32) String schildNrwSchluessel,
        @Min(0) int plaetze) implements GemeinsamesAngebot {
}
