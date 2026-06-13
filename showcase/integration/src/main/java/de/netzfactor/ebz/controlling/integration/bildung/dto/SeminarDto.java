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
import de.netzfactor.ebz.controlling.integration.bildung.model.SeminarKategorie;
import de.netzfactor.ebz.controlling.integration.bildung.validation.GueltigerZeitraum;

/**
 * Flaches per-Typ-DTO für {@code SEMINAR} (§11.2: kein {@code oneOf}). Einzige Validierungsquelle
 * (Stack B, F3) → smallrye-openapi → /q/openapi → @hey-api/openapi-ts (TS + zod). Gemeinsame Felder
 * flach wiederholt; implementiert {@link GemeinsamesAngebot} für den typ-übergreifenden Mapper.
 * {@link GueltigerZeitraum} = Cross-Field (server-seitig, nicht im Schema).
 */
@GueltigerZeitraum
public record SeminarDto(
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
        // seminar-spezifisch
        @NotNull SeminarKategorie kategorie,
        @Min(1) int dauerUE,
        @Size(max = 120) String abschluss,
        boolean zertifikat,
        @Min(0) int minTN,
        @Min(1) int maxTN) implements GemeinsamesAngebot {
}
