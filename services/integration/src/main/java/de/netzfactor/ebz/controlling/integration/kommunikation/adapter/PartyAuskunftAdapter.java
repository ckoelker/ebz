package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.jwt.JsonWebToken;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.IdentitaetsPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.StaffIdentitaetsPort;
import de.netzfactor.ebz.controlling.integration.party.model.Mitarbeiter;
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
public class PartyAuskunftAdapter implements IdentitaetsPort, ErreichbarkeitPort, StaffIdentitaetsPort {

    @Inject
    PartyHoheitService party;

    @Inject
    JsonWebToken jwt;

    @Override
    @Transactional
    public Long personIdFuerSub(String keycloakSub) {
        Person p = party.findeNachSub(keycloakSub);
        return p == null ? null : p.id;
    }

    /**
     * Löst den eingeloggten Mitarbeiter über den Token-{@code sub} auf und legt ihn beim ersten Zugriff
     * <b>leichtgewichtig an</b> (Name/E-Mail aus den Token-Claims) — analog zum {@code selbstRegistrieren}
     * der Person. So braucht ein {@code ebz-staff}-Login mit {@code crm-pflege} keinen vorab geseedeten
     * {@code mdm.mitarbeiter}-Satz.
     */
    @Override
    @Transactional
    public Long mitarbeiterIdFuerSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return null;
        }
        Mitarbeiter m = Mitarbeiter.find("keycloakSub", keycloakSub).firstResult();
        if (m == null) {
            m = new Mitarbeiter();
            m.keycloakSub = keycloakSub;
            m.anzeigeName = anzeigeNameAusToken(keycloakSub);
            m.email = claim("email");
            m.persist();
        }
        return m.id;
    }

    /** Anzeigename aus den JWT-Claims (name → preferred_username → Fallback). */
    private String anzeigeNameAusToken(String fallback) {
        String name = claim("name");
        if (name == null || name.isBlank()) {
            name = claim("preferred_username");
        }
        return name == null || name.isBlank() ? "Mitarbeiter " + fallback : name;
    }

    private String claim(String name) {
        try {
            Object v = jwt.getClaim(name);
            return v == null ? null : v.toString();
        } catch (RuntimeException e) {
            return null; // kein aktiver Token-Kontext (z. B. Seeder)
        }
    }

    @Override
    @Transactional
    public String mitarbeiterName(Long mitarbeiterId) {
        Mitarbeiter m = mitarbeiterId == null ? null : Mitarbeiter.<Mitarbeiter>findById(mitarbeiterId);
        return m == null ? "EBZ-Team" : m.anzeigeName;
    }

    @Override
    @Transactional
    public String personName(Long personId) {
        Person p = personId == null ? null : Person.<Person>findById(personId);
        return p == null ? "Unbekannt" : p.anzeigeName();
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
