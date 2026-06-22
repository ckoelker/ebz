package de.netzfactor.ebz.controlling.integration.hubspot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.hubspot.adapter.HubSpotMockSenke;
import de.netzfactor.ebz.controlling.integration.hubspot.adapter.HubSpotMockSenke.Aufruf;
import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.service.HubSpotSyncService;
import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * H1 — System-Test des Contact-Sync gegen die {@link HubSpotMockSenke}. Prüft die volle Consent-Matrix
 * (Opt-in → marketable, Widerruf/Werbesperre → nicht marketable, Auskunftssperre → kein Versand) sowie
 * Idempotenz des {@code ExterneId}-Mappings. Läuft ohne echtes HubSpot (Mock-Senke ist Default).
 */
@QuarkusTest
class HubSpotContactSyncTest {

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
    void optIn_wird_marketingfaehig_synchronisiert_und_ist_idempotent() {
        Long pid = neuePerson("opt-in@example.test", true, false, false);

        sync.enqueueContact(pid);
        sync.verarbeiteFaellige(50);

        assertEquals(1, mock.aufrufeVon("upsertContact").size());
        assertTrue(mock.aufrufeVon("upsertContact").get(0).marketingErlaubt());
        assertTrue(letzter("setzeMarketingStatus").marketingErlaubt());
        assertEquals(1, hubspotMappings(pid));

        // Zweiter Lauf: idempotent — kein zweites Mapping.
        sync.enqueueContact(pid);
        sync.verarbeiteFaellige(50);
        assertEquals(1, hubspotMappings(pid));
    }

    @Test
    void widerruf_schaltet_marketing_ab_ohne_neuanlage() {
        Long pid = neuePerson("widerruf@example.test", true, false, false);
        sync.enqueueContact(pid);
        sync.verarbeiteFaellige(50);
        mock.leeren();

        // Einwilligung widerrufen → Consent-Änderung spiegeln.
        QuarkusTransaction.requiringNew().run(() -> {
            Einwilligung e = Einwilligung.find("person.id", pid).firstResult();
            e.status = Einwilligung.Status.WIDERRUFEN;
        });
        sync.enqueueConsentChange(pid);
        sync.verarbeiteFaellige(50);

        // Fast-Path: nur Status spiegeln (kein erneutes upsertContact), Marketing aus.
        assertTrue(mock.aufrufeVon("upsertContact").isEmpty());
        assertFalse(letzter("setzeMarketingStatus").marketingErlaubt());
    }

    @Test
    void werbesperre_synchronisiert_aber_nicht_marketingfaehig() {
        Long pid = neuePerson("werbesperre@example.test", true, true, false);

        sync.enqueueContact(pid);
        sync.verarbeiteFaellige(50);

        assertEquals(1, mock.aufrufeVon("upsertContact").size());
        assertFalse(letzter("setzeMarketingStatus").marketingErlaubt());
        assertEquals(1, hubspotMappings(pid));
    }

    @Test
    void auskunftssperre_wird_nie_uebertragen() {
        Long pid = neuePerson("auskunftssperre@example.test", true, false, true);

        sync.enqueueContact(pid);
        sync.verarbeiteFaellige(50);

        assertTrue(mock.aufrufe().isEmpty());
        assertEquals(0, hubspotMappings(pid));
    }

    // ───────────────────────── Helfer ─────────────────────────

    private Aufruf letzter(String methode) {
        List<Aufruf> as = mock.aufrufeVon(methode);
        assertTrue(!as.isEmpty(), "kein Aufruf von " + methode);
        return as.get(as.size() - 1);
    }

    private long hubspotMappings(Long personId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ExterneId.count("quelle = ?1 and person.id = ?2", Quelle.HUBSPOT, personId));
    }

    private Long neuePerson(String email, boolean optIn, boolean werbesperre, boolean auskunftssperre) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Person p = new Person();
            p.vorname = "Test";
            p.nachname = "Person";
            p.status = Person.Status.AKTIV;
            p.werbesperre = werbesperre;
            p.auskunftssperre = auskunftssperre;
            p.matchSchluessel = "hs-test-" + System.nanoTime();
            p.persist();

            Kontaktpunkt k = new Kontaktpunkt();
            k.typ = Kontaktpunkt.Typ.EMAIL;
            k.email = email;
            k.primaer = true;
            k.person = p;
            k.persist();

            if (optIn) {
                Einwilligung e = new Einwilligung();
                e.person = p;
                e.kanal = Einwilligung.Kanal.EMAIL;
                e.zweck = Einwilligung.Zweck.NEWSLETTER;
                e.status = Einwilligung.Status.ERTEILT;
                e.persist();
            }
            return p.id;
        });
    }
}
