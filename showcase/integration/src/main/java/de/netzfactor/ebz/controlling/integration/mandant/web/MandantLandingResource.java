package de.netzfactor.ebz.controlling.integration.mandant.web;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;
import de.netzfactor.ebz.controlling.integration.mandant.service.MandantLandingResolver;
import de.netzfactor.ebz.controlling.integration.mandant.service.MandantLandingResolver.MandantLandingException;

/**
 * Kundenseitige Naht der Landing-Regel A4 (M3c): liefert für den eingeloggten ebz-customers-Login den
 * aufgelösten {@link Mandant} — oder {@code 403} (fail-closed). Liegt bewusst unter {@code /lms/portal/**},
 * damit der {@code PortalTenantResolver} den OIDC-Tenant {@code customers} (Realm {@code ebz-customers})
 * wählt. Der gebrokerte B2B-Login trägt den vom Kunden-IdP gestempelten {@code mandant}-Claim, der direkte
 * B2C-Login keinen → EBZ-Kernmandant. Macht die Brokering-Strecke E2E-prüfbar.
 */
@Path("/lms/portal/landing")
@Tag(name = "Mandanten")
@Produces(MediaType.APPLICATION_JSON)
public class MandantLandingResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    MandantLandingResolver resolver;

    /** Schlanke Sicht auf den gelandeten Mandanten (inkl. Branding-Anker für die SPA). */
    public record LandingDto(Long mandantId, String schluessel, String anzeigeName, String vertragstyp,
            String primaerFarbe, String logoUrl) {
    }

    @GET
    @Authenticated
    public Response landing() {
        String realm = realmAusIssuer(jwt.getIssuer());
        Map<String, Object> claims = new HashMap<>();
        for (String name : jwt.getClaimNames()) {
            claims.put(name, jwt.getClaim(name));
        }
        try {
            Mandant m = resolver.aufloesen(realm, claims);
            return Response.ok(new LandingDto(m.id, m.schluessel, m.anzeigeName, m.vertragstyp.name(),
                    m.primaerFarbe, m.logoUrl)).build();
        } catch (MandantLandingException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new MandantResource.Fehler(e.getMessage())).build();
        }
    }

    /** {@code http://…/realms/ebz-customers} → {@code ebz-customers}. */
    private static String realmAusIssuer(String iss) {
        if (iss == null) {
            return "";
        }
        int i = iss.lastIndexOf("/realms/");
        return i < 0 ? "" : iss.substring(i + "/realms/".length());
    }
}
