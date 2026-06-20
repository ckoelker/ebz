package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import java.time.LocalDateTime;
import java.util.List;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Nachricht;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.TeilnehmerTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.FaqAgentPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.StaffIdentitaetsPort;

/**
 * Geteilte Lese-/Schreib-DTOs der Thread-Endpunkte (Portal {@code /kommunikation/portal} und Admin
 * {@code /kommunikation/admin}) — bewusst gebündelt ([[lean-file-structure]]). DTOs an der Grenze (keine
 * Entities); die Namensauflösung Mitarbeiter/Person läuft über den {@link StaffIdentitaetsPort} (ACL).
 */
public final class KommunikationViews {

    private KommunikationViews() {
    }

    /** Eintrag in der Thread-Liste (Portal/Admin): Kopf + Vorschau der letzten Nachricht + Ungelesen-Flag. */
    public record KonversationView(Long id, String typ, String betreff, String status, String kontextTyp,
            Long kontextId, String partner, String letzteVorschau, LocalDateTime letzteZeit, boolean ungelesen) {
    }

    /** Eine Nachricht im Thread; {@code kiGeneriert} treibt die EU-AI-Act-Kennzeichnung in der UI. */
    public record NachrichtView(Long id, String absenderTyp, String absender, boolean kiGeneriert,
            String inhaltHtml, LocalDateTime zeitpunkt) {
    }

    /** Nachricht senden / Vorgang eröffnen. {@code personId}/{@code betreff}/{@code kontext*} nur beim Eröffnen. */
    public record SendenDto(Long personId, String betreff, String inhaltHtml, String kontextTyp, Long kontextId) {
    }

    // ───────────────────────── Mapping ─────────────────────────

    /** Anzeigename des Absenders einer Nachricht je Typ (Mitarbeiter/Person/KI-Agent). */
    public static String absenderName(Nachricht n, StaffIdentitaetsPort staff) {
        return switch (n.absenderTyp) {
            case MITARBEITER -> staff.mitarbeiterName(n.mitarbeiterId);
            case PERSON -> staff.personName(n.personId);
            case AGENT -> agentName(n.agentKennung);
        };
    }

    /** Anzeigename eines KI-Agenten anhand seiner Kennung (FAQ-Bot vs. allgemeiner Assistent). */
    public static String agentName(String agentKennung) {
        return FaqAgentPort.FAQ_BOT.equals(agentKennung) ? FaqAgentPort.FAQ_BOT_NAME : "KI-Assistent";
    }

    public static NachrichtView toView(Nachricht n, StaffIdentitaetsPort staff) {
        return new NachrichtView(n.id, n.absenderTyp.name(), absenderName(n, staff), n.kiGeneriert,
                n.inhaltHtml, n.zeitpunkt);
    }

    public static List<NachrichtView> toViews(List<Nachricht> ns, StaffIdentitaetsPort staff) {
        return ns.stream().map(n -> toView(n, staff)).toList();
    }

    /** Kurze Klartext-Vorschau aus dem Nachrichten-HTML (Tags entfernt, gekürzt). */
    public static String vorschau(Nachricht n) {
        if (n == null || n.inhaltHtml == null) {
            return "";
        }
        String text = n.inhaltHtml.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 119) + "…" : text;
    }

    /**
     * Partner-Anzeige für die Personen-Listenzeile, je Thread-Art: Admin-Vorgang → Mitarbeitername,
     * Bot-Beratung → KI-Studienberatung, Direkt-Chat → die <i>andere</i> Person (≠ {@code eigenePersonId}).
     */
    public static String partnerFuerPerson(Konversation k, Long eigenePersonId, StaffIdentitaetsPort staff) {
        Konversation.Teilnehmer ma = Konversation.Teilnehmer.find(
                "konversation.id = ?1 and teilnehmerTyp = ?2", k.id, TeilnehmerTyp.MITARBEITER).firstResult();
        if (ma != null) {
            return staff.mitarbeiterName(ma.mitarbeiterId);
        }
        Konversation.Teilnehmer agent = Konversation.Teilnehmer.find(
                "konversation.id = ?1 and teilnehmerTyp = ?2", k.id, TeilnehmerTyp.AGENT).firstResult();
        if (agent != null) {
            return agentName(agent.agentKennung);
        }
        Konversation.Teilnehmer andere = Konversation.Teilnehmer.find(
                "konversation.id = ?1 and teilnehmerTyp = ?2 and personId <> ?3",
                k.id, TeilnehmerTyp.PERSON, eigenePersonId).firstResult();
        return andere == null ? "EBZ-Team" : staff.personName(andere.personId);
    }

    public static String partnerFuerStaff(Konversation k, StaffIdentitaetsPort staff) {
        Konversation.Teilnehmer t = Konversation.Teilnehmer.find(
                "konversation.id = ?1 and teilnehmerTyp = ?2", k.id, TeilnehmerTyp.PERSON).firstResult();
        return t == null ? "Unbekannt" : staff.personName(t.personId);
    }
}
