package de.netzfactor.ebz.controlling.integration.rechnung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.netzfactor.ebz.controlling.integration.rechnung.datev.Buchungssatz;
import de.netzfactor.ebz.controlling.integration.rechnung.datev.ExtfBuchungsstapel;

/**
 * Plain-Test des EXTF-Renderers (R4): korrekter Format-Kopf (EXTF/700/21/Buchungsstapel),
 * Spaltenüberschriften und Datenzeilen (deutsches Dezimalkomma, S/H, Konto/Gegenkonto, Belegfeld 1).
 */
class ExtfBuchungsstapelTest {

    @Test
    void render_hatExtfKopf_spaltenkopf_undDatenzeilen() {
        var kopf = new ExtfBuchungsstapel.Kopf("1001", "1", "20260101", 4,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Buchungsstapel SKR03");
        var saetze = List.of(
                new Buchungssatz(280000, "S", "10001", "8120", "", LocalDate.of(2026, 6, 13),
                        "RE-BS-00001", "Rechnung RE-BS-00001"),
                new Buchungssatz(280000, "H", "10001", "8120", "", LocalDate.of(2026, 6, 14),
                        "ST-BS-00001", "Storno ST-BS-00001"));

        String csv = ExtfBuchungsstapel.render(saetze, kopf);
        String[] z = csv.split("\r\n");

        assertEquals(4, z.length, "Kopf + Spaltenkopf + 2 Datenzeilen");
        assertTrue(z[0].startsWith("\"EXTF\";700;21;\"Buchungsstapel\""), "EXTF-Formatkopf: " + z[0]);
        assertTrue(z[0].contains(";1001;1;20260101;4;20260101;20261231;"), "Berater/Mandant/WJ/Zeitraum");
        assertTrue(z[1].startsWith("Umsatz (ohne Soll/Haben-Kz);Soll/Haben-Kennzeichen"), "Spaltenkopf");
        assertTrue(z[2].startsWith("2800,00;\"S\";\"EUR\""), "Datenzeile 1: " + z[2]);
        assertTrue(z[2].contains(";10001;8120;"), "Konto;Gegenkonto;BU");
        assertTrue(z[2].contains("\"RE-BS-00001\""), "Belegfeld 1");
        assertTrue(z[3].startsWith("2800,00;\"H\""), "Storno = Haben: " + z[3]);
    }
}
