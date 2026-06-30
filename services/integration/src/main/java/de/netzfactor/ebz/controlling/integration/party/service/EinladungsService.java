package de.netzfactor.ebz.controlling.integration.party.service;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
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

    /** Event-Spine: die Einladungsmail (Login-Link) fährt über die Spine (Template + Zustell-Outbox). */
    @Inject
    Event<KommunikationsEreignis> benachrichtigung;

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

        LoginProvisionierung.Ergebnis erg = provisionierung.anlegen(email, p.anzeigeName());
        if (erg.provisioniert()) {
            prozess.schritt("Login provisionieren", Akteur.SYSTEM, Prozess.System.KEYCLOAK,
                    Typ.SERVICE_TASK, Phase.EINLADUNG);
        }

        // Event-Spine: Portal-Log (provisorische Person) + Einladungsmail mit Login-Link (Template + Outbox).
        benachrichtigung.fire(KommunikationsEreignis.mitVariablen(
                EreignisTyp.EINLADUNG, p.id, "Ihr Zugang zum EBZ-Ausbildungsportal",
                KontextTyp.KEINER, null, null,
                Map.of("anzeigeName", p.anzeigeName(), "portalUrl", portalUrl)));
        prozess.schritt("Einladungsmail senden", Akteur.SYSTEM, Prozess.System.MAIL, Typ.MESSAGE,
                Phase.EINLADUNG);

        return new Einladung(p.id, email, erg.keycloakUserId(), erg.provisioniert(), true);
    }

    private static String primaerEmail(Long personId) {
        return PartyHoheitService.primaerEmail(personId);
    }
}
