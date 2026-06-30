package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatApi;
import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatException;

/**
 * OpenOLAT-Adapter der Mandanten-Schicht (M2): projiziert einen MDM-Mandanten als OpenOLAT-Organisation.
 * Hinter einem CDI-Bean, damit der Dispatcher-Test ihn per {@code @io.quarkus.test.Mock} ersetzen kann
 * (kein OpenOLAT-Call im Test). Endpunkte gegen {@code 20.1/openapi.json} verifiziert (B4):
 * {@code GET/PUT/POST /organisations}.
 * <p>
 * <b>Idempotent über {@code externalId}</b> (= Mandant-Schlüssel): {@link #ensureOrganisation} sucht zuerst
 * eine Organisation mit passender {@code externalId}; existiert sie, wird nur ihr Branding
 * ({@code displayName}/{@code cssClass}) nachgezogen (reproduzierbarer Bootstrap, K8), sonst neu angelegt.
 * {@code cssClass} ist ein nativer OrganisationVO-Wert → der per-Org-Branding-Hebel (M0/K3) ohne Hack.
 */
@ApplicationScoped
public class OpenolatOrganisationProvisioning {

    private static final Logger LOG = Logger.getLogger(OpenolatOrganisationProvisioning.class);

    @RestClient
    OpenolatApi api;

    @ConfigProperty(name = "openolat.admin.username", defaultValue = "administrator")
    String adminUser;

    @ConfigProperty(name = "openolat.admin.password", defaultValue = "openolat")
    String adminPass;

    /**
     * Stellt die OpenOLAT-Organisation zum Mandanten sicher (idempotent über {@code externalId}) und liefert
     * ihren {@code key}. {@code cssClass} setzt den per-Org-Branding-Anker (M0); {@code displayName} den
     * Anzeigenamen. Fehlschläge werfen {@link OpenolatException} → der Dispatcher übernimmt Backoff/Retry.
     */
    public long ensureOrganisation(String externalId, String displayName, String cssClass) {
        String auth = basicAuth();
        try {
            Long vorhandenerKey = findeOrganisationKey(auth, externalId);
            if (vorhandenerKey != null) {
                // Branding/Name reproduzierbar nachziehen (Bootstrap-Idempotenz, K8).
                api.updateOrganisation(auth, vo(externalId, displayName, cssClass, vorhandenerKey));
                LOG.infof("OpenOLAT-Org für externalId %s vorhanden (key %d) → Branding aktualisiert", externalId, vorhandenerKey);
                return vorhandenerKey;
            }
            JsonNode created = api.createOrganisation(auth, vo(externalId, displayName, cssClass, null));
            long key = created.path("key").asLong();
            if (key <= 0) {
                throw new OpenolatException("OpenOLAT-Org-Anlage ohne key in der Antwort (externalId " + externalId + ")");
            }
            LOG.infof("OpenOLAT-Org angelegt: key %d (externalId %s, cssClass %s)", key, externalId, cssClass);
            return key;
        } catch (OpenolatException oe) {
            throw oe;
        } catch (RuntimeException re) {
            throw new OpenolatException("OpenOLAT-Org-Projektion fehlgeschlagen: " + re.getMessage(), re);
        }
    }

    /**
     * Zählt die aktiven Mitglieder einer Organisation in der Rolle {@code role} (z. B. {@code user}) — die
     * Grundlage der Seat-Belegung (M5/E2). Gegen {@code GET /organisations/{key}/{role}} verifiziert (B4).
     */
    public int zaehleMitglieder(long organisationKey, String role) {
        try {
            JsonNode members = api.listOrganisationMembers(basicAuth(), organisationKey, role);
            return members != null && members.isArray() ? members.size() : 0;
        } catch (RuntimeException re) {
            throw new OpenolatException("OpenOLAT-Mitglieder-Zählung fehlgeschlagen (org " + organisationKey
                    + ", role " + role + "): " + re.getMessage(), re);
        }
    }

    private Long findeOrganisationKey(String auth, String externalId) {
        JsonNode all = api.listOrganisations(auth);
        if (all != null && all.isArray()) {
            for (JsonNode org : all) {
                if (externalId.equals(org.path("externalId").asText(null))) {
                    return org.path("key").asLong();
                }
            }
        }
        return null;
    }

    private static Map<String, Object> vo(String externalId, String displayName, String cssClass, Long key) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("managedFlagsString", ""); // Pflichtfeld der OrganisationVO; leer = nicht extern-verwaltet
        vo.put("externalId", externalId);
        vo.put("identifier", externalId);
        vo.put("displayName", displayName);
        if (cssClass != null && !cssClass.isBlank()) {
            vo.put("cssClass", cssClass);
        }
        if (key != null) {
            vo.put("key", key);
        }
        return vo;
    }

    private String basicAuth() {
        String token = Base64.getEncoder()
                .encodeToString((adminUser + ":" + adminPass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
