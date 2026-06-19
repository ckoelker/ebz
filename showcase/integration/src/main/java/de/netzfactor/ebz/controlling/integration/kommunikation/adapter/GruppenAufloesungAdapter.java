package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe.Mitglied;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.EmpfaengerAufloesung;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.KohortenAuskunft;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;

/**
 * {@link EmpfaengerAufloesung}-Adapter (K3): löst eine {@link Personengruppe} <b>zum Sendezeitpunkt</b> zu
 * Person-IDs auf. {@code MANUELL} liest die gepflegten {@link Mitglied}er (modulintern); {@code ORGANISATION}
 * leitet den Firmenkreis dynamisch aus {@code party.Mitgliedschaft} ab (ACL→party, nur hier im Adapter).
 * <p>
 * {@code BILDUNGSANGEBOT}-Kohorten bleiben hier bewusst leer — sie hingen an {@code rechnung.Anmeldung},
 * was die ArchUnit-Regel (keine Kopplung an volatile Domänenmodule) verbietet; ihre Auflösung kommt über
 * eine <i>inbound</i>-SPI, die das {@code rechnung}-Modul implementiert (K3b).
 */
@ApplicationScoped
public class GruppenAufloesungAdapter implements EmpfaengerAufloesung {

    @Inject
    KohortenAuskunft kohorten;

    @Override
    @Transactional
    public List<Long> mitglieder(Long gruppeId) {
        Personengruppe g = Personengruppe.findById(gruppeId);
        if (g == null) {
            return List.of();
        }
        return switch (g.quelle) {
            case MANUELL -> Mitglied.<Mitglied>list("gruppe.id", gruppeId).stream()
                    .map(m -> m.personId).distinct().toList();
            case ORGANISATION -> g.quelleRefId == null ? List.of()
                    : Mitgliedschaft.<Mitgliedschaft>list("organisation.id", g.quelleRefId).stream()
                            .map(m -> m.person.id).distinct().toList();
            // K3b: Kohorte aus dem rechnung-Modul über die inbound-SPI (rechnung implementiert sie).
            case BILDUNGSANGEBOT -> kohorten.teilnehmerPersonIds(g.quelleRefId);
        };
    }
}
