package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;
import de.netzfactor.ebz.controlling.integration.rechnung.validation.StimmigeBerufsschulAnmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.validation.StimmigeHochschulAnmeldung;

/**
 * Anmeldung als Abrechnungsbasis (Stack B). Feld-Constraints spiegeln sich ins /q/openapi-Schema;
 * die Vollständigkeits-/Stimmigkeitsregeln sind Cross-Field und liegen server-seitig in
 * {@link StimmigeBerufsschulAnmeldung} bzw. {@link StimmigeHochschulAnmeldung} (R6: Semester/Betrag,
 * optionaler Firmen-Split → zwei Forderungen, Raten).
 */
@StimmigeBerufsschulAnmeldung
@StimmigeHochschulAnmeldung
public record AnmeldungDto(
        Long id,
        Long version,
        @NotNull AnmeldungTyp typ,
        @NotBlank @Size(max = 200) String teilnehmerName,
        @Size(max = 200) String teilnehmerEmail,
        Long bildungsangebotId,
        @NotNull Long zahlungspflichtigerDebitorId,
        @NotNull AnmeldungStatus status,
        // ── BERUFSSCHULE (Cross-Field-Pflicht über @StimmigeBerufsschulAnmeldung) ──
        @Pattern(regexp = "^\\d{4}/\\d{4}$") String schuljahr,
        @Min(1) @Max(2) Integer halbjahr,
        Zimmerart zimmerart,
        @Min(0) Integer unterrichtBetragCent,
        @Min(0) Integer uebernachtungBetragCent,
        // ── HOCHSCHULE (R6) ──
        @Pattern(regexp = "^(WS|SS)\\d{4}$") String semester,
        @Min(0) Integer semesterbetragCent,
        /** Optionaler Firmen-Mitzahler (dualer Studienplatz) → zweite, getrennte Rechnung. */
        Long firmaDebitorId,
        @Min(0) Integer firmaAnteilCent,
        @Min(1) @Max(12) Integer ratenAnzahl) {
}
