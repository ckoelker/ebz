package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
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
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

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

    @Inject
    Prozessspur prozess;

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
        a.teilnehmerName = teilnehmer.anzeigeName();
        a.teilnehmerEmail = primaerEmail(teilnehmer.id);
        a.teilnehmerPerson = teilnehmer;
        a.bestellerPerson = person(bestellerId);
        a.kontextOrganisation = org(b.kontextOrganisationId());
        a.zahlungspflichtigerDebitor = debitor;
        a.status = AnmeldungStatus.AKTIV;
        a.schuljahr = b.schuljahr();
        a.halbjahr = b.halbjahr();
        a.zimmerart = b.zimmerart();
        a.unterrichtBetragCent = b.unterrichtBetragCent();
        a.uebernachtungBetragCent = b.uebernachtungBetragCent();
        a.persist();
        return a;
    }

    /** Self-Service-Anmeldung eines Azubis durch den Firmen-Ansprechpartner (Portal, Schritt D). */
    public record AzubiAnmeldung(Long bestellerPersonId, Long organisationId, String azubiEmail,
            String azubiName, String schuljahr, int halbjahr, Zimmerart zimmerart,
            int unterrichtBetragCent, Integer uebernachtungBetragCent) {
    }

    /**
     * Schritt D: der buchungsberechtigte Ansprechpartner meldet einen Azubi an. Der Azubi wird (idempotent)
     * als provisorische Person mit {@code AZUBI}-Mitgliedschaft angelegt; die Anmeldung entsteht im
     * Firmenkontext mit dem projizierten Firmen-Debitor und Status <b>{@code ANGEFRAGT}</b> — also
     * <i>nicht</i> abrechenbar. Erst die EBZ-Bestätigung (E) und die Vertragsbestätigung der Firma (F)
     * führen sie auf {@code AKTIV}, das der bestehende Rechnungslauf bucht.
     */
    @Transactional
    public Anmeldung meldeAzubiAn(AzubiAnmeldung b) {
        prozess.schritt("Azubi anmelden", Akteur.FIRMA, Prozess.System.PORTAL, Typ.USER_TASK,
                Phase.AZUBI_ANMELDUNG);
        Person azubi = party.registriereTeilnehmer(b.organisationId(), b.azubiEmail(), b.azubiName(),
                "AZUBI", false);
        Debitor debitor = party.ermittleDebitor(b.bestellerPersonId(), b.organisationId(), Bereich.BERUFSSCHULE);

        Anmeldung a = new Anmeldung();
        a.typ = AnmeldungTyp.BERUFSSCHULE;
        a.teilnehmerName = azubi.anzeigeName();
        a.teilnehmerEmail = primaerEmail(azubi.id);
        a.teilnehmerPerson = azubi;
        a.bestellerPerson = person(b.bestellerPersonId());
        a.kontextOrganisation = org(b.organisationId());
        a.zahlungspflichtigerDebitor = debitor;
        a.status = AnmeldungStatus.ANGEFRAGT; // noch nicht abrechenbar (erst nach Vertragsbestätigung → AKTIV)
        a.schuljahr = b.schuljahr();
        a.halbjahr = b.halbjahr();
        a.zimmerart = b.zimmerart();
        a.unterrichtBetragCent = b.unterrichtBetragCent();
        a.uebernachtungBetragCent = b.uebernachtungBetragCent();
        a.persist();
        prozess.schritt("Anmeldung anlegen (ANGEFRAGT)", Akteur.SYSTEM, Prozess.System.BACKEND,
                Typ.SERVICE_TASK, Phase.AZUBI_ANMELDUNG);
        return a;
    }

    /** Hochschul-Buchung (R6): Studierende:r + Besteller + optionaler Firmenkontext (duales Studium). */
    public record Hochschulbuchung(Long teilnehmerPersonId, Long bestellerPersonId,
            Long kontextOrganisationId, String semester, int semesterbetragCent,
            Integer firmaAnteilCent, Integer ratenAnzahl) {
    }

    /**
     * R6 über den Party-Kern: Einschreibung Studierende:r → {@link Anmeldung} (HOCHSCHULE). Der
     * Eigenanteil geht an den privaten Debitor der/des Studierenden; bei dualem Studium
     * ({@code kontextOrganisationId} + {@code firmaAnteilCent}) trägt die Organisation ihren Anteil
     * über ihren Firmen-Debitor (zwei getrennte Forderungen, vom bestehenden Hochschul-Rechnungslauf
     * R6 erzeugt). Der Firmen-Debitor wird im Kontext des Bestellers (buchungsberechtigt) projiziert.
     */
    @Transactional
    public Anmeldung bucheHochschule(Hochschulbuchung b) {
        Person student = Person.findById(b.teilnehmerPersonId());
        if (student == null) {
            throw RegelVerletzung.nichtGefunden("Teilnehmer-Person nicht gefunden: " + b.teilnehmerPersonId());
        }
        Long bestellerId = b.bestellerPersonId() == null ? b.teilnehmerPersonId() : b.bestellerPersonId();

        // Eigenanteil → privater Debitor der/des Studierenden
        Debitor eigen = party.ermittleDebitor(student.id, null, Bereich.HOCHSCHULE);

        Anmeldung a = new Anmeldung();
        a.typ = AnmeldungTyp.HOCHSCHULE;
        a.teilnehmerName = student.anzeigeName();
        a.teilnehmerEmail = primaerEmail(student.id);
        a.teilnehmerPerson = student;
        a.bestellerPerson = person(bestellerId);
        a.kontextOrganisation = org(b.kontextOrganisationId());
        a.zahlungspflichtigerDebitor = eigen;
        a.status = AnmeldungStatus.AKTIV;
        a.semester = b.semester();
        a.semesterbetragCent = b.semesterbetragCent();
        a.ratenAnzahl = b.ratenAnzahl();

        // Duales Studium: Firmenanteil über den Firmen-Debitor (Kontext des Bestellers)
        if (b.kontextOrganisationId() != null && b.firmaAnteilCent() != null) {
            Debitor firma = party.ermittleDebitor(bestellerId, b.kontextOrganisationId(), Bereich.HOCHSCHULE);
            a.firmaDebitor = firma;
            a.firmaAnteilCent = b.firmaAnteilCent();
        }
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
        return Anmeldung.list("kontextOrganisation.id", organisationId);
    }

    /** 360°-Sicht auf eine Identität: alle Anmeldungen, in denen die Person Teilnehmer ist (intern/Selbst). */
    public java.util.List<Anmeldung> personensicht(Long personId) {
        return Anmeldung.list("teilnehmerPerson.id", personId);
    }

    private static String primaerEmail(Long personId) {
        return PartyHoheitService.primaerEmail(personId);
    }

    /** Lädt eine Person/Organisation per ID (FK-Ziel) bzw. {@code null} — für die Assoziations-Zuweisung. */
    private static Person person(Long id) {
        return id == null ? null : Person.findById(id);
    }

    private static Organisation org(Long id) {
        return id == null ? null : Organisation.findById(id);
    }
}
