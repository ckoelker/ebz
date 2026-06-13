package de.netzfactor.ebz.controlling.integration.rechnung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Nummernkreis;

/**
 * Vergibt lückenlose Belegnummern je {@code Bereich × Belegart} (GoBD §8.1). Die Vergabe ist atomar:
 * die Nummernkreis-Zeile wird pessimistisch gesperrt ({@code PESSIMISTIC_WRITE} → {@code SELECT … FOR
 * UPDATE}), inkrementiert und freigegeben. Nebenläufige Läufe serialisieren auf der Zeile statt sich
 * Nummern zu duplizieren; weil die Nummer erst beim Ausstellen (nicht beim Entwurf) gezogen wird,
 * entstehen auch keine Lücken durch verworfene Entwürfe.
 */
@ApplicationScoped
public class NummernkreisService {

    /**
     * Zieht die nächste Nummer und liefert sie formatiert ({@code praefix + 5-stellig}, z. B.
     * {@code RE-BS-2026-00001}). Muss innerhalb der laufenden Transaktion des Ausstellens aufgerufen
     * werden, damit Lock + Inkrement mit der Festschreibung gemeinsam committen.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String vergib(Bereich bereich, Belegart belegart) {
        Nummernkreis kreis = Nummernkreis
                .find("bereich = ?1 and belegart = ?2", bereich, belegart)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (kreis == null) {
            kreis = new Nummernkreis();
            kreis.bereich = bereich;
            kreis.belegart = belegart;
            kreis.praefix = praefix(bereich, belegart);
            kreis.naechsteNummer = 1;
            kreis.persist();
        }
        long laufend = kreis.naechsteNummer;
        kreis.naechsteNummer = laufend + 1;
        return "%s%05d".formatted(kreis.praefix, laufend);
    }

    /** Default-Präfix je Bereich/Belegart (im Showcase fest; produktiv StB-/Mandanten-Konvention). */
    static String praefix(Bereich bereich, Belegart belegart) {
        String b = switch (bereich) {
            case BERUFSSCHULE -> "BS";
            case HOCHSCHULE -> "HS";
            case AKADEMIE -> "AK";
            case SHOP -> "SH";
        };
        String a = switch (belegart) {
            case RECHNUNG -> "RE";
            case GUTSCHRIFT -> "GU";
            case STORNO -> "ST";
            case NACHBERECHNUNG -> "NB";
        };
        return "%s-%s-".formatted(a, b);
    }
}
