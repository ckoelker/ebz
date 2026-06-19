package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;

/**
 * K1b <b>Digest-Bündelung</b>: Personen mit aktiviertem Digest erhalten externe Benachrichtigungen nicht
 * als Einzelmails, sondern gesammelt. Dieser Scheduler zieht im festen Takt alle digest-ausstehenden
 * {@link Zustellung}en, gruppiert sie je Person und versendet <b>eine</b> Sammel-E-Mail (mehrere Betreffs
 * als Liste); die Einzelzustellungen werden dann auf {@code ZUGESTELLT} gesetzt. Das Portal-Postfach ist
 * davon unberührt (dort erscheinen die Einträge ohnehin sofort).
 */
@ApplicationScoped
public class DigestScheduler {

    private static final Logger LOG = Logger.getLogger(DigestScheduler.class);

    @Inject
    Mailer mailer;

    @Inject
    ErreichbarkeitPort erreichbarkeit;

    @Scheduled(every = "${kommunikation.digest.every:1h}", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        int n = versendeFaellige();
        if (n > 0) {
            LOG.debugf("Digest-Scheduler: %d Sammel-Mail(s) versendet", n);
        }
    }

    /** Versendet je Person eine Sammel-Mail über alle digest-ausstehenden Zustellungen. Liefert die Anzahl. */
    @Transactional
    public int versendeFaellige() {
        List<Zustellung> ausstehend = Zustellung.list("digestAusstehend = true");
        Map<Long, List<Zustellung>> proPerson = new LinkedHashMap<>();
        for (Zustellung z : ausstehend) {
            proPerson.computeIfAbsent(z.personEreignis.empfaengerPersonId, k -> new java.util.ArrayList<>()).add(z);
        }
        int mails = 0;
        for (Map.Entry<Long, List<Zustellung>> e : proPerson.entrySet()) {
            Long personId = e.getKey();
            List<Zustellung> posten = e.getValue();
            String email = erreichbarkeit.primaerEmail(personId);
            if (email != null && !email.isBlank()) {
                StringBuilder body = new StringBuilder(erreichbarkeit.anrede(personId)).append(",\n\n")
                        .append("seit Ihrer letzten Zusammenfassung sind ").append(posten.size())
                        .append(" neue Benachrichtigungen eingegangen:\n\n");
                for (Zustellung z : posten) {
                    body.append("• ").append(z.personEreignis.betreff).append('\n');
                }
                body.append("\nAlle Details finden Sie in Ihrem EBZ-Portal unter \"Meine Aktivitäten\".\n\n")
                        .append("Viele Grüße\nIhr EBZ-Team");
                mailer.send(Mail.withText(email, "Ihre EBZ-Benachrichtigungen (Zusammenfassung)", body.toString()));
                mails++;
            }
            for (Zustellung z : posten) {
                z.digestAusstehend = false;
                z.status = Zustellung.Status.ZUGESTELLT;
                z.zeitpunkt = LocalDateTime.now();
            }
        }
        return mails;
    }
}
