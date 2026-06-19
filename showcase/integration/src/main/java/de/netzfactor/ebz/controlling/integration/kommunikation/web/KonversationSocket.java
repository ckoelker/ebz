package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;

import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ThreadRealtimePort;

/**
 * Interaktiver Thread-WebSocket (K2): Portal- und Admin-Client verbinden sich beim Öffnen eines Vorgangs
 * mit {@code /ws/kommunikation/konversationen/{konversationId}} und erhalten ein schlankes „neue Nachricht"-
 * Signal, sobald jemand antwortet — den Inhalt laden sie über das autorisierte REST nach. Reiner
 * Transport (kein DB-Schreiben über diesen Kanal); der Broadcast kommt aus dem {@link ThreadRealtimePort}.
 *
 * <p>Wie die Bestands-Sockets ({@code CtiSocket}/{@code AnreicherungSocket}) derzeit offen; RBAC am
 * WS-Handshake (Cross-Realm-Token) folgt mit der Keycloak-Realm-Konfiguration.
 */
@WebSocket(path = "/ws/kommunikation/konversationen/{konversationId}", endpointId = ThreadRealtimePort.ENDPOINT_ID)
public class KonversationSocket {

    @OnOpen
    public void onOpen() {
        // no-op: Signale kommen ausschließlich per Broadcast aus dem ThreadWebSocketAdapter.
    }
}
