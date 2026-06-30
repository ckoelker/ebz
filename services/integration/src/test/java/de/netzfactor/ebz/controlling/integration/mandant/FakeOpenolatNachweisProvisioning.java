package de.netzfactor.ebz.controlling.integration.mandant;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkus.test.Mock;

import de.netzfactor.ebz.controlling.integration.mandant.service.OpenolatNachweisProvisioning;

/**
 * CDI-Test-Doppel für {@link OpenolatNachweisProvisioning} (Projekt-Konvention {@code @io.quarkus.test.Mock},
 * {@code @Singleton} wie {@link FakeOpenolatOrganisationProvisioning}) — kein OpenOLAT-Call. {@link #meldeCompletion}
 * setzt {@link #completionVorhanden} (simuliert OpenOLAT als System-of-Record), sodass ein anschließendes
 * {@link #leseCompletion} den Abschluss liefert.
 */
@Mock
@Singleton
public class FakeOpenolatNachweisProvisioning extends OpenolatNachweisProvisioning {

    public volatile long courseId = 7000L;
    public volatile String nodeId = "NODE-NACHWEIS";
    public volatile boolean completionVorhanden = false;
    public volatile boolean bestanden = true;
    public volatile Instant abgeschlossenAm = Instant.parse("2026-06-26T08:30:00Z");
    public final AtomicInteger meldeCalls = new AtomicInteger();
    public final AtomicInteger linkCalls = new AtomicInteger();

    public void reset() {
        courseId = 7000L;
        nodeId = "NODE-NACHWEIS";
        completionVorhanden = false;
        bestanden = true;
        abgeschlossenAm = Instant.parse("2026-06-26T08:30:00Z");
        meldeCalls.set(0);
        linkCalls.set(0);
    }

    @Override
    public KursRef ensureNachweisKurs(String externalId, String displayName) {
        return new KursRef(courseId, nodeId);
    }

    @Override
    public void linkKursZuOrg(long courseId, long organisationKey) {
        linkCalls.incrementAndGet();
    }

    @Override
    public void meldeCompletion(long courseId, String nodeId, long identityKey, boolean bestanden) {
        meldeCalls.incrementAndGet();
        this.completionVorhanden = true;
        this.bestanden = bestanden;
    }

    @Override
    public CompletionVO leseCompletion(long courseId, String nodeId, long identityKey) {
        return new CompletionVO(completionVorhanden, bestanden, completionVorhanden ? abgeschlossenAm : null);
    }
}
