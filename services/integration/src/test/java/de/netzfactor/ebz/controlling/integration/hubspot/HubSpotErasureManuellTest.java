package de.netzfactor.ebz.controlling.integration.hubspot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import de.netzfactor.ebz.controlling.integration.hubspot.adapter.HubSpotMockSenke;
import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.service.HubSpotSyncService;
import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * H2 — Recht auf Vergessen mit <b>abgeschaltetem</b> GDPR-Delete ({@code hubspot.sync.gdpr-delete.enabled=false}):
 * statt permanenter Löschung wird archiviert + Marketing deaktiviert, der Auftrag landet auf {@code MANUELL}
 * (endgültige Löschung übernimmt ein Mensch im Cockpit), und das Mapping bleibt zur Wiederauffindbarkeit erhalten.
 */
@QuarkusTest
@TestProfile(HubSpotErasureManuellTest.GdprAus.class)
class HubSpotErasureManuellTest {

    public static class GdprAus implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("hubspot.sync.gdpr-delete.enabled", "false");
        }
    }

    @Inject
    HubSpotSyncService sync;

    @Inject
    HubSpotMockSenke mock;

    @BeforeEach
    void reset() {
        mock.leeren();
        QuarkusTransaction.requiringNew().run(() -> {
            HubSpotSyncAuftrag.deleteAll();
            ExterneId.delete("quelle", Quelle.HUBSPOT);
        });
    }

    @Test
    void erasure_ohne_gdpr_delete_archiviert_und_stellt_auf_manuell() {
        Long pid = personMitMapping("manuell@example.test");
        QuarkusTransaction.requiringNew().run(() -> {
            Person p = Person.findById(pid);
            p.loeschStatus = Person.LoeschStatus.GESPERRT;
        });
        mock.leeren();

        HubSpotSyncAuftrag auftrag = sync.enqueueErasure(pid);
        sync.verarbeiteFaellige(50);

        assertEquals(1, mock.aufrufeVon("archiviere").size());
        assertTrue(mock.aufrufeVon("gdprLoesche").isEmpty(), "kein permanenter Delete erwartet");
        assertFalse(mock.aufrufeVon("setzeMarketingStatus").isEmpty());
        assertFalse(mock.aufrufeVon("setzeMarketingStatus").get(0).marketingErlaubt());
        // Mapping bleibt (manuelle Löschung folgt), Auftrag steht auf MANUELL.
        assertEquals(1, hubspotPersonMappings(pid));
        assertEquals(HubSpotSyncAuftrag.Status.MANUELL, statusVon(auftrag.id));
    }

    private long hubspotPersonMappings(Long personId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ExterneId.count("quelle = ?1 and person.id = ?2", Quelle.HUBSPOT, personId));
    }

    private HubSpotSyncAuftrag.Status statusVon(Long auftragId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ((HubSpotSyncAuftrag) HubSpotSyncAuftrag.findById(auftragId)).status);
    }

    private Long personMitMapping(String email) {
        Long pid = QuarkusTransaction.requiringNew().call(() -> {
            Person p = new Person();
            p.vorname = "Test";
            p.nachname = "Person";
            p.status = Person.Status.AKTIV;
            p.matchSchluessel = "hs-test-" + System.nanoTime();
            p.persist();
            Kontaktpunkt k = new Kontaktpunkt();
            k.typ = Kontaktpunkt.Typ.EMAIL;
            k.email = email;
            k.primaer = true;
            k.person = p;
            k.persist();
            Einwilligung e = new Einwilligung();
            e.person = p;
            e.kanal = Einwilligung.Kanal.EMAIL;
            e.zweck = Einwilligung.Zweck.NEWSLETTER;
            e.status = Einwilligung.Status.ERTEILT;
            e.persist();
            return p.id;
        });
        sync.enqueueContact(pid);
        sync.verarbeiteFaellige(50);
        return pid;
    }
}
