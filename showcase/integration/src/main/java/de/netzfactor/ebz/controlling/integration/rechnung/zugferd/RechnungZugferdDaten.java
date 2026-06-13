package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

import java.time.LocalDate;
import java.util.List;

/**
 * Unveränderliches Daten-View einer ausgestellten Rechnung für die ZUGFeRD-Erzeugung — aus den
 * Entities zusammengestellt (im Transaktions-Kontext), damit {@link PdfA3Generator}/
 * {@link ZugferdService} ohne Persistenz arbeiten und plain testbar sind.
 * <p>
 * R2a deckt Berufsschul-Rechnungen ab (Steuerbefreiung §4 UStG, positive Beträge). Korrekturbelege
 * (Storno/Gutschrift = Typ 381) folgen in einem späteren Schritt.
 */
public record RechnungZugferdDaten(
        String nummer,
        LocalDate ausstellungsdatum,
        int zahlungszielTage,
        Verkaeufer verkaeufer,
        Empfaenger empfaenger,
        String zeitraumBezeichnung,
        String befreiungsgrund,
        List<Position> positionen) {

    /** Rechnungsempfänger (Bill-to) = Debitor. */
    public record Empfaenger(String debitorNr, String name, String strasse, String plz, String ort,
            String land, String ustId) {
    }

    /** Positionszeile; Betrag in Cent (Menge ist in der Berufsschule stets 1). */
    public record Position(String beschreibung, long betragCent) {
    }

    public LocalDate faelligAm() {
        return ausstellungsdatum.plusDays(zahlungszielTage);
    }

    public long gesamtCent() {
        return positionen.stream().mapToLong(Position::betragCent).sum();
    }
}
