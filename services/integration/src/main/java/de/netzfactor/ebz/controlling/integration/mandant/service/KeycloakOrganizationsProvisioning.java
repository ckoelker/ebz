package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.OrganizationsResource;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;

/**
 * Keycloak-Adapter der Mandanten-Schicht (M3): projiziert einen föderierten B2B-{@code Mandant}en als
 * <b>Keycloak-Organization</b> (v26 GA) — die Quelle des Domain→Mandant-Routings und des {@code mandant}-
 * Claims (A3/K4). Hinter einem CDI-Bean (Quarkiverse {@code quarkus-keycloak-admin-rest-client},
 * {@code @Inject Keycloak}), damit der Dispatcher-Test ihn per {@code @io.quarkus.test.Mock} ersetzt
 * (G3: kein echter Keycloak-Call im Test).
 * <p>
 * <b>Idempotent über {@code alias}</b> (= kleingeschriebener Mandant-Schlüssel): existiert die Organization,
 * werden nur Name/Domains/{@code mandant}-Attribut nachgezogen (reproduzierbarer Bootstrap, K8), sonst neu
 * angelegt. Das Attribut {@value #ATTR_MANDANT} trägt den fachlichen Schlüssel und wird in M3b über einen
 * Protocol-Mapper als {@code mandant}-Claim ins Token gehoben.
 */
@ApplicationScoped
public class KeycloakOrganizationsProvisioning {

    private static final Logger LOG = Logger.getLogger(KeycloakOrganizationsProvisioning.class);

    /** Org-Attribut mit dem Mandant-Schlüssel → Quelle des {@code mandant}-Claims (M3b-Mapper). */
    public static final String ATTR_MANDANT = "mandant";

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "mandant.keycloak.realm", defaultValue = "ebz-customers")
    String realm;

    /**
     * Stellt die Keycloak-Organization zum Mandanten sicher (idempotent über {@code alias}) und liefert ihre
     * ID. {@code emailDomains} routen die gebrokerten Logins in diese Org; Keycloak verlangt mindestens eine
     * Domain. Fehlschläge werfen eine {@link RuntimeException} → der Dispatcher übernimmt Backoff/Retry.
     */
    public String ensureOrganization(String schluessel, String anzeigeName, List<String> emailDomains) {
        OrganizationsResource orgs = keycloak.realm(realm).organizations();
        String alias = alias(schluessel);
        OrganizationRepresentation vorhanden = findeByAlias(orgs, alias);
        OrganizationRepresentation rep = vorhanden != null ? vorhanden : new OrganizationRepresentation();
        rep.setName(anzeigeName);
        rep.setAlias(alias);
        rep.setEnabled(true);
        rep.singleAttribute(ATTR_MANDANT, schluessel);
        int domains = ergaenzeDomains(rep, emailDomains);
        if (domains == 0 && (rep.getDomains() == null || rep.getDomains().isEmpty())) {
            throw new IllegalStateException(
                    "Keycloak-Organization braucht mindestens eine E-Mail-Domain (Mandant " + schluessel + ")");
        }
        if (vorhanden != null) {
            orgs.get(vorhanden.getId()).update(rep);
            LOG.infof("Keycloak-Org '%s' vorhanden (id %s) → Name/Domains/Attribut aktualisiert", alias,
                    vorhanden.getId());
            return vorhanden.getId();
        }
        try (Response resp = orgs.create(rep)) {
            if (resp.getStatus() >= 300) {
                throw new IllegalStateException("Keycloak-Org-Anlage fehlgeschlagen: HTTP " + resp.getStatus()
                        + " (alias " + alias + ")");
            }
            String id = CreatedResponseUtil.getCreatedId(resp);
            LOG.infof("Keycloak-Org angelegt: id %s (alias %s, %d Domain(s))", id, alias, domains);
            return id;
        }
    }

    /** Ergänzt fehlende Domains idempotent (lowercase, getrimmt); liefert die Anzahl neu hinzugefügter. */
    private static int ergaenzeDomains(OrganizationRepresentation rep, List<String> emailDomains) {
        int neu = 0;
        if (emailDomains == null) {
            return 0;
        }
        for (String d : emailDomains) {
            if (d == null) {
                continue;
            }
            String dom = d.trim().toLowerCase();
            if (!dom.isEmpty() && rep.getDomain(dom) == null) {
                rep.addDomain(new OrganizationDomainRepresentation(dom));
                neu++;
            }
        }
        return neu;
    }

    private static OrganizationRepresentation findeByAlias(OrganizationsResource orgs, String alias) {
        for (OrganizationRepresentation o : orgs.getAll()) {
            if (alias.equals(o.getAlias())) {
                return o;
            }
        }
        return null;
    }

    /** Keycloak-Organization-Alias = kleingeschriebener Mandant-Schlüssel (Unterstrich → Bindestrich). */
    static String alias(String schluessel) {
        return schluessel.toLowerCase().replace('_', '-');
    }
}
