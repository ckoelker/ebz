package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Nachricht;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.AgentPort;

/**
 * {@link AgentPort}-Adapter: ruft den {@link CoPilot}-AiService (langchain4j) für einen HITL-Antwort­entwurf.
 * Liest den Thread-Verlauf direkt aus dem Modell (Adapter→model erlaubt; Adapter kennt weder Web noch
 * Service, per ArchUnit durchgesetzt). <b>Graceful degradation</b>: ohne erreichbares LLM (z. B. kein
 * {@code OPENAI_API_KEY}) liefert er einen neutralen Vorlagen-Entwurf, statt den Aufruf zu kippen — der
 * Showcase bleibt ohne Schlüssel bedienbar.
 */
@ApplicationScoped
public class CoPilotAgentAdapter implements AgentPort {

    private static final Logger LOG = Logger.getLogger(CoPilotAgentAdapter.class);
    private static final int MAX_NACHRICHTEN = 20;

    @Inject
    CoPilot coPilot;

    @Override
    @Transactional
    public String entwirfAntwort(Long konversationId) {
        List<Nachricht> verlauf = Nachricht.list(
                "konversation.id = ?1 order by zeitpunkt asc", konversationId);
        if (verlauf.isEmpty()) {
            return "";
        }
        String transcript = transcript(verlauf);
        try {
            CoPilot.Entwurf e = coPilot.entwirf(transcript);
            if (e != null && e.text() != null && !e.text().isBlank()) {
                return e.text().trim();
            }
        } catch (RuntimeException ex) {
            LOG.warnf("Co-Pilot-Entwurf nicht verfügbar (Fallback-Vorlage): %s", ex.getMessage());
        }
        return fallback();
    }

    /** Verlauf als Klartext (Rolle + entschärfter Text) für den Prompt. */
    private static String transcript(List<Nachricht> verlauf) {
        StringBuilder sb = new StringBuilder();
        for (Nachricht n : verlauf.stream().limit(MAX_NACHRICHTEN).toList()) {
            String rolle = switch (n.absenderTyp) {
                case MITARBEITER -> "Sachbearbeiter";
                case PERSON -> "Kunde";
                case AGENT -> "Assistent";
            };
            String text = n.inhaltHtml == null ? "" :
                    n.inhaltHtml.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            sb.append(rolle).append(": ").append(text).append('\n');
        }
        return sb.toString();
    }

    private static String fallback() {
        return "Vielen Dank für Ihre Nachricht. Wir prüfen Ihr Anliegen und melden uns kurzfristig "
                + "mit einer Antwort bei Ihnen. Für Rückfragen stehen wir gern zur Verfügung.";
    }
}
