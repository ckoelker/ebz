package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * D5: Peppol-/SmartTransfer-Transport (Showcase-Mock). DB-frei — ein Dummy-ZUGFeRD wird übertragen;
 * geprüft werden Netzwerk, Empfänger-ID, eine vergebene Übertragungs-ID und die Leeres-Dokument-Sperre.
 */
@QuarkusTest
class PeppolVersandTest {

    @Inject
    PeppolVersand peppol;

    @Test
    void versende_liefertUebertragungsQuittung() {
        byte[] pdf = "%PDF-1.7 EBZ-Testbeleg".getBytes(StandardCharsets.UTF_8);

        PeppolVersand.Quittung q = peppol.versende(pdf, "9930:DE123456789", "RE-1");

        assertEquals("Peppol/TRAFFIQX", q.netzwerk());
        assertEquals("9930:DE123456789", q.empfaengerId());
        assertNotNull(q.uebertragungsId(), "Übertragungs-ID");
        assertTrue(q.uebertragungsId().startsWith("TRX-"), "ID: " + q.uebertragungsId());
    }

    @Test
    void leeresDokument_wirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> peppol.versende(new byte[0], "9930:DE1", "RE-2"));
    }
}
