package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Nachricht;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.FaqAgentPort;

/**
 * {@link FaqAgentPort}-Adapter: ruft den {@link FaqBot}-AiService (langchain4j) für eine <b>autonome</b>
 * Antwort auf den bisherigen Bot-Thread-Verlauf. Liest den Verlauf direkt aus dem Modell (Adapter→model
 * erlaubt; Adapter kennt weder Web noch Service, per ArchUnit durchgesetzt). <b>Graceful degradation</b>:
 * ohne erreichbares LLM (z. B. kein {@code OPENAI_API_KEY}) liefert er eine neutrale Auskunft mit Verweis
 * auf das Service-Team, statt den Aufruf zu kippen — der Showcase bleibt ohne Schlüssel bedienbar.
 */
@ApplicationScoped
public class FaqAgentAdapter implements FaqAgentPort {

    private static final Logger LOG = Logger.getLogger(FaqAgentAdapter.class);
    private static final int MAX_NACHRICHTEN = 20;

    @Inject
    FaqBot faqBot;

    @Override
    @Transactional
    public String beantworteThread(Long konversationId) {
        List<Nachricht> verlauf = Nachricht.list(
                "konversation.id = ?1 order by zeitpunkt asc", konversationId);
        if (verlauf.isEmpty()) {
            return "";
        }
        try {
            FaqBot.Antwort a = faqBot.beantworte(transcript(verlauf));
            if (a != null && a.text() != null && !a.text().isBlank()) {
                return a.text().trim();
            }
        } catch (RuntimeException ex) {
            LOG.warnf("FAQ-Bot nicht verfügbar (Fallback-Auskunft): %s", ex.getMessage());
        }
        return fallback();
    }

    /** Verlauf als Klartext (Rolle + entschärfter Text) für den Prompt. */
    private static String transcript(List<Nachricht> verlauf) {
        StringBuilder sb = new StringBuilder();
        for (Nachricht n : verlauf.stream().limit(MAX_NACHRICHTEN).toList()) {
            String rolle = switch (n.absenderTyp) {
                case MITARBEITER -> "Mitarbeiter";
                case PERSON -> "Person";
                case AGENT -> "KI-Studienberatung";
            };
            String text = n.inhaltHtml == null ? "" :
                    n.inhaltHtml.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            sb.append(rolle).append(": ").append(text).append('\n');
        }
        return sb.toString();
    }

    private static String fallback() {
        return "Vielen Dank für Ihre Frage. Ich kann Ihnen dazu gerade keine gesicherte Auskunft geben. "
                + "Gern leite ich Ihr Anliegen an unser EBZ-Service-Team weiter, das sich bei Ihnen meldet.";
    }
}
