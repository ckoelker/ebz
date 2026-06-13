package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zahlungsart;

/**
 * Normalisierte externe Bestellung (R7) — die <b>quellen-agnostische</b> Naht Commerce-SoR → Billing-
 * SoR: eine bezahlte/auf-Rechnung-Bestellung (z. B. aus Vendure) wird neben der {@code Anmeldung} zur
 * Abrechnungsbasis. Der Kunde wird über die Debitoren-Hoheit (R3) idempotent aufgelöst, die Bestellung
 * über {@code quelle|externeId} idempotent in einen Rechnungs-Entwurf überführt. Steuer je Position
 * (Shop = regelbesteuert), sodass ZUGFeRD (R2) und DATEV (R4) korrekt nachgelagert greifen.
 */
public record ExterneBestellung(
        @NotBlank @Size(max = 40) String quelle,
        @NotBlank @Size(max = 64) String externeId,
        @NotNull Zahlungsart zahlungsart,
        @Valid @NotNull DebitorAnlageDto debitor,
        @Valid @NotEmpty List<Position> positionen) {

    /** Bestellposition mit Betrag (Cent) und Steuer je Zeile. */
    public record Position(
            @NotBlank @Size(max = 300) String beschreibung,
            @Min(1) long betragCent,
            @NotNull Steuerfall steuerfall,
            @Min(0) Integer steuersatz) {
    }
}
