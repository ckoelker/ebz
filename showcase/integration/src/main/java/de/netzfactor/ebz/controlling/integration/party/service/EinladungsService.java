package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * Schritt C: lädt einen geprüften Ansprechpartner ins Ausbildungsportal ein — provisioniert (sofern
 * aktiv) den Customer-Login über {@link LoginProvisionierung} und sendet die <b>Einladungsmail</b> mit
 * dem Portal-Link über den Quarkus-{@link Mailer} (Dev → Mailpit; Test → MockMailbox). Der erste Login
 * mit derselben E-Mail claimt anschließend die provisorische {@link Person} (Bestands-Mechanismus
 * {@code selbstRegistrieren}) und aktiviert sie.
 */
@ApplicationScoped
public class EinladungsService {

    @Inject
    LoginProvisionierung provisionierung;

    @Inject
    Mailer mailer;

    @Inject
    Prozessspur prozess;

    @ConfigProperty(name = "anmeldung.portal.url", defaultValue = "http://localhost:5174")
    String portalUrl;

    public record Einladung(Long personId, String email, String keycloakUserId, boolean provisioniert,
            boolean eingeladen) {
    }

    @Transactional
    public Einladung ladeEin(Long personId) {
        Person p = Person.findById(personId);
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + personId);
        }
        String email = primaerEmail(p.id);
        if (email == null) {
            throw new RegelVerletzung("Person " + personId + " hat keine E-Mail für die Einladung.");
        }

        prozess.schritt("Login-Einladung auslösen", Akteur.EBZ, Prozess.System.COCKPIT, Typ.USER_TASK,
                Phase.EINLADUNG);

        LoginProvisionierung.Ergebnis erg = provisionierung.anlegen(email, p.anzeigeName);
        if (erg.provisioniert()) {
            prozess.schritt("Login provisionieren", Akteur.SYSTEM, Prozess.System.KEYCLOAK,
                    Typ.SERVICE_TASK, Phase.EINLADUNG);
        }

        mailer.send(Mail.withText(email,
                "Ihr Zugang zum EBZ-Ausbildungsportal",
                """
                Hallo %s,

                Ihr Ausbildungsbetrieb ist im EBZ-Ausbildungsportal freigeschaltet. Bitte melden Sie sich
                mit dieser E-Mail-Adresse an und vergeben Sie beim ersten Login Ihr Passwort:

                %s

                Anschließend können Sie Ihre Auszubildenden zur Berufsschule anmelden.

                Viele Grüße
                Ihr EBZ-Team
                """.formatted(p.anzeigeName, portalUrl)));
        prozess.schritt("Einladungsmail senden", Akteur.SYSTEM, Prozess.System.MAIL, Typ.MESSAGE,
                Phase.EINLADUNG);

        return new Einladung(p.id, email, erg.keycloakUserId(), erg.provisioniert(), true);
    }

    private static String primaerEmail(Long personId) {
        PersonEmail e = PersonEmail.find("personId = ?1 and primaer = true", personId).firstResult();
        if (e == null) {
            e = PersonEmail.find("personId", personId).firstResult();
        }
        return e == null ? null : e.email;
    }
}
