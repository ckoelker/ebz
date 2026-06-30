package de.netzfactor.ebz.controlling.integration.mandant;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkus.test.Mock;

import de.netzfactor.ebz.controlling.integration.mandant.service.KeycloakOrganizationsProvisioning;

/**
 * CDI-Test-Doppel für {@link KeycloakOrganizationsProvisioning} (Projekt-Konvention:
 * {@code @io.quarkus.test.Mock}, wie {@link FakeOpenolatOrganisationProvisioning}). Ersetzt im Test-Profil
 * den echten Adapter → kein Keycloak-Call (G3). Zählt {@code ensureOrganization}-Aufrufe (Idempotenz) und
 * merkt sich die zuletzt übergebenen Domains/Schlüssel; kann auf Fehler geschaltet werden
 * (Backoff/Dead-Letter). Bewusst {@code @Singleton} — Feld-Mutationen wirken auf dieselbe Instanz.
 */
@Mock
@Singleton
public class FakeKeycloakOrganizationsProvisioning extends KeycloakOrganizationsProvisioning {

    public volatile boolean fail = false;
    public volatile String nextOrgId = "kc-org-0001";
    public final AtomicInteger ensureCalls = new AtomicInteger();
    public volatile String letzterSchluessel = "(noch nie gerufen)";
    public volatile List<String> letzteDomains = List.of();

    public void reset() {
        fail = false;
        nextOrgId = "kc-org-0001";
        ensureCalls.set(0);
        letzterSchluessel = "(noch nie gerufen)";
        letzteDomains = List.of();
    }

    @Override
    public String ensureOrganization(String schluessel, String anzeigeName, List<String> emailDomains) {
        ensureCalls.incrementAndGet();
        letzterSchluessel = schluessel;
        letzteDomains = emailDomains;
        if (fail) {
            throw new IllegalStateException("Mock-Fehler bei Keycloak-Org-Anlage");
        }
        return nextOrgId;
    }
}
