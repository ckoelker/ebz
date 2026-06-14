package de.netzfactor.ebz.controlling.integration.party.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * Keycloak-Implementierung der {@link LoginProvisionierung}: legt im Customer-Realm
 * ({@code anmeldung.provisionierung.realm}) idempotent einen Login an (E-Mail = Username,
 * {@code emailVerified=false}, Pflicht-Aktion {@code UPDATE_PASSWORD}). Ist die Provisionierung
 * deaktiviert ({@code anmeldung.provisionierung.enabled=false}, z. B. im Test/ohne Keycloak), wird
 * <b>übersprungen</b> ({@code provisioniert=false}) — die Einladungsmail geht trotzdem raus.
 *
 * <p>{@link Keycloak} wird über {@link Instance} lazy bezogen, damit ohne aktive Provisionierung kein
 * Admin-Client benötigt/kontaktiert wird.
 */
@ApplicationScoped
public class KeycloakLoginProvisionierung implements LoginProvisionierung {

    private static final Logger LOG = Logger.getLogger(KeycloakLoginProvisionierung.class);

    @Inject
    Instance<Keycloak> keycloak;

    @ConfigProperty(name = "anmeldung.provisionierung.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "anmeldung.provisionierung.realm", defaultValue = "ebz-customers")
    String realm;

    @Override
    public Ergebnis anlegen(String email, String anzeigeName) {
        if (!enabled) {
            LOG.debugf("Provisionierung deaktiviert → übersprungen für %s", email);
            return new Ergebnis(null, false, false);
        }
        UsersResource users = keycloak.get().realm(realm).users();

        List<UserRepresentation> vorhanden = users.searchByEmail(email, true);
        if (vorhanden != null && !vorhanden.isEmpty()) {
            return new Ergebnis(vorhanden.get(0).getId(), false, true); // idempotent
        }

        UserRepresentation u = new UserRepresentation();
        u.setUsername(email);
        u.setEmail(email);
        u.setEnabled(true);
        u.setEmailVerified(false);
        u.setFirstName(vorname(anzeigeName));
        u.setLastName(nachname(anzeigeName));
        u.setRequiredActions(List.of("UPDATE_PASSWORD"));

        try (Response r = users.create(u)) {
            if (r.getStatus() == 201) {
                return new Ergebnis(CreatedResponseUtil.getCreatedId(r), true, true);
            }
            if (r.getStatus() == 409) { // Race: zwischenzeitlich angelegt
                List<UserRepresentation> jetzt = users.searchByEmail(email, true);
                return new Ergebnis(jetzt.isEmpty() ? null : jetzt.get(0).getId(), false, true);
            }
            throw new RegelVerletzung("Keycloak-Provisionierung fehlgeschlagen (HTTP " + r.getStatus() + ").");
        }
    }

    private static String vorname(String anzeigeName) {
        if (anzeigeName == null || anzeigeName.isBlank()) {
            return "";
        }
        int sp = anzeigeName.trim().lastIndexOf(' ');
        return sp <= 0 ? anzeigeName.trim() : anzeigeName.trim().substring(0, sp);
    }

    private static String nachname(String anzeigeName) {
        if (anzeigeName == null || anzeigeName.isBlank()) {
            return "";
        }
        int sp = anzeigeName.trim().lastIndexOf(' ');
        return sp <= 0 ? "" : anzeigeName.trim().substring(sp + 1);
    }
}
