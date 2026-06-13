package de.netzfactor.ebz.controlling.integration.rechnung;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdDaten;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.Verkaeufer;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

/**
 * Plain-JUnit (kein Quarkus-Runtime): {@code PdfA3Generator}/{@code ZugferdService} haben keine
 * Injection-Abhängigkeiten und lassen sich direkt instanziieren. Beweist die Kette
 * PDFBox → PDF/A-3 → Mustang ZUGFeRD (EN16931) → ZUGFeRDValidator — und damit das harte Validator-Tor
 * (§4b) für eine steuerbefreite Berufsschul-Sammelrechnung (Typ 380) UND einen Storno-Korrekturbeleg
 * (Typ 381 mit Bezug auf die Originalrechnung).
 */
class ZugferdPipelineTest {

    private static Verkaeufer ebz() {
        return new Verkaeufer("EBZ Bildungs- und Forschungszentrum gGmbH", "Musterstraße 1", "45657",
                "Recklinghausen", "DE", "DE123456789", "rechnung@ebz.example", "Rechnungswesen EBZ",
                "+49 2361 0000-0", "Sparkasse Vest Recklinghausen", "DE02426501500000123456", "WELADED1REK");
    }

    private static RechnungZugferdDaten.Empfaenger empf() {
        return new RechnungZugferdDaten.Empfaenger("BS-RE-0001", "Muster Ausbildungs GmbH",
                "Hauptstr. 1", "45657", "Recklinghausen", "DE", null, "buchhaltung@muster-ausbildung.example");
    }

    @Test
    void erzeugtValidesZugferd_steuerbefreit() throws Exception {
        var daten = new RechnungZugferdDaten("RE-BS-00001", LocalDate.of(2026, 6, 13), 14, ebz(), empf(),
                "Schuljahr 2025/2026, 1. Halbjahr",
                "Umsatzsteuerbefreit nach § 4 UStG (Bildungsleistung)", "Rechnung",
                false, null, null, LocalDate.of(2025, 8, 1), LocalDate.of(2026, 1, 31),
                List.of(new RechnungZugferdDaten.Position("Unterricht (Schuljahr 2025/2026, 1. Halbjahr) – Anna Azubi", 150000),
                        new RechnungZugferdDaten.Position("Übernachtung Doppelzimmer (Schuljahr 2025/2026, 1. Halbjahr) – Anna Azubi", 130000),
                        new RechnungZugferdDaten.Position("Unterricht (Schuljahr 2025/2026, 1. Halbjahr) – Ben Azubi", 150000)));

        ZugferdService.Ergebnis erg = new ZugferdService().erzeugeUndValidiere(daten);
        schreibeUndPruefe(erg, "rechnung-zugferd.pdf");
    }

    @Test
    void erzeugtValidesZugferd_storno_typ381() throws Exception {
        // Korrekturbeleg: gespiegelte (negative) Positionsbeträge wie im RechnungService; die Pipeline
        // führt sie für Typ 381 positiv. Pflicht-Bezug auf die Originalrechnung (BT-25/26).
        var daten = new RechnungZugferdDaten("ST-BS-00001", LocalDate.of(2026, 6, 14), 14, ebz(), empf(),
                "Schuljahr 2025/2026, 1. Halbjahr – Vollstorno",
                "Umsatzsteuerbefreit nach § 4 UStG (Bildungsleistung)", "Storno",
                true, "RE-BS-00001", LocalDate.of(2026, 6, 13), null, null,
                List.of(new RechnungZugferdDaten.Position("Storno: Unterricht – Anna Azubi", -150000),
                        new RechnungZugferdDaten.Position("Storno: Übernachtung Doppelzimmer – Anna Azubi", -130000)));

        ZugferdService.Ergebnis erg = new ZugferdService().erzeugeUndValidiere(daten);
        schreibeUndPruefe(erg, "storno-zugferd.pdf");
    }

    private static void schreibeUndPruefe(ZugferdService.Ergebnis erg, String dateiname) throws Exception {
        Path out = Path.of("target", dateiname);
        Files.createDirectories(out.getParent());
        Files.write(out, erg.pdf());
        System.out.println("ZUGFeRD-PDF: " + out.toAbsolutePath() + " · valide=" + erg.valide());
        if (!erg.valide()) {
            System.out.println("=== Validator-Report ===\n" + erg.report());
        }
        assertTrue(erg.valide(), "ZUGFeRD/PDF-A muss valide sein — siehe Report");
        assertTrue(erg.pdf().length > 3000, "PDF nicht leer");
    }
}
