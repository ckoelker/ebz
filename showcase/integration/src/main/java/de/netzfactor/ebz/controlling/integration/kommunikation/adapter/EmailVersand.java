package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.KanalVersand;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;

/**
 * {@link KanalVersand}-Adapter für <b>E-Mail</b> über den Quarkus-{@link Mailer} (Dev → Mailpit; Test →
 * MockMailbox). Läuft async aus dem {@code ZustellDispatcher} (eigene Transaktion); bei fehlender
 * Empfänger-Adresse oder SMTP-Fehler wirft er → Backoff/Retry/Dead-Letter. Empfänger-Adresse/Anrede
 * kommen über den {@link ErreichbarkeitPort} (kein direkter Party-Zugriff). In K0 ein schlanker Text-
 * Body (Betreff aus dem {@code PersonEreignis}); die versionierte Template-Registry (Qute) folgt in K1.
 */
@ApplicationScoped
public class EmailVersand implements KanalVersand {

    @Inject
    Mailer mailer;

    @Inject
    ErreichbarkeitPort erreichbarkeit;

    @Override
    public Kanal kanal() {
        return Kanal.EMAIL;
    }

    @Override
    public void zustelle(Zustellung zustellung) {
        PersonEreignis ev = zustellung.personEreignis;
        Long personId = ev.empfaengerPersonId;
        String email = erreichbarkeit.primaerEmail(personId);
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Keine E-Mail-Adresse für Person " + personId);
        }
        mailer.send(Mail.withText(email, ev.betreff, """
                %s,

                %s

                Diese Nachricht finden Sie auch jederzeit in Ihrem EBZ-Portal unter „Meine Aktivitäten".

                Viele Grüße
                Ihr EBZ-Team
                """.formatted(erreichbarkeit.anrede(personId), ev.betreff)));
        zustellung.status = Zustellung.Status.ZUGESTELLT;
        zustellung.zeitpunkt = LocalDateTime.now();
    }
}
