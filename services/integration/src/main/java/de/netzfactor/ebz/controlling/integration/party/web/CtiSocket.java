package de.netzfactor.ebz.controlling.integration.party.web;

import de.netzfactor.ebz.controlling.integration.party.service.CtiService;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;

/**
 * CTI-WebSocket der Cockpits (Plan A13): reiner Transport-Kanal. Die Cockpits verbinden sich beim Laden
 * und erhalten eingehende Anruf-Events ({@link CtiService.AnrufEvent}) per Broadcast. Die offene
 * Verbindung wird von Quarkus in {@code OpenConnections} registriert (Endpoint-Id {@value
 * CtiService#ENDPOINT_ID}), über die der {@link CtiService} broadcastet.
 *
 * <p>Reiner Listener (kein DB-Schreiben über diesen Kanal) → derzeit offen wie die übrigen CRM-Reads;
 * RBAC-Feinschliff über den WS-Handshake folgt mit der Keycloak-Realm-Konfiguration.
 */
@WebSocket(path = "/ws/crm/cti", endpointId = CtiService.ENDPOINT_ID)
public class CtiSocket {

    /** Begrüßungs-Hook; die Registrierung in {@code OpenConnections} übernimmt der Container. */
    @OnOpen
    public void onOpen() {
        // no-op: Events kommen ausschließlich per Broadcast aus dem CtiService.
    }
}
