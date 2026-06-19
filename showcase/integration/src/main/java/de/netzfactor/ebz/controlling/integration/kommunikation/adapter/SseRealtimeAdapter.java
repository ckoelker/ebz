package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import jakarta.enterprise.context.ApplicationScoped;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.RealtimePort;

/**
 * {@link RealtimePort}-Adapter auf Basis eines In-Memory-{@link BroadcastProcessor} → liefert den
 * Portal-SSE-Feed (K1): bei jedem neuen Inbox-Ereignis erhält die Person sofort ein Signal (Badge/Feed-
 * Aktualisierung), ohne zu pollen. {@link #signalisiere} ist <b>best effort</b> (entkoppelt von der
 * Geschäfts-Tx, wirft nie). Eine Instanz genügt im Showcase; bei mehreren Instanzen träte hier später die
 * Backplane (LISTEN/NOTIFY/Redis) an die Stelle des lokalen Processors — der Port bleibt gleich.
 */
@ApplicationScoped
public class SseRealtimeAdapter implements RealtimePort {

    private record Signal(Long personId, String ref) {
    }

    private final BroadcastProcessor<Signal> processor = BroadcastProcessor.create();

    @Override
    public void signalisiere(Long personId, String ereignisRef) {
        if (personId != null) {
            processor.onNext(new Signal(personId, ereignisRef));
        }
    }

    @Override
    public Multi<String> stream(Long personId) {
        return processor.filter(s -> personId.equals(s.personId())).map(Signal::ref);
    }
}
