package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.smallrye.common.annotation.Identifier;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;

/**
 * Fassade der DATEV-Übergabe (R4): wählt festgeschriebene Belege eines Zeitraums, erzeugt die
 * Buchungssätze und reicht sie an den per {@code datev.modus} aktiven {@link DatevUebergabe}-Weg
 * (EXTF-CSV-Brücke oder DATEV-Cloud-Mock). Der EXTF-Export ist zusätzlich direkt abrufbar.
 */
@ApplicationScoped
public class DatevService {

    @Inject
    BuchungssatzService buchungen;

    @Inject
    DatevKonten konten;

    @Inject
    @Identifier("extf")
    DatevUebergabe extf;

    @Inject
    @Identifier("cloud-mock")
    DatevUebergabe cloudMock;

    @Inject
    @Identifier("cloud")
    DatevUebergabe cloud;

    @Inject
    de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur prozess;

    /** Festgeschriebene Belege (keine Entwürfe) im Zeitraum, optional je Bereich. */
    public List<Rechnung> belege(LocalDate von, LocalDate bis, Bereich bereich) {
        if (bereich != null) {
            return Rechnung.list("status <> ?1 and ausstellungsdatum >= ?2 and ausstellungsdatum <= ?3 "
                    + "and bereich = ?4 order by ausstellungsdatum, id",
                    RechnungStatus.ENTWURF, von, bis, bereich);
        }
        return Rechnung.list("status <> ?1 and ausstellungsdatum >= ?2 and ausstellungsdatum <= ?3 "
                + "order by ausstellungsdatum, id", RechnungStatus.ENTWURF, von, bis);
    }

    public List<Buchungssatz> buchungssaetze(List<Rechnung> belege) {
        return buchungen.fuerBelege(belege);
    }

    public byte[] extfCsv(List<Rechnung> belege, LocalDate von, LocalDate bis) {
        return ExtfBuchungsstapel.bytes(buchungssaetze(belege), kopf(von, bis));
    }

    /** Übergibt an den aktiven Weg (datev.modus): {@code cloud} (Buchungsdatenservice), {@code cloud-mock} sonst EXTF-CSV. */
    public DatevUebergabe.Protokoll uebergeben(List<Rechnung> belege, LocalDate von, LocalDate bis) {
        DatevUebergabe weg = switch (konten.modus() == null ? "extf" : konten.modus().toLowerCase()) {
            case "cloud" -> cloud;
            case "cloud-mock" -> cloudMock;
            default -> extf;
        };
        DatevUebergabe.Protokoll p = weg.uebergeben(buchungssaetze(belege), kopf(von, bis));
        prozess.schritt("DATEV-Buchungsstapel übergeben", de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur.EBZ,
                de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.System.DATEV,
                de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ.SERVICE_TASK,
                de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase.DATEV_EXPORT);
        return p;
    }

    private ExtfBuchungsstapel.Kopf kopf(LocalDate von, LocalDate bis) {
        return new ExtfBuchungsstapel.Kopf(konten.beraternummer(), konten.mandantennummer(),
                konten.wirtschaftsjahrBeginn(), konten.sachkontenLaenge(), von, bis,
                "Buchungsstapel " + konten.skr());
    }
}
