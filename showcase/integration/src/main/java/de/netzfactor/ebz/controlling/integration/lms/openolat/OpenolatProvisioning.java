package de.netzfactor.ebz.controlling.integration.lms.openolat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Fachlicher OpenOLAT-Provisionierungs-Adapter (hinter einem CDI-Bean, damit der Dispatcher-Test ihn
 * mocken kann). Kapselt die zwei idempotenten Operationen der Einschreibungs-Naht:
 * <ul>
 *   <li>{@link #ensureUserUndEnrol} — OpenOLAT-Identität anhand des Keycloak-Subjects sicherstellen
 *       (Lookup über {@code authProvider=KEYCLOAK}; sonst User + KEYCLOAK-Authentifizierung anlegen,
 *       damit der spätere SSO-Login dublettenfrei verschmilzt) und in die Lernressource einschreiben.</li>
 *   <li>{@link #ausschreiben} — Teilnehmer aus der Lernressource entfernen.</li>
 * </ul>
 * Beide sind idempotent (At-least-once-tauglich); Fehlschläge werfen {@link OpenolatException}, der
 * Dispatcher übernimmt Backoff/Retry/Dead-Letter.
 */
@ApplicationScoped
public class OpenolatProvisioning {

    private static final Logger LOG = Logger.getLogger(OpenolatProvisioning.class);
    private static final String KEYCLOAK_PROVIDER = "KEYCLOAK";

    @RestClient
    OpenolatApi api;

    @ConfigProperty(name = "openolat.admin.username", defaultValue = "administrator")
    String adminUser;

    @ConfigProperty(name = "openolat.admin.password", defaultValue = "openolat")
    String adminPass;

    /** Stellt die OpenOLAT-Identität (per Keycloak-Sub) sicher und schreibt sie in {@code openolatKey} ein. */
    public long ensureUserUndEnrol(String keycloakSub, String email, String anzeigeName, long openolatKey) {
        String auth = basicAuth();
        long identityKey = ensureUser(auth, keycloakSub, email, anzeigeName);
        try (Response r = api.addParticipant(auth, openolatKey, identityKey)) {
            if (r.getStatus() >= 300) {
                throw new OpenolatException("Einschreiben fehlgeschlagen (HTTP " + r.getStatus()
                        + ") für Identity " + identityKey + " in Kurs " + openolatKey);
            }
        } catch (OpenolatException oe) {
            throw oe;
        } catch (RuntimeException re) {
            throw new OpenolatException("OpenOLAT-Einschreiben nicht erreichbar: " + re.getMessage(), re);
        }
        LOG.infof("OpenOLAT: Identity %d in Kurs %d eingeschrieben (sub %s)", identityKey, openolatKey, keycloakSub);
        return identityKey;
    }

    /** Entfernt den Teilnehmer wieder (Storno). {@code identityKey} stammt aus der Einschreibung. */
    public void ausschreiben(long openolatKey, long identityKey) {
        try (Response r = api.removeParticipant(basicAuth(), openolatKey, identityKey)) {
            if (r.getStatus() >= 300 && r.getStatus() != 404) {
                throw new OpenolatException("Ausschreiben fehlgeschlagen (HTTP " + r.getStatus() + ")");
            }
        } catch (OpenolatException oe) {
            throw oe;
        } catch (RuntimeException re) {
            throw new OpenolatException("OpenOLAT-Ausschreiben nicht erreichbar: " + re.getMessage(), re);
        }
        LOG.infof("OpenOLAT: Identity %d aus Kurs %d ausgeschrieben", identityKey, openolatKey);
    }

    // ── intern ──
    private long ensureUser(String auth, String keycloakSub, String email, String anzeigeName) {
        try {
            JsonNode found = api.findUsersByAuth(auth, KEYCLOAK_PROVIDER, keycloakSub);
            if (found != null && found.isArray() && found.size() > 0) {
                return found.get(0).path("key").asLong(); // bereits vorhanden (z. B. via SSO)
            }
            // Neu anlegen + KEYCLOAK-Authentifizierung, damit der spätere SSO-Login matcht.
            String[] name = splitName(anzeigeName, email);
            JsonNode created = api.createUser(auth, Map.of(
                    "login", email != null && !email.isBlank() ? email : keycloakSub,
                    "email", email != null ? email : keycloakSub + "@invalid.local",
                    "firstName", name[0],
                    "lastName", name[1],
                    "password", UUID.randomUUID().toString())); // Login läuft über SSO, nicht über dieses Passwort
            long identityKey = created.path("key").asLong();
            // OpenOLAT lädt die Identität aus identityKey IM BODY (nicht aus dem Pfad) und verlangt eine
            // credential — fehlt eines, antwortet die AuthenticationWebService mit 404. Daher beides setzen
            // (credential = sub; für den SSO-Login irrelevant, gematcht wird über authUsername).
            try (Response r = api.putAuthentication(auth, identityKey, Map.of(
                    "identityKey", identityKey,
                    "provider", KEYCLOAK_PROVIDER,
                    "authUsername", keycloakSub,
                    "credential", keycloakSub))) {
                if (r.getStatus() >= 300) {
                    throw new OpenolatException("Anlegen der KEYCLOAK-Authentifizierung fehlgeschlagen (HTTP "
                            + r.getStatus() + ")");
                }
            }
            LOG.infof("OpenOLAT: User %d neu angelegt + KEYCLOAK-Auth (sub %s)", identityKey, keycloakSub);
            return identityKey;
        } catch (OpenolatException oe) {
            throw oe;
        } catch (RuntimeException re) {
            throw new OpenolatException("OpenOLAT-User-Provisionierung fehlgeschlagen: " + re.getMessage(), re);
        }
    }

    private String basicAuth() {
        String token = Base64.getEncoder()
                .encodeToString((adminUser + ":" + adminPass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    /** "Carla Kundin" → ["Carla","Kundin"]; ohne Leerzeichen → ["WBT", name]; leer → aus E-Mail/Default. */
    private static String[] splitName(String anzeigeName, String email) {
        String n = anzeigeName == null ? "" : anzeigeName.trim();
        if (n.isEmpty()) {
            n = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "Lernende";
        }
        int sp = n.lastIndexOf(' ');
        if (sp <= 0) {
            return new String[] {"WBT", n};
        }
        return new String[] {n.substring(0, sp), n.substring(sp + 1)};
    }
}
