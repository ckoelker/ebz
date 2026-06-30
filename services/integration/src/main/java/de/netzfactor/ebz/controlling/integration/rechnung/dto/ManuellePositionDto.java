package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

/**
 * Manuell ergänzte/erfasste Position (nur im ENTWURF zulässig, sowie als Korrektur-Position bei
 * Gutschrift/Nachberechnung). Beträge bleiben variabel = manuelle Betragseingabe (Sachbearbeiter).
 */
public record ManuellePositionDto(
        @Size(max = 200) String teilnehmerName,
        @NotBlank @Size(max = 300) String beschreibung,
        int menge,
        int einzelbetragCent,
        @NotNull Steuerfall steuerfall,
        int steuersatz,
        @Size(max = 200) String befreiungsgrund,
        @NotNull Leistungsart leistungsart) {
}
