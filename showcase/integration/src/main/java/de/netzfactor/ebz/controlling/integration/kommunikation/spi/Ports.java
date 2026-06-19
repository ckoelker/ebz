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

    /** Liefert gerenderte Templates (Qute) für Betreff/Inhalt je {@link EreignisTyp} (versionierte Registry). */
    public interface TemplatePort {
        /** Gerendertes Ergebnis: Betreff + Klartext-Body. */
        record Gerendert(String betreff, String body) {
        }

        /** Rendert Betreff + Body für einen Ereignistyp mit Variablen (z. B. anrede, betreff). */
        Gerendert render(EreignisTyp typ, java.util.Map<String, Object> variablen);
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

    /**
     * Auflösung Token-{@code sub} ↔ <b>Mitarbeiter</b>-Identität (Realm {@code ebz-staff}) für die
     * Admin-Seite der zweiseitigen Threads (ab K2, Cross-Realm). Getrennt vom personenseitigen
     * {@link IdentitaetsPort} (anderer Realm, anderer Tenant).
     */
    public interface StaffIdentitaetsPort {
        /** Mitarbeiter-ID zum Keycloak-{@code sub} (oder {@code null}). */
        Long mitarbeiterIdFuerSub(String keycloakSub);

        /** Anzeigename des Mitarbeiters (für die Nachrichten-Sicht); neutraler Fallback, nie {@code null}. */
        String mitarbeiterName(Long mitarbeiterId);

        /** Anzeigename einer Person (für die Admin-Nachrichten-Sicht); neutraler Fallback, nie {@code null}. */
        String personName(Long personId);
    }

    /**
     * Spiegelt eine vom Mitarbeiter gesendete Thread-Nachricht zusätzlich ins <b>staff-interne CRM-Log</b>
     * ({@code party.Aktivitaet}) — „kein Doppelsystem" (K2): die Konversation ist die Live-Kommunikation,
     * das CRM-Kontaktlog bleibt die kuratierte Historie. ACL→party (nur im Adapter); best effort.
     */
    public interface CrmSpiegelPort {
        /** Schreibt eine ausgehende {@code Aktivitaet} (Mitarbeiter→Person) als CRM-Kontaktnachweis. */
        void spiegleStaffNachricht(Long mitarbeiterId, Long personId, String betreff, String inhaltText);
    }

    /** Echtzeit-Push (SSE ab K1, WebSocket ab K2); kapselt den Transport (später Gateway-Tier + Backplane). */
    public interface RealtimePort {
        /** Signalisiert der Person ein neues Inbox-Ereignis (Badge/Feed-Update); best effort, wirft nie. */
        void signalisiere(Long personId, String ereignisRef);

        /** Live-Strom der Inbox-Signale dieser Person (SSE-Abonnement im Portal). */
        io.smallrye.mutiny.Multi<String> stream(Long personId);
    }

    /**
     * Interaktiver Thread-Transport (WebSocket ab K2): signalisiert den in einem Thread verbundenen
     * Teilnehmern, dass eine neue Nachricht vorliegt. Bewusst <b>nur ein Signal</b> (Konversations-ID),
     * kein Inhalt über den Socket — der Inhalt wird über das autorisierte REST nachgeladen (der WS ist
     * im Showcase wie die Bestands-Sockets noch offen; RBAC am Handshake folgt mit der Realm-Konfiguration).
     */
    public interface ThreadRealtimePort {
        /** Endpoint-Id des Thread-WebSockets (von Socket und Adapter geteilt, damit beide ohne Web↔Adapter-Kopplung auskommen). */
        String ENDPOINT_ID = "komm-thread";

        /** Signalisiert „neue Nachricht im Thread" an die verbundenen Clients dieses Threads; best effort, wirft nie. */
        void signalisiereThread(Long konversationId);
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
