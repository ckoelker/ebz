package de.netzfactor.ebz.controlling.integration.lms.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.ScormVersion;

/**
 * Flaches DTO für einen WBT-Katalogeintrag. Einzige Validierungsquelle (Stack B) → smallrye-openapi →
 * /q/openapi → orval (TS + zod). {@code id}/{@code version} read-only (Mass-Assignment-Schutz: der
 * Mapper übernimmt nie id/version). {@code openolatKey} kommt aus dem REST-Import (vgl. lms-import-seed.sh).
 */
public record WbtKursDto(
        Long id,
        Long version,
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{2,32}$") String code,
        @NotBlank @Size(max = 200) String titel,
        @Size(max = 2000) String kurzbeschreibung,
        @Min(1) Long openolatKey,
        ScormVersion scormVersion,
        @Min(0) Integer preisCent,
        boolean shopVerkauf,
        @NotNull AngebotStatus status,
        @Size(max = 64) String vendureProductId,
        @DecimalMin("0.0") BigDecimal sollStundenAnrechenbar) {
}
