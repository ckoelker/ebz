package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.KanalVersand;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.AnhangPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.TemplatePort;

/**
 * {@link KanalVersand}-Adapter für <b>E-Mail</b> über den Quarkus-{@link Mailer} (Dev → Mailpit; Test →
 * MockMailbox). Läuft async aus dem {@code ZustellDispatcher} (eigene Transaktion); bei fehlender
 * Empfänger-Adresse oder SMTP-Fehler wirft er → Backoff/Retry/Dead-Letter. Empfänger ist die
 * Person-Primäradresse ({@link ErreichbarkeitPort}) <b>oder</b> der Direkt-Empfänger {@code anEmail}
 * (Bestands-Mail-Migration); Betreff/Body kommen aus der {@link TemplatePort Template-Registry} (Qute,
 * gespeist aus den Event-Variablen), Anhänge über den {@link AnhangPort} (z. B. ZUGFeRD beim Rechnungs-Kontext).
 */
@ApplicationScoped
public class EmailVersand implements KanalVersand {

    private static final TypeReference<Map<String, Object>> VARIABLEN_TYP = new TypeReference<>() {
    };

    @Inject
    Mailer mailer;

    @Inject
    ErreichbarkeitPort erreichbarkeit;

    @Inject
    TemplatePort templates;

    @Inject
    Instance<AnhangPort> anhangPorts;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Kanal kanal() {
        return Kanal.EMAIL;
    }

    @Override
    public void zustelle(Zustellung zustellung) {
        PersonEreignis ev = zustellung.personEreignis;
        Long personId = ev.empfaengerPersonId;
        boolean direkt = ev.anEmail != null && !ev.anEmail.isBlank();
        String email = direkt ? ev.anEmail : erreichbarkeit.primaerEmail(personId);
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Keine E-Mail-Adresse für Ereignis " + ev.id);
        }

        Map<String, Object> variablen = new HashMap<>(leseVariablen(ev.variablenJson));
        variablen.put("anrede", personId != null ? erreichbarkeit.anrede(personId) : "Guten Tag");
        variablen.put("betreff", ev.betreff);
        TemplatePort.Gerendert g = templates.render(ev.ereignisTyp, variablen);

        Mail mail = Mail.withText(email, g.betreff(), g.body());
        for (AnhangPort port : anhangPorts) {
            for (AnhangPort.Anhang a : port.anhaenge(ev.kontextTyp, ev.kontextId)) {
                mail.addAttachment(a.dateiname(), a.inhalt(), a.contentType());
            }
        }
        mailer.send(mail);
        zustellung.status = Zustellung.Status.ZUGESTELLT;
        zustellung.zeitpunkt = LocalDateTime.now();
    }

    private Map<String, Object> leseVariablen(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, VARIABLEN_TYP);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
