package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungVersandStatus;

/** Lese-Sicht eines Belegs (Kopf + Positionen + Summe + Versand + Zahlungseingang). Schreiben über die Lebenszyklus-Endpunkte. */
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
        RechnungVersandStatus versandStatus,
        Instant versendetAm,
        String versendetAn,
        LocalDate bezahltAm,
        Long zahlbetragCent,
        String zahlungsReferenz,
        List<RechnungPositionDto> positionen) {
}
