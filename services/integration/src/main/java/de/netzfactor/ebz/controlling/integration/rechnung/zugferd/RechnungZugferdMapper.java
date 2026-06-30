package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

/**
 * Baut aus einer (festgeschriebenen) {@link Rechnung} das persistenzfreie {@link RechnungZugferdDaten}
 * -View — die einzige Stelle, die Entities in das ZUGFeRD-Modell überführt. Muss im Transaktions-
 * Kontext laufen (lädt Debitor + ggf. Originalrechnung); genutzt vom Endpoint und vom GoBD-Archiv.
 * <p>
 * Gutschrift/Storno → ZUGFeRD-Typ 381 mit Pflicht-Bezug auf die Originalrechnung. Der
 * Leistungszeitraum (BG-14) wird – wenn möglich – aus der Zeitraum-Bezeichnung der Berufsschule
 * ("Schuljahr JJJJ/JJJJ, n. Halbjahr") abgeleitet; andernfalls bleibt er offen.
 */
@ApplicationScoped
public class RechnungZugferdMapper {

    private static final Pattern SCHULJAHR =
            Pattern.compile("(\\d{4})\\s*/\\s*(\\d{4}).*?(\\d)\\.\\s*Halbjahr");

    @Inject
    EbzVerkaeufer verkaeufer;

    public RechnungZugferdDaten baue(Rechnung r) {
        Debitor d = Debitor.findById(r.debitorId());
        if (d == null) {
            throw new IllegalStateException("Debitor zur Rechnung fehlt: " + r.debitorId());
        }
        var empf = new RechnungZugferdDaten.Empfaenger(d.debitorNr, d.name, d.strasse, d.plz, d.ort,
                d.land, d.ustId, d.email);

        List<RechnungZugferdDaten.Position> pos = r.positionen.stream()
                .map(p -> new RechnungZugferdDaten.Position(p.beschreibung, p.betragCent(),
                        p.steuerfall == Steuerfall.BEFREIT ? "E" : "S", p.steuersatz))
                .toList();

        boolean hatBefreit = r.positionen.stream().anyMatch(p -> p.steuerfall == Steuerfall.BEFREIT);
        String grund = r.positionen.stream().map(p -> p.befreiungsgrund)
                .filter(g -> g != null && !g.isBlank()).findFirst()
                .orElse(hatBefreit ? "Umsatzsteuerbefreit nach § 4 UStG (Bildungsleistung)" : "");

        boolean gutschrift = r.belegart == Belegart.GUTSCHRIFT || r.belegart == Belegart.STORNO;

        String originalNummer = null;
        LocalDate originalDatum = null;
        if (r.originalRechnung != null) {
            Rechnung orig = Rechnung.findById(r.originalRechnungId());
            if (orig != null) {
                originalNummer = orig.nummer;
                originalDatum = orig.ausstellungsdatum;
            }
        }

        LocalDate[] zeitraum = leistungszeitraum(r.zeitraumBezeichnung);

        return new RechnungZugferdDaten(r.nummer, r.ausstellungsdatum, r.zahlungszielTage,
                verkaeufer.snapshot(), empf, r.zeitraumBezeichnung, grund, bezeichnung(r.belegart),
                gutschrift, originalNummer, originalDatum, zeitraum[0], zeitraum[1], pos);
    }

    private static String bezeichnung(Belegart art) {
        return switch (art) {
            case GUTSCHRIFT -> "Gutschrift";
            case STORNO -> "Storno";
            case NACHBERECHNUNG -> "Nachberechnung";
            case RECHNUNG -> "Rechnung";
        };
    }

    /**
     * Leitet den Schul-Halbjahres-Zeitraum aus der Bezeichnung ab: 1. Halbjahr = 01.08.–31.01.,
     * 2. Halbjahr = 01.02.–31.07. (deutsche Schuljahr-Konvention). Nicht erkennbar → beide {@code null}.
     */
    static LocalDate[] leistungszeitraum(String bezeichnung) {
        if (bezeichnung == null) {
            return new LocalDate[] {null, null};
        }
        Matcher m = SCHULJAHR.matcher(bezeichnung);
        if (!m.find()) {
            return new LocalDate[] {null, null};
        }
        int jahr1 = Integer.parseInt(m.group(1));
        int jahr2 = Integer.parseInt(m.group(2));
        int hj = Integer.parseInt(m.group(3));
        if (hj == 1) {
            return new LocalDate[] {LocalDate.of(jahr1, 8, 1), LocalDate.of(jahr2, 1, 31)};
        }
        return new LocalDate[] {LocalDate.of(jahr2, 2, 1), LocalDate.of(jahr2, 7, 31)};
    }
}
