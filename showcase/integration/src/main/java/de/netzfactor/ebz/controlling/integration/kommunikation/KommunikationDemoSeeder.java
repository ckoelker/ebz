package de.netzfactor.ebz.controlling.integration.kommunikation;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.party.model.Login;

/**
 * Seedet beim Start idempotent ein paar personenseitige Aktivitätslog-Einträge für die Beispielkundin
 * Carla Kundin ({@code customer@ebz.de}, vom {@code PartyDemoSeeder} angelegt) — damit der Portal-Zeitstrahl
 * im Showcase sofort gefüllt ist: ein System-Hinweis (nur Portal), eine Rechnungs-Benachrichtigung
 * (Portal + E-Mail) und eine zu bestätigende Vertrags-Benachrichtigung (Pflicht-Kenntnisnahme).
 * <p>
 * Läuft nach dem {@code PartyDemoSeeder} ({@link Priority} dahinter); idempotent über die
 * {@code idempotenzSchluessel} der Ereignisse (Re-Delivery dedupt {@link KommunikationApi}).
 */
@ApplicationScoped
public class KommunikationDemoSeeder {

    private static final Logger LOG = Logger.getLogger(KommunikationDemoSeeder.class);
    private static final String KAEUFER_LOGIN = "customer@ebz.de";

    @Inject
    KommunikationApi kommunikation;

    @Transactional
    void seed(@Observes @Priority(Interceptor.Priority.APPLICATION + 1100) StartupEvent ev) {
        Login login = Login.find("lower(loginEmail) = ?1", KAEUFER_LOGIN).firstResult();
        if (login == null) {
            return; // PartyDemoSeeder hat (noch) nicht geseedet — nichts zu tun
        }
        Long personId = login.personId();

        kommunikation.protokolliere(KommunikationsEreignis.ohneKontext(
                EreignisTyp.SYSTEM_HINWEIS, personId,
                "Willkommen in Ihrem EBZ-Portal", "seed:carla:willkommen"));

        kommunikation.protokolliere(KommunikationsEreignis.mitKontext(
                EreignisTyp.RECHNUNG_VERSANDT, personId,
                "Ihre Rechnung steht im Portal bereit", KontextTyp.RECHNUNG, null, null,
                "seed:carla:rechnung"));

        kommunikation.protokolliere(KommunikationsEreignis.mitKontext(
                EreignisTyp.AZUBI_VERTRAG_BESTAETIGT, personId,
                "Bitte bestätigen Sie die Kenntnisnahme Ihres Ausbildungsvertrags",
                KontextTyp.ANMELDUNG, null, null, "seed:carla:vertrag"));

        LOG.infof("Kommunikations-Demo geseedet für %s (Person %d)", KAEUFER_LOGIN, personId);
    }
}
