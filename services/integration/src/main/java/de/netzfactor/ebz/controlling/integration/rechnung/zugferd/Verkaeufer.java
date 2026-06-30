package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

/**
 * Aussteller-/Verkäuferstammdaten der EBZ für die E-Rechnung. Adresse + Steuer-/Bankangaben werden
 * für das EN-16931-XML zwingend benötigt (im PDF-Briefkopf ohnehin sichtbar). Snapshot-Record, damit
 * der {@link PdfA3Generator}/{@link ZugferdService} frei von Config-/CDI-Abhängigkeiten bleiben
 * (plain unit-testbar wie im erechnung-Referenzprototyp).
 */
public record Verkaeufer(
        String name,
        String strasse,
        String plz,
        String ort,
        String land,
        String ustId,
        String email,
        String kontaktName,
        String kontaktTelefon,
        String bankName,
        String iban,
        String bic) {
}
