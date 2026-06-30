package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.KanalVersand;

/**
 * {@link KanalVersand}-Adapter für die <b>Portal-Inbox</b>. Die Inbox-Kopie ({@code PersonEreignis} +
 * {@code Zustellung}) entsteht bereits synchron in der Geschäfts-Transaktion (unverlierbar); dieser
 * Adapter markiert die Zustellung schlicht als {@code ZUGESTELLT} (Badge sichtbar). Idempotent: bereits
 * gelesene/zugestellte Einträge bleiben unverändert.
 */
@ApplicationScoped
public class PortalVersand implements KanalVersand {

    @Override
    public Kanal kanal() {
        return Kanal.PORTAL;
    }

    @Override
    public void zustelle(Zustellung zustellung) {
        if (zustellung.status == Zustellung.Status.NEU) {
            zustellung.status = Zustellung.Status.ZUGESTELLT;
            zustellung.zeitpunkt = LocalDateTime.now();
        }
    }
}
