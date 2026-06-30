package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.CrmSpiegelPort;
import de.netzfactor.ebz.controlling.integration.party.model.Aktivitaet;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Mitarbeiter;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * {@link CrmSpiegelPort}-Adapter: spiegelt eine vom Sachbearbeiter im Thread gesendete Nachricht zusätzlich
 * als ausgehende {@code party.Aktivitaet} ins CRM-Kontaktlog (K2: „kein Doppelsystem"). Berührt — wie der
 * {@link PartyAuskunftAdapter} — bewusst nur im {@code adapter}-Paket den Party-Kern (ACL); der
 * Kommunikations-Kern bleibt party-frei. Best effort: ein Fehler hier darf den Nachrichtenversand nie kippen.
 */
@ApplicationScoped
public class CrmSpiegelAdapter implements CrmSpiegelPort {

    private static final Logger LOG = Logger.getLogger(CrmSpiegelAdapter.class);

    @Override
    @Transactional
    public void spiegleStaffNachricht(Long mitarbeiterId, Long personId, String betreff, String inhaltText) {
        try {
            Person person = personId == null ? null : Person.findById(personId);
            if (person == null) {
                return;
            }
            Aktivitaet a = new Aktivitaet();
            a.typ = typ();
            a.richtung = Aktivitaet.Richtung.AUSGEHEND;
            a.betreff = betreff == null || betreff.isBlank() ? "Nachricht im Kundenportal" : betreff;
            a.inhaltHtml = inhaltText;
            a.person = person;
            a.bearbeiter = mitarbeiterId == null ? null : Mitarbeiter.findById(mitarbeiterId);
            a.persist();
        } catch (RuntimeException e) {
            // Spiegelung ist nachrangig — der Thread-Versand bleibt davon unberührt.
            LOG.warnf(e, "CRM-Spiegelung der Portal-Nachricht an Person %d fehlgeschlagen", personId);
        }
    }

    /** Aktivitätstyp „Notiz" (Fallback: erster vorhandener) für den Kontaktnachweis. */
    private static Lookups.Aktivitaetstyp typ() {
        Lookups.Aktivitaetstyp t = Lookups.Aktivitaetstyp.find("code", "NOTIZ").firstResult();
        return t != null ? t : Lookups.Aktivitaetstyp.<Lookups.Aktivitaetstyp>findAll().firstResult();
    }
}
