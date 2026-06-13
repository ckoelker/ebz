package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

/** Lese-Sicht einer Rechnungsposition (inkl. errechnetem {@code betragCent} = menge × einzelbetrag). */
public record RechnungPositionDto(
        Long id,
        String teilnehmerName,
        String beschreibung,
        int menge,
        int einzelbetragCent,
        long betragCent,
        Steuerfall steuerfall,
        int steuersatz,
        String befreiungsgrund,
        Leistungsart leistungsart,
        String herkunft) {
}
