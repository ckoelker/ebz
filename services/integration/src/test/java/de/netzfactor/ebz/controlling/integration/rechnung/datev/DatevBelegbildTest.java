package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * D2: Belegbild-Upload in den DATEV-Belegbilderservice ({@code accounting:documents}) gegen den WireMock-
 * Stub. DB-frei — ein Dummy-PDF wird übertragen; geprüft werden Access-Token-Holung, Pflicht-Header
 * (der PUT-Stub matcht nur mit ihnen), die selbst erzeugte RFC4122-GUID und der Belegbezug.
 */
@QuarkusTest
@WithTestResource(DatevWireMockResource.class)
class DatevBelegbildTest {

    @Inject
    DatevBelegbildService belegbild;

    @Test
    void belegbild_wirdHochgeladen_mitRfc4122Guid() {
        byte[] pdf = "%PDF-1.7 EBZ-Testbeleg".getBytes(StandardCharsets.UTF_8);

        DatevBelegbildService.Beleg b = belegbild.uebertrage(pdf, "RE-CLOUD-1");

        assertEquals("RE-CLOUD-1", b.belegnummer());
        assertNotNull(b.documentGuid(), "DATEV-Dokument-GUID");
        assertTrue(b.documentGuid().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
                "GUID im 8-4-4-4-12-Format: " + b.documentGuid());
    }
}
