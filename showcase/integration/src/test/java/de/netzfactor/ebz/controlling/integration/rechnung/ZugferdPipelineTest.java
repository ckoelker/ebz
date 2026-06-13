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
 * (§4b) für eine steuerbefreite Berufsschul-Sammelrechnung.
 */
class ZugferdPipelineTest {

    private static Verkaeufer ebz() {
        return new Verkaeufer("EBZ Bildungs- und Forschungszentrum gGmbH", "Musterstraße 1", "45657",
                "Recklinghausen", "DE", "DE123456789", "rechnung@ebz.example", "Rechnungswesen EBZ",
                "+49 2361 0000-0", "Sparkasse Vest Recklinghausen", "DE02426501500000123456", "WELADED1REK");
    }

    @Test
    void erzeugtValidesZugferd_steuerbefreit() throws Exception {
        var empf = new RechnungZugferdDaten.Empfaenger("BS-RE-0001", "Muster Ausbildungs GmbH",
                "Hauptstr. 1", "45657", "Recklinghausen", "DE", null);
        var daten = new RechnungZugferdDaten("RE-BS-00001", LocalDate.of(2026, 6, 13), 14, ebz(), empf,
                "Schuljahr 2025/2026, 1. Halbjahr",
                "Umsatzsteuerbefreit nach § 4 UStG (Bildungsleistung)",
                List.of(new RechnungZugferdDaten.Position("Unterricht (Schuljahr 2025/2026, 1. Halbjahr) – Anna Azubi", 150000),
                        new RechnungZugferdDaten.Position("Übernachtung Doppelzimmer (Schuljahr 2025/2026, 1. Halbjahr) – Anna Azubi", 130000),
                        new RechnungZugferdDaten.Position("Unterricht (Schuljahr 2025/2026, 1. Halbjahr) – Ben Azubi", 150000)));

        ZugferdService.Ergebnis erg = new ZugferdService().erzeugeUndValidiere(daten);

        Path out = Path.of("target", "rechnung-zugferd.pdf");
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
