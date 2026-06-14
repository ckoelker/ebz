package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;
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

    private static String primaerEmail(Long personId) {
        PersonEmail e = PersonEmail.find("personId = ?1 and primaer = true", personId).firstResult();
        if (e == null) {
            e = PersonEmail.find("personId", personId).firstResult();
        }
        return e == null ? null : e.email;
    }
}
