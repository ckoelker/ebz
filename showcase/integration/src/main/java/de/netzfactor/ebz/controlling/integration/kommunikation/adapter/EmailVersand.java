package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import java.util.Map;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.KanalVersand;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.TemplatePort;

/**
 * {@link KanalVersand}-Adapter für <b>E-Mail</b> über den Quarkus-{@link Mailer} (Dev → Mailpit; Test →
 * MockMailbox). Läuft async aus dem {@code ZustellDispatcher} (eigene Transaktion); bei fehlender
 * Empfänger-Adresse oder SMTP-Fehler wirft er → Backoff/Retry/Dead-Letter. Empfänger-Adresse/Anrede
 * kommen über den {@link ErreichbarkeitPort}, Betreff/Body über die {@link TemplatePort Template-Registry}
 * (Qute) — kein direkter Party-Zugriff, keine hartkodierten Texte.
 */
@ApplicationScoped
public class EmailVersand implements KanalVersand {

    @Inject
    Mailer mailer;

    @Inject
    ErreichbarkeitPort erreichbarkeit;

    @Inject
    TemplatePort templates;

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
        TemplatePort.Gerendert g = templates.render(ev.ereignisTyp, Map.of(
                "anrede", erreichbarkeit.anrede(personId),
                "betreff", ev.betreff));
        mailer.send(Mail.withText(email, g.betreff(), g.body()));
        zustellung.status = Zustellung.Status.ZUGESTELLT;
        zustellung.zeitpunkt = LocalDateTime.now();
    }
}
