package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

import java.time.LocalDate;
import java.util.List;

/**
 * Unveränderliches Daten-View einer ausgestellten Rechnung für die ZUGFeRD-Erzeugung — aus den
 * Entities zusammengestellt (im Transaktions-Kontext via {@code RechnungZugferdMapper}), damit
 * {@link PdfA3Generator}/{@link ZugferdService} ohne Persistenz arbeiten und plain testbar sind.
 * <p>
 * Deckt Rechnungen (Typ 380) und Korrekturbelege ab: {@link #gutschrift()} = Gutschrift/Storno
 * (ZUGFeRD-Typ 381, positive Beträge nach EN-16931-Konvention), mit Pflicht-Bezug auf die
 * Originalrechnung ({@link #originalNummer()}/{@link #originalDatum()}, BT-25/BT-26). R2a:
 * Berufsschule, durchgängig steuerbefreit (§4 UStG, Kategorie {@code E}).
 */
public record RechnungZugferdDaten(
        String nummer,
        LocalDate ausstellungsdatum,
        int zahlungszielTage,
        Verkaeufer verkaeufer,
        Empfaenger empfaenger,
        String zeitraumBezeichnung,
        String befreiungsgrund,
        /** Anzeige-/Belegbezeichnung, z. B. "Rechnung", "Gutschrift", "Storno", "Nachberechnung". */
        String belegBezeichnung,
        /** true = Gutschrift/Storno → ZUGFeRD-Typ 381 (Beträge als Betrag, nicht negiert). */
        boolean gutschrift,
        /** Bezug auf die Originalrechnung (BT-25), Pflicht bei Korrekturbelegen; sonst {@code null}. */
        String originalNummer,
        /** Ausstellungsdatum der Originalrechnung (BT-26); sonst {@code null}. */
        LocalDate originalDatum,
        /** Leistungszeitraum BG-14 (BT-73/BT-74); sonst {@code null}. */
        LocalDate leistungVon,
        LocalDate leistungBis,
        List<Position> positionen) {

    /** Rechnungsempfänger (Bill-to) = Debitor. {@code email} = elektronische Adresse (BT-49). */
    public record Empfaenger(String debitorNr, String name, String strasse, String plz, String ort,
            String land, String ustId, String email) {
    }

    /** Positionszeile; Betrag in Cent (Menge ist in der Berufsschule stets 1). */
    public record Position(String beschreibung, long betragCent) {
    }

    public LocalDate faelligAm() {
        return ausstellungsdatum.plusDays(zahlungszielTage);
    }

    /** Anzeigebetrag einer Position: bei Gutschrift/Storno als positiver Betrag (EN-16931-Konvention). */
    public long anzeige(long betragCent) {
        return gutschrift ? Math.abs(betragCent) : betragCent;
    }

    public long gesamtCent() {
        return anzeige(positionen.stream().mapToLong(Position::betragCent).sum());
    }
}
