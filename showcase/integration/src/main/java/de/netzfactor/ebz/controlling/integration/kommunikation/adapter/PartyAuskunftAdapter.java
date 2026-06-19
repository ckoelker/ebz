package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.IdentitaetsPort;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * <b>Die einzige Brücke des Kommunikations-Moduls in den Party-Kern</b> (Anti-Corruption-Layer): hier — und
 * nur hier — wird {@code party} berührt. Der Kern (model/event/service/web/spi) kennt Personen nur als
 * {@code Long}-IDs und löst Identität/Erreichbarkeit über diese Ports auf. So bleibt der Kern split-ready:
 * beim späteren Communication-Core-Schnitt wird dieser Adapter zum Netz-Client (REST/Identitäts-Service),
 * ohne dass der Kern sich ändert. Durchgesetzt per ArchUnit (nur {@code adapter} darf {@code party} sehen).
 */
@ApplicationScoped
public class PartyAuskunftAdapter implements IdentitaetsPort, ErreichbarkeitPort {

    @Inject
    PartyHoheitService party;

    @Override
    public Long personIdFuerSub(String keycloakSub) {
        Person p = party.findeNachSub(keycloakSub);
        return p == null ? null : p.id;
    }

    @Override
    public Set<Kanal> erlaubteKanaele(Long personId, EreignisTyp typ) {
        Person p = Person.findById(personId);
        if (p == null) {
            return Set.of(); // unbekannt → Aufrufer verwirft das Ereignis
        }
        Set<Kanal> erlaubt = new HashSet<>();
        for (Kanal kanal : typ.pushKanaele) {
            // Transaktional (Vertrag) bzw. PORTAL gehen immer; Marketing-Kanäle prüfen werbe-/auskunftssperre.
            if (typ.transaktional() || kanal == Kanal.PORTAL || (!p.werbesperre && !p.auskunftssperre)) {
                erlaubt.add(kanal);
            }
        }
        // Bekannte Person bei sichtbarem Typ ist über das Portal stets erreichbar (Inbox-Kopie).
        if (typ.sichtbar) {
            erlaubt.add(Kanal.PORTAL);
        }
        return erlaubt;
    }

    @Override
    public String primaerEmail(Long personId) {
        return PartyHoheitService.primaerEmail(personId);
    }

    @Override
    public String anrede(Long personId) {
        Person p = Person.findById(personId);
        return p == null ? "Guten Tag" : p.briefanrede();
    }
}
