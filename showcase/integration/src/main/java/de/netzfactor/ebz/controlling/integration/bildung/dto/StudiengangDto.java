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
import de.netzfactor.ebz.controlling.integration.bildung.model.Studienabschluss;
import de.netzfactor.ebz.controlling.integration.bildung.model.Studienform;
import de.netzfactor.ebz.controlling.integration.bildung.validation.GueltigerZeitraum;

/** Flaches per-Typ-DTO für {@code STUDIENGANG} (§11.2). {@code startsemester} im Format WS/SS+Jahr. Cross-Field {@link GueltigerZeitraum}. */
@GueltigerZeitraum
public record StudiengangDto(
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
        boolean shopVerkauf,
        @Size(max = 64) String vendureProductId,
        @Size(max = 200) String zielgruppe,
        // studiengang-spezifisch
        @NotNull Studienabschluss abschluss,
        @NotNull Studienform studienform,
        @NotNull @Pattern(regexp = "^(WS|SS)\\d{4}$") String startsemester,
        @Min(1) int regelstudienzeitSemester,
        LocalDate akkreditierungBis,
        @Min(0) int ratenAnzahl,
        @Min(0) int plaetze) implements GemeinsamesAngebot {
}
