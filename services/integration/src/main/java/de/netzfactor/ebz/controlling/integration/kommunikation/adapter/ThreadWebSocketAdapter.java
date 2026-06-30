package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.websockets.next.OpenConnections;

import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ThreadRealtimePort;

/**
 * {@link ThreadRealtimePort}-Adapter auf {@code quarkus-websockets-next}: broadcastet ein schlankes
 * „neue Nachricht"-Signal an die Clients, die mit genau diesem Thread verbunden sind (Filter über den
 * Pfad-Parameter {@code konversationId}). Broadcast idiomatisch über {@link OpenConnections} (kein
 * eigener Session-Pool, wie der Bestands-{@code CtiService}). Bewusst <b>nur die Konversations-ID</b> —
 * den Inhalt lädt der Client über das autorisierte REST nach (kein PII-Leak über den offenen Socket).
 */
@ApplicationScoped
public class ThreadWebSocketAdapter implements ThreadRealtimePort {

    private static final Logger LOG = Logger.getLogger(ThreadWebSocketAdapter.class);

    @Inject
    OpenConnections connections;

    @Override
    public void signalisiereThread(Long konversationId) {
        if (konversationId == null) {
            return;
        }
        String ref = String.valueOf(konversationId);
        String json = "{\"konversationId\":" + konversationId + "}";
        try {
            connections.findByEndpointId(ThreadRealtimePort.ENDPOINT_ID).stream()
                    .filter(c -> ref.equals(c.pathParam("konversationId")))
                    .forEach(c -> c.sendTextAndAwait(json));
        } catch (RuntimeException e) {
            // Echtzeit ist best effort — der persistierte Stand bleibt über REST/Reload korrekt.
            LOG.debugf("Thread-WS-Broadcast für Konversation %d übersprungen: %s", konversationId, e.getMessage());
        }
    }
}
