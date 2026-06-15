package de.netzfactor.ebz.controlling.integration.lms;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

import io.quarkus.test.Mock;

import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatException;
import de.netzfactor.ebz.controlling.integration.lms.openolat.OpenolatProvisioning;

/**
 * CDI-Test-Doppel für {@link OpenolatProvisioning} (Projekt-Konvention: {@code @io.quarkus.test.Mock}
 * statt Mockito, wie der WebUntis-Mock). Ersetzt im Test-Profil den echten Adapter → kein OpenOLAT-Call.
 * Zählt Enrol-/Unenrol-Aufrufe (Idempotenz) und kann auf Fehler geschaltet werden (Backoff/Dead-Letter).
 * <p>
 * Bewusst {@code @Singleton} (kein Client-Proxy) — so wirken die Feld-Mutationen aus dem Test (fail/
 * nextIdentityKey/Counter) auf <i>dieselbe</i> Instanz, die der Service nutzt (ein {@code @ApplicationScoped}-
 * Proxy würde nur Methoden, nicht Feldzugriffe delegieren).
 */
@Mock
@Singleton
public class FakeOpenolatProvisioning extends OpenolatProvisioning {

    public volatile boolean fail = false;
    public volatile long nextIdentityKey = 999L;
    public final AtomicInteger enrolCalls = new AtomicInteger();
    public final AtomicInteger unenrolCalls = new AtomicInteger();

    public void reset() {
        fail = false;
        nextIdentityKey = 999L;
        enrolCalls.set(0);
        unenrolCalls.set(0);
    }

    @Override
    public long ensureUserUndEnrol(String keycloakSub, String email, String anzeigeName, long openolatKey) {
        enrolCalls.incrementAndGet();
        if (fail) {
            throw new OpenolatException("Mock-Fehler beim Einschreiben");
        }
        return nextIdentityKey;
    }

    @Override
    public void ausschreiben(long openolatKey, long identityKey) {
        unenrolCalls.incrementAndGet();
        if (fail) {
            throw new OpenolatException("Mock-Fehler beim Ausschreiben");
        }
    }
}
