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

/**
 * Flaches per-Typ-DTO für {@code SEMINAR} (§11.2: kein {@code oneOf}-Supertyp).
 * <p>
 * <b>Dies ist die EINZIGE Validierungsquelle (Stack B, F3).</b> Die Bean-Validation-Annotationen
 * hier spiegelt {@code quarkus-smallrye-openapi} ins {@code /q/openapi}-JSON-Schema (required /
 * minLength/maxLength / pattern / minimum / enum), woraus {@code @hey-api/openapi-ts} TS-Typen + zod
 * erzeugt. Rohe Entities werden nie exponiert (§11.9-B: Mass-Assignment-Schutz).
 * <p>
 * {@code id}/{@code version} sind beim Anlegen leer (Server vergibt), beim Update relevant
 * (Optimistic Locking). Cross-Field-Regeln ({@code gueltigBis ≥ gueltigAb}) stehen nicht im
 * JSON-Schema → server-seitig in P1.2 (400-Anzeige).
 */
public record SeminarDto(
        Long id,
        Long version,

        /** Konstant {@code SEMINAR} — Diskriminator für die Frontend-Union (§11.9-D). */
        BildungsangebotTyp typ,

        // ── gemeinsame Felder ──
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

        // ── seminar-spezifisch ──
        @NotNull SeminarKategorie kategorie,
        @Min(1) int dauerUE,
        @Size(max = 120) String abschluss,
        boolean zertifikat,
        @Min(0) int minTN,
        @Min(1) int maxTN) {
}
