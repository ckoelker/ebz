package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.smallrye.common.annotation.Identifier;

/**
 * EXTF-CSV-Weg (Brücke/Fallback): erzeugt aus den Buchungssätzen den DATEV-Buchungsstapel als
 * EXTF-Datei. Dieser Weg liefert das importierbare Artefakt direkt zurück (manueller/automatisierter
 * Import in DATEV) — die Brücke aus der R0-Klärung, bis der DATEV-Datenservice angebunden ist.
 */
@ApplicationScoped
@Identifier("extf")
public class ExtfCsvUebergabe implements DatevUebergabe {

    private static final Logger LOG = Logger.getLogger(ExtfCsvUebergabe.class);

    @Override
    public Protokoll uebergeben(List<Buchungssatz> saetze, ExtfBuchungsstapel.Kopf kopf) {
        byte[] artefakt = ExtfBuchungsstapel.bytes(saetze, kopf);
        String dateiname = "EXTF_Buchungsstapel_" + kopf.von() + "_" + kopf.bis() + ".csv";
        LOG.infof("DATEV EXTF-Buchungsstapel erzeugt: %d Buchungen, %d Bytes (%s)",
                saetze.size(), artefakt.length, dateiname);
        return new Protokoll("EXTF-CSV", dateiname, saetze.size(), artefakt,
                "DATEV-Import-Brücke (Buchungsstapel, EXTF-Format).");
    }
}
