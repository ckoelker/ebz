package de.netzfactor.ebz.controlling.integration.mandant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.service.MandantLandingResolver;
import de.netzfactor.ebz.controlling.integration.mandant.service.MandantLandingResolver.MandantLandingException;

/**
 * M3c-Beweis der Landing-Regel A4: claim-/realm-basierte Auflösung eines Logins auf genau einen Mandanten,
 * fail-closed. Reiner Service-Test mit synthetischen Claim-Maps (kein OIDC/Token nötig).
 */
@QuarkusTest
class MandantLandingResolverTest {

    @Inject
    MandantLandingResolver resolver;

    private Mandant neuerMandant(Mandant.Vertragstyp typ, Mandant.Status status) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Mandant m = new Mandant();
            m.schluessel = "LAND-" + (System.nanoTime() % 1_000_000_000L);
            m.anzeigeName = "Landing-Mandant";
            m.vertragstyp = typ;
            m.status = status;
            m.erstelltAm = Instant.now();
            m.persist();
            return m;
        });
    }

    @Test
    void brokerMandantClaimLoestAufAktivenMandanten() {
        Mandant m = neuerMandant(Mandant.Vertragstyp.ENTERPRISE_FLAT, Mandant.Status.AKTIV);
        Mandant gelandet = resolver.aufloesen("ebz-customers", Map.of(MandantLandingResolver.CLAIM_MANDANT, m.schluessel));
        assertEquals(m.id, gelandet.id);
    }

    @Test
    void unbekannterMandantClaimWirdFailClosedAbgewiesen() {
        assertThrows(MandantLandingException.class, () ->
                resolver.aufloesen("ebz-customers", Map.of(MandantLandingResolver.CLAIM_MANDANT, "GIBT_ES_NICHT_" + System.nanoTime())));
    }

    @Test
    void gesperrterMandantClaimWirdFailClosedAbgewiesen() {
        Mandant m = neuerMandant(Mandant.Vertragstyp.ENTERPRISE_FLAT, Mandant.Status.GESPERRT);
        assertThrows(MandantLandingException.class, () ->
                resolver.aufloesen("ebz-customers", Map.of(MandantLandingResolver.CLAIM_MANDANT, m.schluessel)));
    }

    @Test
    void gebrokerterLoginOhneMandantClaimAberOrgMitgliedschaftWirdAbgewiesen() {
        // organization-Claim vorhanden (B2B-Mitglied), aber kein mandant-Claim → fail-closed (A4).
        Map<String, Object> claims = Map.of(MandantLandingResolver.CLAIM_ORGANIZATION,
                List.of(Map.of("irgendeine-org", Map.of("id", "x"))));
        assertThrows(MandantLandingException.class, () -> resolver.aufloesen("ebz-customers", claims));
    }

    @Test
    void direkterCustomerLoginOhneClaimsLandetAufEbzCustomerKernmandant() {
        neuerMandant(Mandant.Vertragstyp.EBZ_CUSTOMER, Mandant.Status.AKTIV);
        Mandant gelandet = resolver.aufloesen("ebz-customers", Map.of());
        assertEquals(Mandant.Vertragstyp.EBZ_CUSTOMER, gelandet.vertragstyp);
    }

    @Test
    void direkterStaffLoginOhneClaimsLandetAufEbzStaffKernmandant() {
        neuerMandant(Mandant.Vertragstyp.EBZ_STAFF, Mandant.Status.AKTIV);
        Mandant gelandet = resolver.aufloesen("ebz-staff", Map.of());
        assertEquals(Mandant.Vertragstyp.EBZ_STAFF, gelandet.vertragstyp);
    }
}
