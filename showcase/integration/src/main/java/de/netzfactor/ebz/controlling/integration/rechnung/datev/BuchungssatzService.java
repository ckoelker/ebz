package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

/**
 * Übersetzt festgeschriebene Belege in DATEV-Buchungssätze (R4) — je Beleg ein Satz pro Steuerfall
 * (Erlöskonto): Debitor-Personenkonto gegen Erlös-Sachkonto, Umsatz positiv, Richtung über S/H
 * (Rechnung/Nachberechnung = S, Gutschrift/Storno = H). Konto/BU werden aus {@link DatevKonten}
 * (SKR + StB-Platzhalter) abgeleitet. Muss im Transaktions-Kontext laufen (lädt Debitor).
 */
@ApplicationScoped
public class BuchungssatzService {

    @Inject
    DatevKonten konten;

    public List<Buchungssatz> fuerBeleg(Rechnung r) {
        if (r.nummer == null) {
            return List.of(); // nur festgeschriebene Belege
        }
        Debitor d = Debitor.findById(r.debitorId);
        String personenkonto = personenkonto(d, r.debitorId);
        boolean haben = r.belegart == Belegart.GUTSCHRIFT || r.belegart == Belegart.STORNO;
        String sollHaben = haben ? "H" : "S";
        String text = buchungstext(r);

        // Positionsbeträge je Steuerfall summieren (stabile Reihenfolge).
        Map<Steuerfall, Long> jeFall = new LinkedHashMap<>();
        for (RechnungPosition p : r.positionen) {
            jeFall.merge(p.steuerfall, p.betragCent(), Long::sum);
        }
        List<Buchungssatz> saetze = new ArrayList<>();
        for (Map.Entry<Steuerfall, Long> e : jeFall.entrySet()) {
            long umsatz = Math.abs(e.getValue());
            if (umsatz == 0) {
                continue;
            }
            saetze.add(new Buchungssatz(umsatz, sollHaben, personenkonto,
                    konten.erloeskonto(e.getKey()), konten.buSchluessel(e.getKey()),
                    r.ausstellungsdatum != null ? r.ausstellungsdatum : LocalDate.now(),
                    r.nummer, text));
        }
        return saetze;
    }

    public List<Buchungssatz> fuerBelege(List<Rechnung> belege) {
        List<Buchungssatz> alle = new ArrayList<>();
        for (Rechnung r : belege) {
            alle.addAll(fuerBeleg(r));
        }
        return alle;
    }

    /** Debitor-Personenkonto = numerischer Teil der Debitorennummer (Fallback: 10000 + id). */
    private static String personenkonto(Debitor d, Long debitorId) {
        String nr = d == null ? null : d.debitorNr;
        String ziffern = nr == null ? "" : nr.replaceAll("[^0-9]", "");
        return ziffern.isEmpty() ? String.valueOf(10000 + debitorId) : ziffern;
    }

    private static String buchungstext(Rechnung r) {
        String art = switch (r.belegart) {
            case RECHNUNG -> "Rechnung";
            case GUTSCHRIFT -> "Gutschrift";
            case STORNO -> "Storno";
            case NACHBERECHNUNG -> "Nachberechnung";
        };
        String zeitraum = r.zeitraumBezeichnung == null || r.zeitraumBezeichnung.isBlank()
                ? "" : " " + r.zeitraumBezeichnung;
        return (art + " " + r.nummer + zeitraum).trim();
    }
}
