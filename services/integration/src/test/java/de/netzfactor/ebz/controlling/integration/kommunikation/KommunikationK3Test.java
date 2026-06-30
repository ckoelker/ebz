package de.netzfactor.ebz.controlling.integration.kommunikation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.GruppenService;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.ZustellService;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K3 — Person→Gruppe (Broadcast): manuelle Verteiler fächern pro Mitglied ein {@code GRUPPEN_INFO}-
 * Ereignis auf (Aktivitätslog + Kanäle); Werbe-/Auskunftssperre unterdrückt die E-Mail, das Portal bleibt
 * (Consent-Durchsetzung); ORGANISATION-Gruppen lösen ihre Mitglieder dynamisch aus den Mitgliedschaften auf.
 * Eindeutige Schlüssel pro Lauf wegen der persistenten Showcase-DB.
 */
@QuarkusTest
class KommunikationK3Test {

    @Inject
    GruppenService gruppen;

    @Inject
    PartyHoheitService party;

    @Inject
    ZustellService zustellService;

    @Inject
    MockMailbox mailbox;

    @Test
    void manuelleGruppe_broadcastAnAlleMitglieder() {
        long n = System.nanoTime();
        Person a = party.selbstRegistrieren("k3-a-" + n, "k3-a-" + n + "@ebz.de", "Anna A");
        Person b = party.selbstRegistrieren("k3-b-" + n, "k3-b-" + n + "@ebz.de", "Bert B");
        Personengruppe g = gruppen.anlegenManuell("Newsletter " + n, "Test-Verteiler");
        gruppen.mitgliedHinzu(g.id, a.id);
        gruppen.mitgliedHinzu(g.id, b.id);

        String text = "Wichtige Info an alle " + n;
        int erreicht = gruppen.broadcast(g.id, text);

        assertEquals(2, erreicht, "beide Mitglieder erreicht");
        assertTrue(zaehle(a.id, text) > 0, "Anna hat das Broadcast-Ereignis im Log");
        assertTrue(zaehle(b.id, text) > 0, "Bert hat das Broadcast-Ereignis im Log");
    }

    @Test
    void werbesperre_unterdruecktEmail_portalBleibt() {
        long n = System.nanoTime();
        String email = "k3-sperre-" + n + "@ebz.de";
        Person w = party.selbstRegistrieren("k3-sperre-" + n, email, "Wanda Werbesperre");
        QuarkusTransaction.requiringNew().run(() -> {
            Person p = Person.findById(w.id);
            p.werbesperre = true;
        });
        Personengruppe g = gruppen.anlegenManuell("Sperr-Verteiler " + n, null);
        gruppen.mitgliedHinzu(g.id, w.id);

        mailbox.clear();
        String text = "Broadcast mit Sperre " + n;
        gruppen.broadcast(g.id, text);
        zustellService.verarbeiteFaellige(100);

        assertTrue(mailbox.getMailMessagesSentTo(email).isEmpty(), "E-Mail durch Werbesperre unterdrückt");
        assertTrue(zaehle(w.id, text) > 0, "Portal-Eintrag bleibt trotz Sperre");
    }

    @Test
    void organisationGruppe_loestMitgliederDynamischAuf() {
        long n = System.nanoTime();
        Organisation o = party.legeOrganisationAn("Acme " + n, "Weg 1", "44137", "Dortmund", "DE",
                null, Organisation.Status.AKTIV);
        Person p1 = party.registriereTeilnehmer(o.id, "k3-org1-" + n + "@ebz.de", "Olaf Org", "AUSBILDER", false);
        Person p2 = party.registriereTeilnehmer(o.id, "k3-org2-" + n + "@ebz.de", "Petra Org", "AUSBILDER", false);

        Personengruppe g = gruppen.anlegenOrganisation("Acme-Kreis " + n, null, o.id);
        List<Long> mitglieder = gruppen.mitglieder(g.id);

        assertTrue(mitglieder.contains(p1.id) && mitglieder.contains(p2.id),
                "ORGANISATION-Gruppe löst die Mitgliedschaften dynamisch auf");
    }

    private static long zaehle(Long personId, String betreff) {
        return QuarkusTransaction.requiringNew().call(() ->
                PersonEreignis.count("empfaengerPersonId = ?1 and betreff = ?2", personId, betreff));
    }
}
