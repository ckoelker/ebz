package de.netzfactor.ebz.controlling.integration.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.Login;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * Liefert Teilnehmer-Vorschläge für den Shop-Checkout: die <b>Personen der Käufer-Organisation(en)</b>.
 * Der/die angemeldete Besteller:in wird über die Login-E-Mail (bzw. Keycloak-{@code sub}) im Party-Kern
 * aufgelöst; daraus die aktiven {@link Mitgliedschaft}en → Organisationen → alle dort aktiven Personen
 * (Kolleg:innen, inkl. Besteller:in). So kann eine Firma im Checkout ihre Mitarbeiter:innen auswählen,
 * statt sie neu zu tippen. Findet sich keine Person/Org, kommt eine leere Liste zurück (UI bietet dann
 * nur „neu eingeben").
 */
@ApplicationScoped
public class TeilnehmerVorschlagService {

    /** Vorschlag = anwählbare Person (Felder wie die MDM-Teilnehmererfassung im Shop). */
    public record Vorschlag(String vorname, String nachname, String titel, String geschlecht, String email) {
    }

    @Transactional
    public List<Vorschlag> fuerBesteller(String email, String sub) {
        Person besteller = aufloesen(email, sub);
        if (besteller == null) {
            return List.of();
        }
        // Aktive Org-Zugehörigkeiten der Besteller:in (gueltigBis offen).
        List<Long> orgIds = new ArrayList<>();
        for (Mitgliedschaft m : Mitgliedschaft.<Mitgliedschaft>list("person = ?1 and gueltigBis is null", besteller)) {
            if (m.organisation != null && !orgIds.contains(m.organisation.id)) {
                orgIds.add(m.organisation.id);
            }
        }
        if (orgIds.isEmpty()) {
            return List.of();
        }
        // Alle aktiven Personen dieser Organisationen (distinct, produktiv, nicht gesperrt).
        Map<Long, Vorschlag> distinct = new LinkedHashMap<>();
        for (Mitgliedschaft m : Mitgliedschaft.<Mitgliedschaft>list(
                "organisation.id in ?1 and gueltigBis is null", orgIds)) {
            Person p = m.person;
            if (p == null || p.loeschStatus != Person.LoeschStatus.AKTIV) {
                continue;
            }
            distinct.computeIfAbsent(p.id, id -> new Vorschlag(
                    p.vorname, p.nachname, p.titel,
                    p.geschlecht == null ? null : p.geschlecht.name(),
                    loginEmail(p)));
        }
        return new ArrayList<>(distinct.values());
    }

    private Person aufloesen(String email, String sub) {
        if (sub != null && !sub.isBlank()) {
            Person p = Person.find("keycloakSub", sub).firstResult();
            if (p != null) {
                return p;
            }
            Login l = Login.find("keycloakSub", sub).firstResult();
            if (l != null) {
                return l.person;
            }
        }
        if (email != null && !email.isBlank()) {
            Login l = Login.find("lower(loginEmail) = ?1", email.toLowerCase()).firstResult();
            if (l != null) {
                return l.person;
            }
        }
        return null;
    }

    private String loginEmail(Person p) {
        Login l = Login.find("person", p).firstResult();
        return l == null ? null : l.loginEmail;
    }
}
