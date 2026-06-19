package de.netzfactor.ebz.controlling.integration.kommunikation.spi;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * <b>Outbound-SPI für einen Zustellkanal</b> (Adapter-Muster wie {@code Zielsystemexport}) — bewusst hinter
 * einem Interface, damit jeder Kanal (PORTAL/E-Mail, später SMS/Teams/Webhook/Push) einzeln testbar,
 * mockbar und austauschbar ist. Ein neuer Kanal = ein neuer Adapter, ohne Eingriff in den Kern; in der
 * Produktion werden daraus die unabhängig skalierenden Channel-Worker (Vendor-Isolation).
 * <p>
 * {@link #zustelle} <b>muss idempotent</b> sein (At-least-once: Retries/Doppel-Zustellung möglich) und
 * wirft bei Fehlschlag eine Exception — der {@code ZustellDispatcher} übernimmt Backoff/Retry/Dead-Letter.
 */
public interface KanalVersand {

    /** Für welchen Kanal dieser Adapter zuständig ist. */
    Kanal kanal();

    /** Stellt die {@link Zustellung} über den Kanal zu (idempotent; setzt bei Erfolg den Status). */
    void zustelle(Zustellung zustellung);
}
