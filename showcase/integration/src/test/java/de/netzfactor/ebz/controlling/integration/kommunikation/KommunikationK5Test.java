package de.netzfactor.ebz.controlling.integration.kommunikation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.BestaetigungsStatus;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.BestaetigungService;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.KommunikationApi;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K5 — <b>Pflicht-Bestätigungs-Workflow</b> auf den seit K1 vorhandenen Quittierungs-Feldern. Geprüft:
 * die Frist wird bei der Projektion gesetzt und das Selbstauskunfts-Gate listet die offene Kenntnisnahme
 * (bis sie quittiert ist); der {@code BestaetigungService} versendet vor Fristablauf <b>autonom eine
 * Erinnerung</b> (über die Event-Spine) und <b>eskaliert</b> nach Fristablauf (hartes
 * {@code hatUeberfaelligeBestaetigung}-Gate); der Cockpit-Report zeigt den Status. Eindeutige
 * Schlüssel/Personen pro Lauf wegen der persistenten Showcase-DB.
 */
@QuarkusTest
class KommunikationK5Test {

    @Inject
    KommunikationApi kommunikation;

    @Inject
    BestaetigungService bestaetigung;

    @Inject
    PartyHoheitService party;

    private Person neuePerson(String sub, String name) {
        return party.selbstRegistrieren(sub, sub + "@ebz.de", name);
    }

    private PersonEreignis pflichtEreignis(Long personId, String betreff, String key) {
        return kommunikation.protokolliere(KommunikationsEreignis.mitKontext(
                EreignisTyp.AZUBI_VERTRAG_BESTAETIGT, personId, betreff, KontextTyp.ANMELDUNG, null, null, key));
    }

    @Test
    @TestSecurity(user = "k5-anna-sub")
    void fristGesetzt_undGateZeigtOffeneBisQuittiert() {
        Person anna = neuePerson("k5-anna-sub", "Anna Auszubildende");
        String betreff = "Kenntnisnahme Vertrag " + System.nanoTime();
        PersonEreignis pe = pflichtEreignis(anna.id, betreff, "k5:anna:" + System.nanoTime());

        // Frist = Eingang + 14 Tage (aus EreignisTyp.bestaetigungFristTage); Status zunächst OFFEN.
        assertNotNull(pe.bestaetigenBis, "Frist gesetzt");
        assertEquals(pe.zeitpunkt.plusDays(14), pe.bestaetigenBis);
        assertEquals(BestaetigungsStatus.OFFEN, pe.status(LocalDateTime.now()));

        // Portal-Gate: die offene Pflicht-Kenntnisnahme erscheint in der Selbstauskunft.
        given().when().get("/kommunikation/portal/bestaetigungen/offen").then().statusCode(200)
                .body("betreff", hasItem(betreff))
                .body("find { it.betreff == '%s' }.status".formatted(betreff), org.hamcrest.Matchers.is("OFFEN"));

        // Quittieren → fällt aus dem Gate; Status BESTAETIGT.
        given().when().post("/kommunikation/portal/ereignisse/" + pe.id + "/bestaetigen").then().statusCode(204);

        given().when().get("/kommunikation/portal/bestaetigungen/offen").then().statusCode(200)
                .body("betreff", not(hasItem(betreff)));

        BestaetigungsStatus status = QuarkusTransaction.requiringNew().call(
                () -> ((PersonEreignis) PersonEreignis.findById(pe.id)).status(LocalDateTime.now()));
        assertEquals(BestaetigungsStatus.BESTAETIGT, status);
    }

    @Test
    @TestSecurity(user = "k5-bert-sub")
    void erinnerung_wirdVorFristablaufAutonomVersendet() {
        Person bert = neuePerson("k5-bert-sub", "Bert Bauer");
        PersonEreignis pe = pflichtEreignis(bert.id, "Vertrag Bert " + System.nanoTime(),
                "k5:bert:" + System.nanoTime());

        // Erinnerungs-/Eskalations-Lauf (Test-Nachfass-Intervall PT0S → sofort fällig, Frist noch offen).
        bestaetigung.verarbeiteFaellige();

        QuarkusTransaction.requiringNew().run(() -> {
            PersonEreignis frisch = PersonEreignis.findById(pe.id);
            assertNotNull(frisch.erinnertAm, "Erinnerung vermerkt");
            assertTrue(frisch.erinnerungen >= 1, "Erinnerungszähler erhöht");
            assertEquals(BestaetigungsStatus.OFFEN, frisch.status(LocalDateTime.now()), "noch nicht eskaliert");
        });

        // Über die Spine ist ein KI-/System-Erinnerungs-Ereignis für die Person entstanden.
        long erinnerungen = QuarkusTransaction.requiringNew().call(() -> PersonEreignis.count(
                "empfaengerPersonId = ?1 and ereignisTyp = ?2", bert.id, EreignisTyp.BESTAETIGUNG_ERINNERUNG));
        assertTrue(erinnerungen >= 1, "Erinnerungs-Ereignis im Aktivitätslog");
    }

    @Test
    @TestSecurity(user = "k5-cora-sub")
    void eskalation_nachFristablauf_undHartesGate() {
        Person cora = neuePerson("k5-cora-sub", "Cora Cardinal");
        PersonEreignis pe = pflichtEreignis(cora.id, "Vertrag Cora " + System.nanoTime(),
                "k5:cora:" + System.nanoTime());

        // Frist künstlich in die Vergangenheit ziehen → der Lauf muss eskalieren.
        QuarkusTransaction.requiringNew().run(() -> {
            PersonEreignis x = PersonEreignis.findById(pe.id);
            x.bestaetigenBis = LocalDateTime.now().minusDays(1);
        });

        bestaetigung.verarbeiteFaellige();

        QuarkusTransaction.requiringNew().run(() -> {
            PersonEreignis frisch = PersonEreignis.findById(pe.id);
            assertNotNull(frisch.eskaliertAm, "eskaliert");
            assertEquals(BestaetigungsStatus.ESKALIERT, frisch.status(LocalDateTime.now()));
        });

        // Hartes Gate: andere Domänen-Flows können auf eine überfällige Pflicht-Kenntnisnahme blockieren.
        boolean ueberfaellig = QuarkusTransaction.requiringNew().call(
                () -> kommunikation.hatUeberfaelligeBestaetigung(cora.id));
        assertTrue(ueberfaellig, "überfällige Pflicht-Bestätigung blockiert das Gate");
    }

    @Test
    @TestSecurity(user = "k5-staff-sub", roles = "crm-pflege")
    void cockpitReport_zeigtPflichtKenntnisnahmenMitStatus() {
        Person dora = neuePerson("k5-dora-sub", "Dora Doktorandin");
        PersonEreignis pe = pflichtEreignis(dora.id, "Vertrag Dora " + System.nanoTime(),
                "k5:dora:" + System.nanoTime());

        // Eskalieren, damit der Report einen eindeutigen, prüfbaren Status zeigt.
        QuarkusTransaction.requiringNew().run(() -> {
            PersonEreignis x = PersonEreignis.findById(pe.id);
            x.bestaetigenBis = LocalDateTime.now().minusDays(2);
        });
        bestaetigung.verarbeiteFaellige();

        given().when().get("/kommunikation/admin/bestaetigungen").then().statusCode(200)
                .body("find { it.ereignisId == %d }.personName".formatted(pe.id),
                        org.hamcrest.Matchers.is("Dora Doktorandin"))
                .body("find { it.ereignisId == %d }.status".formatted(pe.id),
                        org.hamcrest.Matchers.is("ESKALIERT"));
    }
}
