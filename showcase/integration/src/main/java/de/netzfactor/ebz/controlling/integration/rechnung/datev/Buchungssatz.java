package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.time.LocalDate;

/**
 * Ein DATEV-Buchungssatz (Zeile im Buchungsstapel). {@code umsatzCent} ist stets positiv; die Richtung
 * steht im {@code sollHaben} ("S"/"H"). Bei Ausgangsrechnungen ist {@code konto} das Debitor-
 * Personenkonto und {@code gegenkonto} das Erlös-Sachkonto (Korrekturbelege kehren S/H um).
 */
public record Buchungssatz(
        long umsatzCent,
        String sollHaben,
        String konto,
        String gegenkonto,
        String buSchluessel,
        LocalDate belegdatum,
        String belegfeld1,
        String buchungstext) {
}
