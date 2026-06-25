package de.netzfactor.ebz.controlling.integration.mandant;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkus.test.Mock;

import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatException;
import de.netzfactor.ebz.controlling.integration.mandant.service.OpenolatOrganisationProvisioning;

/**
 * CDI-Test-Doppel für {@link OpenolatOrganisationProvisioning} (Projekt-Konvention:
 * {@code @io.quarkus.test.Mock}, wie {@code FakeOpenolatProvisioning}). Ersetzt im Test-Profil den echten
 * Adapter → kein OpenOLAT-Call. Zählt {@code ensureOrganisation}-Aufrufe (Idempotenz), merkt sich die
 * zuletzt übergebene {@code cssClass} (Branding-Ableitung M0) und kann auf Fehler geschaltet werden
 * (Backoff/Dead-Letter). Bewusst {@code @Singleton} — Feld-Mutationen wirken auf dieselbe Instanz.
 */
@Mock
@Singleton
public class FakeOpenolatOrganisationProvisioning extends OpenolatOrganisationProvisioning {

    public volatile boolean fail = false;
    public volatile long nextOrgKey = 4242L;
    public final AtomicInteger ensureCalls = new AtomicInteger();
    public volatile String letzteCssKlasse = "(noch nie gerufen)";
    /** Steuerbare aktive Org-Mitgliederzahl für die Seat-Cap-Tests (M5). */
    public volatile int mitgliederCount = 0;

    public void reset() {
        fail = false;
        nextOrgKey = 4242L;
        ensureCalls.set(0);
        letzteCssKlasse = "(noch nie gerufen)";
        mitgliederCount = 0;
    }

    @Override
    public int zaehleMitglieder(long organisationKey, String role) {
        return mitgliederCount;
    }

    @Override
    public long ensureOrganisation(String externalId, String displayName, String cssClass) {
        ensureCalls.incrementAndGet();
        letzteCssKlasse = cssClass;
        if (fail) {
            throw new OpenolatException("Mock-Fehler bei Org-Anlage");
        }
        return nextOrgKey;
    }
}
