package de.netzfactor.ebz.controlling.integration.bildung.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.Bereich;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.BildungsangebotTyp;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.PreisModell;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.SeminarKategorie;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.Studienabschluss;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungEnums.Studienform;

/**
 * Alle per-Typ-DTOs gebündelt (schlank: eine Datei statt fünf). Jedes DTO ist ein eigenes FLACHES
 * Schema (§11.2: kein {@code oneOf}) und die EINZIGE Validierungsquelle (Stack B, F3) — Bean
 * Validation hier → smallrye-openapi spiegelt sie ins {@code /q/openapi} → {@code @hey-api/openapi-ts}
 * generiert TS-Typen + zod. {@code @Schema(name=…)} hält die Schemanamen trotz Verschachtelung stabil
 * (z. B. {@code SeminarDto}). Gemeinsame Felder werden flach wiederholt (statt geschachtelt), damit je
 * Typ ein sauberes flaches Schema entsteht; {@link GemeinsamesAngebot} erlaubt dem Mapper die
 * typ-übergreifende Übernahme DTO→Entity. {@code id}/{@code version} leer beim Anlegen.
 */
public final class BildungDtos {

    private BildungDtos() {
    }

    /** Gemeinsame Supertyp-Felder; Record-Accessoren erfüllen das Interface automatisch. */
    public interface GemeinsamesAngebot {
        String code();

        String titel();

        Bereich bereich();

        String kurzbeschreibung();

        AngebotStatus status();

        LocalDate gueltigAb();

        LocalDate gueltigBis();

        String verantwortlich();

        PreisModell preisModell();

        boolean shopVerkauf();

        String vendureProductId();

        String zielgruppe();
    }

    @Schema(name = "SeminarDto")
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

    @Schema(name = "TagungDto")
    public record TagungDto(
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
            // tagungsspezifisch
            @NotBlank @Size(max = 200) String thema,
            @NotNull LocalDate terminVon,
            LocalDate terminBis,
            @Size(max = 200) String ort,
            @Pattern(regexp = "^https?://.+") @Size(max = 300) String programmUrl,
            @Min(1) int maxTN) implements GemeinsamesAngebot {
    }

    @Schema(name = "BerufsschuljahrDto")
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

    @Schema(name = "StudiengangDto")
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

    /** Read-only-Header-Projektion über die Registry (§11.4). */
    @Schema(name = "RegistryItemDto")
    public record RegistryItemDto(
            Long id,
            BildungsangebotTyp typ,
            String code,
            String titel,
            Bereich bereich,
            AngebotStatus status,
            boolean shopVerkauf) {
    }
}
