package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import java.util.List;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.ExterneBestellung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zahlungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;
import de.netzfactor.ebz.controlling.integration.rechnung.service.BestellungBillingService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * Buchung im Kontext — der Naht-Punkt, der den Party-Kern an die Abrechnung andockt: Eingang ist
 * <i>wer bucht für wen in welchem Kontext</i> (Identität + Bestellkontext), nicht mehr eine nackte
 * Debitor-ID. Daraus wird der zahlungspflichtige Debitor über {@link PartyHoheitService#ermittleDebitor}
 * projiziert und eine {@link Anmeldung} mit Identitäts-Provenienz angelegt; der bestehende
 * Rechnungslauf (R1/R6) verarbeitet sie unverändert weiter.
 * <p>
 * Bildet die volle R1-Strecke ab (Berufsschule, halbjährlich). {@code teilnehmerName/email} werden als
 * Anzeige-Snapshot aus der Teilnehmer-{@link Person} gezogen; die {@code *PersonId}/{@code kontext}-
 * Felder halten die Verknüpfung für die 360°-Sicht.
 */
@ApplicationScoped
public class BuchungService {

    @Inject
    PartyHoheitService party;

    @Inject
    BestellungBillingService bestellungBilling;

    /** Buchungsauftrag: Teilnehmer + Besteller (Identitäten) + gewählter Kontext + Berufsschul-Daten. */
    public record Berufsschulbuchung(Long teilnehmerPersonId, Long bestellerPersonId,
            Long kontextOrganisationId, String schuljahr, int halbjahr, Zimmerart zimmerart,
            int unterrichtBetragCent, Integer uebernachtungBetragCent) {
    }

    @Transactional
    public Anmeldung bucheBerufsschule(Berufsschulbuchung b) {
        Person teilnehmer = Person.findById(b.teilnehmerPersonId());
        if (teilnehmer == null) {
            throw RegelVerletzung.nichtGefunden("Teilnehmer-Person nicht gefunden: " + b.teilnehmerPersonId());
        }
        Long bestellerId = b.bestellerPersonId() == null ? b.teilnehmerPersonId() : b.bestellerPersonId();

        // Kontext → zahlungspflichtiger Debitor (privat = eigener; Firma = Org-Debitor, Berechtigung geprüft)
        Debitor debitor = party.ermittleDebitor(bestellerId, b.kontextOrganisationId(), Bereich.BERUFSSCHULE);

        Anmeldung a = new Anmeldung();
        a.typ = AnmeldungTyp.BERUFSSCHULE;
        a.teilnehmerName = teilnehmer.anzeigeName;
        a.teilnehmerEmail = primaerEmail(teilnehmer.id);
        a.teilnehmerPersonId = teilnehmer.id;
        a.bestellerPersonId = bestellerId;
        a.kontextOrganisationId = b.kontextOrganisationId();
        a.zahlungspflichtigerDebitorId = debitor.id;
        a.status = AnmeldungStatus.AKTIV;
        a.schuljahr = b.schuljahr();
        a.halbjahr = b.halbjahr();
        a.zimmerart = b.zimmerart();
        a.unterrichtBetragCent = b.unterrichtBetragCent();
        a.uebernachtungBetragCent = b.uebernachtungBetragCent();
        a.persist();
        return a;
    }

    /** Externe Shop-Bestellung (R7) im gewählten Kontext: Käufer + Kontext + Bereich + Positionen. */
    public record Shopbestellung(String quelle, String externeId, Zahlungsart zahlungsart, Bereich bereich,
            String kaeuferEmail, String kaeuferName, Long kontextOrganisationId,
            List<ExterneBestellung.Position> positionen) {
    }

    /**
     * R7 über den Party-Kern: eine externe Bestellung (z. B. bezahlte Vendure-Order) wird identitäts-
     * und kontextgeführt abgerechnet. Der Käufer wird über die E-Mail zur {@link Person} aufgelöst
     * (Shop-Gast = provisorisch, später claimbar); der zahlungspflichtige Debitor folgt dem Kontext
     * (privat vs. im Auftrag der Organisation) statt einer mitgelieferten Debitor-DTO. Idempotent über
     * {@code quelle|externeId} (gemeinsamer Beleg-Bauer mit dem klassischen R7-Weg).
     */
    @Transactional
    public Rechnung ausShopBestellung(Shopbestellung b) {
        Bereich bereich = b.bereich() == null ? Bereich.SHOP : b.bereich();
        Person kaeufer = party.findeOderLegePerson(b.kaeuferEmail(), b.kaeuferName());
        Debitor debitor = party.ermittleDebitor(kaeufer.id, b.kontextOrganisationId(), bereich);
        return bestellungBilling.belegAusBestellung(b.quelle(), b.externeId(), b.zahlungsart(),
                bereich, debitor.id, b.positionen());
    }

    /**
     * Firmensicht (DSGVO-Scope): <b>nur</b> Buchungen, die im Kontext dieser Organisation getätigt
     * wurden. Privatbuchungen ({@code kontextOrganisationId == null}) und Buchungen im Kontext anderer
     * Organisationen sind hier strukturell ausgeschlossen — das Firmenportal sieht keine Privatbuchungen.
     */
    public java.util.List<Anmeldung> firmensicht(Long organisationId) {
        return Anmeldung.list("kontextOrganisationId", organisationId);
    }

    /** 360°-Sicht auf eine Identität: alle Anmeldungen, in denen die Person Teilnehmer ist (intern/Selbst). */
    public java.util.List<Anmeldung> personensicht(Long personId) {
        return Anmeldung.list("teilnehmerPersonId", personId);
    }

    private static String primaerEmail(Long personId) {
        PersonEmail e = PersonEmail.find("personId = ?1 and primaer = true", personId).firstResult();
        if (e == null) {
            e = PersonEmail.find("personId", personId).firstResult();
        }
        return e == null ? null : e.email;
    }
}
