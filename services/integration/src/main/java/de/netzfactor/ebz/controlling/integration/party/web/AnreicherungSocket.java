package de.netzfactor.ebz.controlling.integration.party.web;

import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.party.service.AnreicherungService;
import de.netzfactor.ebz.controlling.integration.party.service.AnreicherungService.AnreicherungsAnfrage;
import de.netzfactor.ebz.controlling.integration.party.service.AnreicherungService.AnreicherungsEvent;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;

/**
 * WebSocket der Firmen-Online-Anreicherung (Plan A15, „Daten online ziehen"): nimmt eine JSON-Anfrage
 * {@link AnreicherungsAnfrage} (Name/Website/USt-IdNr.) entgegen und streamt den Fortschritt
 * ({@link AnreicherungsEvent}) zurück — jeder {@code Multi}-Eintrag wird als eigene JSON-Textnachricht
 * gesendet (Jackson). Die fachliche Orchestrierung steckt im {@link AnreicherungService}.
 *
 * <p>Lese-Vorgang ohne DB-Schreiben → derzeit offen (wie die übrigen CRM-Reads); RBAC-Feinschliff
 * ({@code crm-pflege} über den WS-Handshake) folgt mit der Keycloak-Realm-Konfiguration.
 */
@WebSocket(path = "/ws/crm/anreicherung")
public class AnreicherungSocket {

    @Inject
    AnreicherungService anreicherung;

    @OnTextMessage
    public Multi<AnreicherungsEvent> onMessage(AnreicherungsAnfrage anfrage) {
        return anreicherung.anreichern(anfrage);
    }
}
