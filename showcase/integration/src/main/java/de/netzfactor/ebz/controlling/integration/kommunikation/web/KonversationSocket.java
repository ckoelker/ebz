package de.netzfactor.ebz.controlling.integration.kommunikation.web;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

import de.netzfactor.ebz.controlling.integration.kommunikation.service.KonversationService;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.IdentitaetsPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.StaffIdentitaetsPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ThreadRealtimePort;

/**
 * Interaktiver Thread-WebSocket (K2): Portal- und Admin-Client verbinden sich beim Öffnen eines Vorgangs
 * mit {@code /ws/kommunikation/konversationen/{konversationId}} und erhalten ein schlankes „neue Nachricht"-
 * Signal, sobald jemand antwortet — den Inhalt laden sie über das autorisierte REST nach. Reiner
 * Transport (kein DB-Schreiben über diesen Kanal); der Broadcast kommt aus dem {@link ThreadRealtimePort}.
 *
 * <p><b>RBAC am Handshake (Härtung):</b> {@link Authenticated} erzwingt einen gültigen Token am HTTP-Upgrade
 * (Cross-Realm — Staff über {@code ebz-staff}, Kunde über {@code ebz-customers}; der Tenant wird am
 * Token-Issuer aufgelöst, das Token reicht der Browser als {@code ?access_token} herein → s.
 * {@link RealtimeAuthRouteFilter}). Zusätzlich prüft {@link #onOpen} die <b>Thread-Mitgliedschaft</b>:
 * Wer weder als Person noch als Mitarbeiter zum Vorgang gehört, wird sofort getrennt — so erfährt niemand
 * über fremde Konversations-IDs auch nur deren Aktivität.
 */
@Authenticated
@WebSocket(path = "/ws/kommunikation/konversationen/{konversationId}", endpointId = ThreadRealtimePort.ENDPOINT_ID)
public class KonversationSocket {

    private static final Logger LOG = Logger.getLogger(KonversationSocket.class);

    @Inject
    SecurityIdentity identity;

    @Inject
    KonversationService konversationen;

    @Inject
    IdentitaetsPort personIdentitaet;

    @Inject
    StaffIdentitaetsPort staffIdentitaet;

    @OnOpen
    @Transactional
    public void onOpen(@PathParam("konversationId") String konversationId, WebSocketConnection connection) {
        // websockets-next bindet @PathParam nur als String → hier nach Long parsen (Thread-ID).
        String sub = identity == null || identity.getPrincipal() == null ? null
                : identity.getPrincipal().getName();
        if (!darfThread(konversationId, sub)) {
            LOG.warnf("WS-Zugriff auf Konversation %s für Subject %s abgelehnt (kein Teilnehmer)",
                    konversationId, sub);
            connection.closeAndAwait(); // kein Teilnehmer → Verbindung sofort trennen
        }
    }

    /** Gehört der per Token aufgelöste Aufrufer (Kunde ODER Mitarbeiter) zu diesem Thread? */
    private boolean darfThread(String konversationIdRaw, String sub) {
        if (sub == null || konversationIdRaw == null) {
            return false;
        }
        Long konversationId;
        try {
            konversationId = Long.valueOf(konversationIdRaw);
        } catch (NumberFormatException e) {
            return false;
        }
        Long personId = personIdentitaet.personIdFuerSub(sub);
        if (personId != null && konversationen.istTeilnehmerPerson(konversationId, personId)) {
            return true;
        }
        Long mitarbeiterId = staffIdentitaet.mitarbeiterIdFuerSub(sub);
        return mitarbeiterId != null && konversationen.istTeilnehmerStaff(konversationId, mitarbeiterId);
    }
}
