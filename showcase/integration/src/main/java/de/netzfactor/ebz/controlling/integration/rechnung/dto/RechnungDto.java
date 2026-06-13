package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import java.time.LocalDate;
import java.util.List;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;

/** Lese-Sicht eines Belegs (Kopf + Positionen + Summe). Schreiben erfolgt über die Lebenszyklus-Endpunkte. */
public record RechnungDto(
        Long id,
        long version,
        Belegart belegart,
        Bereich bereich,
        String nummer,
        Long debitorId,
        String zeitraumBezeichnung,
        LocalDate ausstellungsdatum,
        int zahlungszielTage,
        RechnungStatus status,
        Long originalRechnungId,
        long summeCent,
        List<RechnungPositionDto> positionen) {
}
