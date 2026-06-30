package de.netzfactor.ebz.controlling.integration.kommunikation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K4 MCP-Demo: der hauseigene MCP-Server ({@code AktivitaetslogMcpServer}) bietet externen KI-Agenten den
 * Aktivitätslog als Tool an. Geprüft über das MCP-Protokoll (McpAssured, SSE-Transport): das Tool ist im
 * {@code tools/list} sichtbar und liefert beim {@code tools/call} <b>nur die DSGVO-freigegebenen</b>
 * (sichtbaren) Einträge — ein bewusst auf {@code sichtbar=false} gesetzter interner Vermerk bleibt dem
 * Agenten verborgen. Eindeutige Schlüssel/Betreffe pro Lauf wegen der persistenten Showcase-DB.
 */
@QuarkusTest
class KommunikationMcpTest {

    @Inject
    PartyHoheitService party;

    @Inject
    KommunikationApi kommunikation;

    @Test
    void mcpTool_liefertNurSichtbarenLog() {
        long n = System.nanoTime();
        Person p = party.selbstRegistrieren("mcp-sub-" + n, "mcp-" + n + "@ebz.de", "Mona MCP");

        String sichtbar = "MCP-sichtbar " + n;
        String intern = "MCP-intern " + n;

        // Ein sichtbares Ereignis über die Fassade …
        kommunikation.protokolliere(KommunikationsEreignis.ohneKontext(
                EreignisTyp.SYSTEM_HINWEIS, p.id, sichtbar, "mcp:sichtbar:" + n));
        // … und ein bewusst unsichtbarer interner Vermerk direkt persistiert (Allowlist-Gegenprobe).
        QuarkusTransaction.requiringNew().run(() -> {
            PersonEreignis pe = new PersonEreignis();
            pe.empfaengerPersonId = p.id;
            pe.ereignisTyp = EreignisTyp.INTERNER_VERMERK;
            pe.betreff = intern;
            pe.kontextTyp = KontextTyp.KEINER;
            pe.sichtbar = false;
            pe.persist();
        });

        McpSseTestClient client = McpAssured.newConnectedSseClient();
        try {
            client.when()
                    .toolsList(page -> assertNotNull(page.findByName("ebz_aktivitaetslog"),
                            "MCP-Tool ebz_aktivitaetslog im tools/list"))
                    .thenAssertResults();

            client.when()
                    .toolsCall("ebz_aktivitaetslog", Map.of("personId", p.id), response -> {
                        assertFalse(response.isError(), "Tool-Aufruf ohne Fehler");
                        String text = response.firstContent().asText().text();
                        assertTrue(text.contains(sichtbar), "sichtbares Ereignis im MCP-Ergebnis: " + text);
                        assertFalse(text.contains(intern), "interner Vermerk bleibt verborgen: " + text);
                    })
                    .thenAssertResults();
        } finally {
            client.disconnect();
        }
    }
}
