package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.netzfactor.ebz.controlling.integration.party.model.AnrufLog;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import io.quarkus.websockets.next.OpenConnections;

/**
 * CTI-Anbindung (Plan A13), <b>anbieter-neutral</b>: ein eingehender Anruf (z. B. vom TK-Gateway als
 * Webhook gemeldet) wird über die normalisierte {@link Kontaktpunkt#nummerE164 E.164-Nummer} gegen den
 * Bestand gematcht, als {@link AnrufLog} protokolliert und als JSON-Event an alle offenen CTI-WebSockets
 * der CRM-Cockpits <b>gebroadcastet</b> — so erscheint beim Mitarbeiter sofort ein „Anruf von …"-Toast
 * mit Sprung in den passenden Kontakt. Eine unbekannte Nummer bleibt offen (UI bietet Schnellanlage).
 *
 * <p>Der WebSocket ({@code /ws/crm/cti}, Endpoint-Id {@code crm-cti}) ist nur Transport; die fachliche
 * Auflösung steckt hier. Broadcast über {@link OpenConnections} (idiomatisch, kein eigener Session-Pool).
 */
@ApplicationScoped
public class CtiService {

    private static final Logger LOG = Logger.getLogger(CtiService.class);

    /** Endpoint-Id des CTI-WebSockets (siehe {@code CtiSocket}); Filter für den Broadcast. */
    public static final String ENDPOINT_ID = "crm-cti";

    @Inject
    OpenConnections connections;

    @Inject
    ObjectMapper mapper;

    /** Eingang eines simulierten/echten Anrufs (TK-Gateway-Webhook). */
    public record AnrufAnfrage(String nummerE164, String richtung) {
    }

    /** Das an die Cockpits gebroadcastete Anruf-Event (aufgelöster Kontakt oder „unbekannt"). */
    public record AnrufEvent(String nummerE164, boolean bekannt, Long personId, String personName,
            Long organisationId, String organisationName, String zeitpunkt) {
    }

    /** Matcht den Anruf, protokolliert ihn und broadcastet das Event an alle offenen CTI-Cockpits. */
    @Transactional
    public AnrufEvent simuliereAnruf(AnrufAnfrage anfrage) {
        String roh = anfrage == null ? null : anfrage.nummerE164();
        String e164 = normalisiere(roh);
        if (e164 == null) {
            throw new de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung(
                    "Bitte eine Telefonnummer angeben.");
        }

        // Exakter E.164-Match auf einen aktiven Telefon-Kontaktpunkt.
        Kontaktpunkt k = Kontaktpunkt.find("typ = ?1 and nummerE164 = ?2", Kontaktpunkt.Typ.TELEFON, e164)
                .firstResult();
        Person person = k == null ? null
                : (k.person != null ? k.person : (k.mitgliedschaft != null ? k.mitgliedschaft.person : null));
        Organisation org = k == null ? null
                : (k.organisation != null ? k.organisation
                        : (k.mitgliedschaft != null ? k.mitgliedschaft.organisation : null));

        AnrufLog log = new AnrufLog();
        log.nummerE164 = e164;
        log.richtung = "AUSGEHEND".equalsIgnoreCase(anfrage.richtung())
                ? AnrufLog.Richtung.AUSGEHEND : AnrufLog.Richtung.EINGEHEND;
        log.status = AnrufLog.Status.ANGENOMMEN;
        log.person = person;
        log.organisation = org;
        log.persist();

        AnrufEvent event = new AnrufEvent(e164, person != null || org != null,
                person == null ? null : person.id, person == null ? null : person.anzeigeName(),
                org == null ? null : org.id, org == null ? null : org.name,
                log.zeitpunkt.toString());
        broadcast(event);
        return event;
    }

    private void broadcast(AnrufEvent event) {
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.warnf("CTI-Event nicht serialisierbar: %s", e.getMessage());
            return;
        }
        connections.findByEndpointId(ENDPOINT_ID).forEach(c -> {
            if (c.isOpen()) {
                c.sendTextAndAwait(json);
            }
        });
    }

    /** E.164-Normalisierung: Ziffern behalten, führendes {@code +} bewahren, Leer-/Trennzeichen entfernen. */
    private static String normalisiere(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        boolean plus = t.startsWith("+");
        String ziffern = t.replaceAll("\\D", "");
        if (ziffern.isEmpty()) {
            return null;
        }
        return (plus ? "+" : "") + ziffern;
    }
}
