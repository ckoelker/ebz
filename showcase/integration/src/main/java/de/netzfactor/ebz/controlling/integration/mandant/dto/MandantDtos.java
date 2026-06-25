package de.netzfactor.ebz.controlling.integration.mandant.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import de.netzfactor.ebz.controlling.integration.mandant.model.IdpFoederation;
import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion;
import de.netzfactor.ebz.controlling.integration.mandant.model.SeatMeldung;

/**
 * Gebündelte, flache DTOs der Mandanten-Verwaltung (Stack B: einzige Validierungsquelle → smallrye-openapi
 * → {@code /q/openapi} → orval/zod). Gebündelt statt verstreut ({@code lean-file-structure}); die
 * {@code @Schema(name=…)} halten die Codegen-Namen stabil. {@code id}/{@code version} read-only
 * (Mass-Assignment-Schutz — der Mapper übernimmt nie id/version); {@code openolatOrganisationKey} ist
 * server-verwaltet (von der Org-Projektion M2 gesetzt) und wird nicht aus dem DTO übernommen.
 */
public final class MandantDtos {

    private MandantDtos() {
    }

    /** Ein Mandant (EBZ-Kernmandant, EBZ-Staff oder B2B-Flatrate). */
    @Schema(name = "MandantDto")
    public record MandantDto(
            Long id,
            Long version,
            @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{2,40}$") String schluessel,
            @NotBlank @Size(max = 200) String anzeigeName,
            @NotNull Mandant.Vertragstyp vertragstyp,
            Mandant.Status status,
            Long organisationId,
            Long openolatOrganisationKey,
            @Size(max = 300) String logoUrl,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String primaerFarbe,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String sekundaerFarbe) {
    }

    /** IdP-Föderation eines B2B-Mandanten (Keycloak-Organization, gebrokerter Kunden-IdP). */
    @Schema(name = "IdpFoederationDto")
    public record IdpFoederationDto(
            Long id,
            Long version,
            @NotBlank @Size(max = 60) String idpAlias,
            @NotBlank @Size(max = 500) String emailDomains,
            @NotNull IdpFoederation.Protokoll protokoll,
            IdpFoederation.Status status) {
    }

    /** Seat-Lizenz eines B2B-Mandanten (nur {@code ENTERPRISE_FLAT}). */
    @Schema(name = "LizenzvertragDto")
    public record LizenzvertragDto(
            Long id,
            Long version,
            @Min(0) int seatLimit,
            @NotNull LocalDate gueltigVon,
            LocalDate gueltigBis,
            boolean aktiv) {
    }

    /** Lese-Sicht auf den Stand der Org-Projektion (Outbox) eines Mandanten — read-only. */
    @Schema(name = "MandantProjektionDto")
    public record MandantProjektionDto(
            Long id,
            Long mandantId,
            MandantProjektion.Operation operation,
            MandantProjektion.Status status,
            int versuche,
            String letzterFehler,
            Long openolatOrganisationKey) {
    }

    /** Seat-Belegungs-Sicht eines Mandanten (M5) — read-only. {@code seatLimit} null = unbegrenzt. */
    @Schema(name = "SeatStatusDto")
    public record SeatStatusDto(
            Long mandantId,
            boolean begrenzt,
            Integer seatLimit,
            int belegung,
            boolean ueberbucht,
            long offeneMeldungen) {
    }

    /** Ergebnis einer Seat-Aufnahme-Prüfung (M5). */
    @Schema(name = "SeatAufnahmeDto")
    public record SeatAufnahmeDto(
            String entscheidung,
            int belegungVorher,
            Integer seatLimit,
            Long meldungId) {
    }

    /** Eine HITL-Überbuchungsmeldung (M5) — read-only. */
    @Schema(name = "SeatMeldungDto")
    public record SeatMeldungDto(
            Long id,
            Long mandantId,
            int belegungBeiMeldung,
            int seatLimit,
            SeatMeldung.Status status,
            String bestaetigtVon) {
    }
}
