package de.netzfactor.ebz.controlling.integration.hubspot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.hubspot.adapter.HubSpotMockSenke;
import de.netzfactor.ebz.controlling.integration.hubspot.adapter.HubSpotMockSenke.Aufruf;
import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.service.HubSpotSyncService;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.ObjektTyp;
import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * H2 — Recht auf Vergessen (Art. 17), Firmen-Sync inkl. Marketing-Merkmale (Branche/Verband) und
 * Kontakt↔Firma-Association. GDPR-Delete ist hier aktiv (Default); der Archiv-Fallback wird separat in
 * {@code HubSpotErasureManuellTest} geprüft.
 */
@QuarkusTest
class HubSpotErasureCompanyTest {

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
    void recht_auf_vergessen_loescht_permanent_und_entfernt_mapping() {
        Long pid = personMitMapping("vergessen@example.test");
        QuarkusTransaction.requiringNew().run(() -> {
            Person p = Person.findById(pid);
            p.loeschStatus = Person.LoeschStatus.ANONYMISIERT;
        });
        mock.leeren();

        sync.enqueueErasure(pid);
        sync.verarbeiteFaellige(50);

        assertEquals(1, mock.aufrufeVon("gdprLoesche").size());
        assertEquals(ObjektTyp.CONTACT, mock.aufrufeVon("gdprLoesche").get(0).objektTyp());
        assertEquals(0, hubspotPersonMappings(pid));
    }

    @Test
    void company_sync_uebertraegt_branche_und_verband() {
        Long oid = neueOrganisation();

        sync.enqueueCompany(oid);
        sync.verarbeiteFaellige(50);

        assertEquals(1, mock.aufrufeVon("upsertCompany").size());
        Aufruf c = mock.aufrufeVon("upsertCompany").get(0);
        assertFalse(c.properties().get("branche").isBlank(), "Branche fehlt im Company-Sync");
        assertFalse(c.properties().get("verbaende").isBlank(), "Verband fehlt im Company-Sync");
        assertEquals(1, hubspotOrgMappings(oid));
    }

    @Test
    void association_verknuepft_contact_und_company() {
        Long pid = personMitMapping("assoc@example.test");
        Long oid = neueOrganisation();
        Long mid = neueMitgliedschaft(pid, oid);

        // Firma zuerst syncen (Contact ist über personMitMapping bereits gemappt) …
        sync.enqueueCompany(oid);
        sync.verarbeiteFaellige(50);
        // … dann die Verknüpfung.
        sync.enqueueAssociation(mid);
        sync.verarbeiteFaellige(50);

        assertEquals(1, mock.aufrufeVon("verknuepfe").size());
    }

    // ───────────────────────── Helfer ─────────────────────────

    private long hubspotPersonMappings(Long personId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ExterneId.count("quelle = ?1 and person.id = ?2", Quelle.HUBSPOT, personId));
    }

    private long hubspotOrgMappings(Long orgId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ExterneId.count("quelle = ?1 and organisation.id = ?2", Quelle.HUBSPOT, orgId));
    }

    /** Legt eine consent-positive Person an und synchronisiert sie einmal → HubSpot-Mapping existiert. */
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

    private Long neueOrganisation() {
        return QuarkusTransaction.requiringNew().call(() -> {
            long nano = System.nanoTime();
            Organisation o = new Organisation();
            o.name = "Test GmbH " + nano;
            o.status = Organisation.Status.AKTIV;
            o.website = "https://test-" + nano + ".example";
            o.branche = ersteOder(Lookups.Branche.findAll().firstResult(),
                    () -> lookup(new Lookups.Branche(), "HS_BR_" + nano, "Testbranche"));
            o.persist();
            Lookups.Verband v = ersteOder(Lookups.Verband.findAll().firstResult(),
                    () -> lookup(new Lookups.Verband(), "HS_VB_" + nano, "Testverband"));
            o.verbandszugehoerigkeiten.add(v);
            return o.id;
        });
    }

    private Long neueMitgliedschaft(Long pid, Long oid) {
        return QuarkusTransaction.requiringNew().call(() -> {
            long nano = System.nanoTime();
            Mitgliedschaft m = new Mitgliedschaft();
            m.person = Person.findById(pid);
            m.organisation = Organisation.findById(oid);
            m.rolle = ersteOder(Lookups.Rolle.findAll().firstResult(),
                    () -> lookup(new Lookups.Rolle(), "HS_RO_" + nano, "Testrolle"));
            m.persist();
            return m.id;
        });
    }

    private static <T extends Lookups.LookupBase> T ersteOder(T vorhanden, java.util.function.Supplier<T> neu) {
        return vorhanden != null ? vorhanden : neu.get();
    }

    private static <T extends Lookups.LookupBase> T lookup(T l, String code, String bezeichnung) {
        l.code = code;
        l.bezeichnung = bezeichnung;
        l.persist();
        return l;
    }
}
