package de.netzfactor.ebz.controlling.integration.kommunikation.spi;

import java.util.List;
import java.util.Set;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * <b>Split-Readiness-Verträge</b> des Kommunikations-Moduls: die ausgehenden Ports, die im Showcase als
 * In-Process-Interfaces bestehen und beim späteren Microservice-Schnitt zu Netz-Clients werden, ohne den
 * Kern anzufassen ({@code KanalVersand} ist der aktive, separate Port; hier die übrigen — je Verantwortung
 * einer). Bewusst gebündelt (eine Datei) statt verstreut: die meisten werden erst in späteren Phasen
 * implementiert. Die ArchUnit-Fitness-Functions stellen sicher, dass Adapter nur am Port hängen und der
 * Kern volatile Module ({@code rechnung}/{@code bildung}) nur über Events/IDs erreicht.
 */
public final class Ports {

    private Ports() {
    }

    /** Liefert Templates (Qute) für Betreff/Inhalt je {@link EreignisTyp} (versionierte Registry, ab K1). */
    public interface TemplatePort {
        /** Gerenderter Betreff/Text für einen Ereignistyp mit Variablen. */
        String render(EreignisTyp typ, java.util.Map<String, Object> variablen);
    }

    /** Erreichbarkeit + Consent (ACL→party): welche Kanäle für die Person zulässig sind + Versanddaten. */
    public interface ErreichbarkeitPort {
        /**
         * Effektive Push-Kanäle für Person×Ereignis (Default-Kanäle ∩ Consent, {@code werbesperre}/
         * {@code auskunftssperre} überstimmen nicht-transaktionale Kanäle). <b>Leeres Set ⇒ Person
         * unbekannt</b> (Aufrufer verwirft das Ereignis); eine bekannte Person hat bei sichtbarem Typ
         * stets mindestens {@code PORTAL}.
         */
        Set<Kanal> erlaubteKanaele(Long personId, EreignisTyp typ);

        /** Primäre E-Mail-Adresse der Person (für den E-Mail-Adapter); {@code null} wenn keine bekannt. */
        String primaerEmail(Long personId);

        /** Briefanrede der Person (für Template/E-Mail-Body); neutraler Fallback, nie {@code null}. */
        String anrede(Long personId);
    }

    /** Löst abgeleitete {@code Personengruppe}n zum Sendezeitpunkt zu Empfängern auf (ACL→bildung/party, ab K3). */
    public interface EmpfaengerAufloesung {
        /** Person-IDs der Gruppe (manuell gepflegt oder dynamisch aus Anmeldung/Mitgliedschaft). */
        List<Long> mitglieder(Long gruppeId);
    }

    /** Auflösung Token-{@code sub} ↔ Party-Identität (ACL→party/keycloak) für die Sicht-Autorisierung (ab K1). */
    public interface IdentitaetsPort {
        /** Person-ID zum Keycloak-{@code sub} (oder {@code null}). */
        Long personIdFuerSub(String keycloakSub);
    }

    /** Echtzeit-Push (SSE ab K1, WebSocket ab K2); kapselt den Transport (später Gateway-Tier + Backplane). */
    public interface RealtimePort {
        /** Signalisiert der Person ein neues Inbox-Ereignis (Badge/Feed-Update). */
        void signalisiere(Long personId, String ereignisRef);
    }

    /** KI-Agent als Teilnehmer (langchain4j/HITL/MCP, ab K2): Antwortentwurf bzw. autonome Antwort. */
    public interface AgentPort {
        /** Entwurf einer Antwort für eine Konversation (HITL: Freigabe durch Sachbearbeiter). */
        String entwirfAntwort(Long konversationId);
    }

    /** Übergabe einer {@code Zustellung} an die asynchrone Auslieferung (Outbox-Enqueue); Impl: ZustellService. */
    public interface DispatchPort {
        /** Reiht eine Zustellung zur garantierten async Auslieferung ein (idempotent). */
        void enqueue(Long zustellungId);
    }
}
