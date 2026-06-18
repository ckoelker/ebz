package de.netzfactor.ebz.controlling.integration.shop;

import java.time.LocalDate;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

import de.netzfactor.ebz.controlling.integration.party.model.Login;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * Seedet beim Start idempotent eine kleine Käufer-Organisation für den Shop-Checkout-Demo: die im
 * Keycloak-Realm {@code ebz-customers} vorhandene Beispielkundin (Login {@code customer@ebz.de}) wird
 * als Party-{@link Person} samt {@link Organisation} und zwei Kolleg:innen angelegt. So liefert der
 * Teilnehmer-Picker ({@link TeilnehmerVorschlagService}) echte „Personen der Käufer-Organisation".
 * <p>
 * Idempotent über die {@link Login}-E-Mail. Läuft nach {@link de.netzfactor.ebz.controlling.integration.party.service.LookupSeeder}
 * ({@link Priority} hinter dem Default), Rolle wird zur Sicherheit per find-or-create aufgelöst.
 */
@ApplicationScoped
public class PartyDemoSeeder {

    private static final Logger LOG = Logger.getLogger(PartyDemoSeeder.class);
    private static final String KAEUFER_LOGIN = "customer@ebz.de";

    @Transactional
    void seed(@Observes @Priority(Interceptor.Priority.APPLICATION + 1000) StartupEvent ev) {
        if (Login.find("lower(loginEmail) = ?1", KAEUFER_LOGIN).firstResult() != null) {
            return;
        }
        Lookups.Rolle rolle = rolle();

        Organisation org = new Organisation();
        org.name = "Muster Immobilien GmbH";
        org.rechtsform = "GmbH";
        org.status = Organisation.Status.AKTIV;
        org.persist();

        Person carla = person("Carla", "Kundin", Person.Geschlecht.WEIBLICH, null);
        Login login = new Login();
        login.person = carla;
        login.loginEmail = KAEUFER_LOGIN;
        login.verifiziert = true;
        login.persist();
        mitgliedschaft(carla, org, rolle, true);

        mitgliedschaft(person("Jens", "Hofmann", Person.Geschlecht.MAENNLICH, "Dr."), org, rolle, false);
        mitgliedschaft(person("Petra", "Albrecht", Person.Geschlecht.WEIBLICH, null), org, rolle, false);

        LOG.infof("Party-Demo geseedet: Organisation '%s' mit 3 Personen (Käufer %s)", org.name, KAEUFER_LOGIN);
    }

    private Person person(String vorname, String nachname, Person.Geschlecht g, String titel) {
        Person p = new Person();
        p.vorname = vorname;
        p.nachname = nachname;
        p.geschlecht = g;
        p.titel = titel;
        p.status = Person.Status.AKTIV;
        p.loeschStatus = Person.LoeschStatus.AKTIV;
        p.persist();
        return p;
    }

    private void mitgliedschaft(Person p, Organisation org, Lookups.Rolle rolle, boolean haupt) {
        Mitgliedschaft m = new Mitgliedschaft();
        m.person = p;
        m.organisation = org;
        m.rolle = rolle;
        m.hauptzugehoerigkeit = haupt;
        m.buchungsberechtigt = haupt;
        m.gueltigVon = LocalDate.now();
        m.persist();
    }

    private Lookups.Rolle rolle() {
        Lookups.Rolle r = Lookups.Rolle.find("code", "SEMINAR_BUCHER").firstResult();
        if (r == null) {
            r = new Lookups.Rolle();
            r.code = "SEMINAR_BUCHER";
            r.bezeichnung = "Seminarbucher:in";
            r.sortierung = 0;
            r.persist();
        }
        return r;
    }
}
