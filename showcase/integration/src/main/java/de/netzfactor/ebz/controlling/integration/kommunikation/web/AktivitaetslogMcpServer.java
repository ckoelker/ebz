package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;

/**
 * <b>MCP-Demo</b> (Plan K4, „Zukunfts-Beispiel"): ein hauseigener <b>Model-Context-Protocol-Server</b>
 * (HTTP/SSE-Transport, {@code quarkus-mcp-server-http}), der externen KI-Agenten den EBZ-Aktivitätslog
 * als standardisiertes <i>Tool</i> anbietet — statt eines proprietären Adapters je Agent. Der Showcase-Wert:
 * dieselbe DSGVO-Disziplin wie im Portal greift auch hier, weil das Tool ausschließlich über die
 * veröffentlichte Fassade {@link KommunikationApi#ereignisseFuer} liest, die nur die <b>sichtbare</b>
 * Projektion (Allowlist {@code sichtbar=true}) zurückgibt — interne Vermerke bleiben dem Agenten verborgen.
 * <p>
 * Liegt in {@code web} (Protokoll-/Transportgrenze wie eine REST-Resource → Fassade erlaubt, kein Adapter-
 * Zugriff, party-frei: nur die {@code Long}-Person-ID). <b>Im Showcase offen</b> wie die Bestands-Sockets;
 * produktiv gehört der MCP-Endpunkt hinter einen Keycloak-Service-Account des Agenten + Scope-Prüfung
 * (vgl. Secure-MCP), damit die Person-ID nicht frei abfragbar ist.
 */
@ApplicationScoped
public class AktivitaetslogMcpServer {

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Inject
    KommunikationApi kommunikation;

    @Tool(name = "ebz_aktivitaetslog", description = """
        Liefert den für eine Person SICHTBAREN EBZ-Aktivitätslog (Benachrichtigungen/Ereignisse aus dem
        Portal-Zeitstrahl). Es werden ausschließlich DSGVO-freigegebene Einträge zurückgegeben; interne
        Vermerke bleiben grundsätzlich unsichtbar. Eingabe: die interne EBZ-Person-ID.""")
    @Transactional
    public String aktivitaetslog(
            @ToolArg(description = "Interne Person-ID im EBZ-Party-Kern") long personId) {
        List<PersonEreignis> log = kommunikation.ereignisseFuer(personId);
        if (log.isEmpty()) {
            return "Keine sichtbaren Aktivitäten für Person " + personId + ".";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(log.size()).append(" sichtbare Aktivität(en) für Person ").append(personId).append(":\n");
        for (PersonEreignis pe : log) {
            sb.append("- [").append(pe.ereignisTyp.kategorie).append("] ").append(pe.betreff)
                    .append(" (").append(pe.zeitpunkt.format(ZEIT)).append(')');
            if (pe.bestaetigungErforderlich && pe.bestaetigtAm == null) {
                sb.append(" — Kenntnisnahme ausstehend");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
