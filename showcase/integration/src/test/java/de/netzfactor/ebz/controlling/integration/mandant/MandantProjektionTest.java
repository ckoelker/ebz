package de.netzfactor.ebz.controlling.integration.mandant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.model.MandantProjektion;
import de.netzfactor.ebz.controlling.integration.mandant.service.MandantProjektionService;

/**
 * M2-Beweis der Org-Projektions-Outbox: anfordern → Dispatcher → OpenOLAT-Org-Anlage, inkl. Idempotenz
 * (Dedupe je Mandant), Erfolg (openolatOrganisationKey zurückgeschrieben) und der Branding-cssClass-
 * Ableitung (EBZ-Kernmandant = Default-Theme → keine cssClass). OpenOLAT ist per
 * {@link FakeOpenolatOrganisationProvisioning} ersetzt; der Dispatcher ist im Test-Profil faktisch aus →
 * der Test treibt {@code verarbeiteFaellige()} selbst.
 */
@QuarkusTest
class MandantProjektionTest {

    @Inject
    MandantProjektionService service;

    @Inject
    FakeOpenolatOrganisationProvisioning openolat;

    @BeforeEach
    void setup() {
        openolat.reset();
    }

    private Long neuerMandant(Mandant.Vertragstyp typ) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Mandant m = new Mandant();
            m.schluessel = "PROJ-" + (System.nanoTime() % 1_000_000_000L);
            m.anzeigeName = "Projektions-Mandant";
            m.vertragstyp = typ;
            m.status = Mandant.Status.AKTIV;
            m.erstelltAm = Instant.now();
            m.persist();
            return m.id;
        });
    }

    private MandantProjektion.Status statusVon(Long projId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            MandantProjektion p = MandantProjektion.findById(projId);
            return p == null ? null : p.status;
        });
    }

    private Long orgKeyVon(Long mandantId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Mandant m = Mandant.findById(mandantId);
            return m == null ? null : m.openolatOrganisationKey;
        });
    }

    @Test
    void anfordernIstIdempotent() {
        Long mandantId = neuerMandant(Mandant.Vertragstyp.ENTERPRISE_FLAT);
        Long id1 = service.anfordern(mandantId).id;
        Long id2 = service.anfordern(mandantId).id;
        // Zweiter Aufruf reiht keinen zweiten offenen Auftrag ein → derselbe Datensatz.
        assertEquals(id1, id2);
    }

    @Test
    void projektionLegtOrgAnUndSchreibtKeyZurueck() {
        Long mandantId = neuerMandant(Mandant.Vertragstyp.ENTERPRISE_FLAT);
        Long projId = service.anfordern(mandantId).id;

        int verarbeitet = service.verarbeiteFaellige(50);

        assertEquals(MandantProjektion.Status.ERLEDIGT, statusVon(projId));
        assertEquals(4242L, orgKeyVon(mandantId));
        assertEquals(1, openolat.ensureCalls.get(), "genau eine Org-Anlage");
        // Branding: B2B-Mandant bekommt eine per-Org-cssClass (M0-Anker).
        assertEquals("mandant-" + schluesselVon(mandantId), openolat.letzteCssKlasse);
        assertEquals(true, verarbeitet >= 1);
    }

    @Test
    void ebzKernmandantBleibtAufDefaultTheme() {
        // EBZ-Customer (B2C) → KEINE per-Org-cssClass (globales Default-Theme).
        Long mandantId = neuerMandant(Mandant.Vertragstyp.EBZ_CUSTOMER);
        service.anfordern(mandantId);
        service.verarbeiteFaellige(50);
        assertNull(openolat.letzteCssKlasse, "EBZ-Kernmandant ohne per-Org-cssClass");
    }

    private String schluesselVon(Long mandantId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Mandant m = Mandant.findById(mandantId);
            return m.schluessel.toLowerCase().replace('_', '-');
        });
    }
}
