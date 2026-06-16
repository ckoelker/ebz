package de.netzfactor.ebz.controlling.integration.lms.web;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;

import de.netzfactor.ebz.controlling.integration.lms.model.EinschreibungStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.service.KurseinschreibungService;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * „Meine Trainings" im Außenportal (Realm {@code ebz-customers}, pfadbasiert auf den {@code customers}-
 * Tenant aufgelöst — vgl. {@code PortalTenantResolver}). Der eingeloggte Lernende sieht seine
 * <b>eigenen</b> WBT-Einschreibungen (kontext-skopiert über den Token-{@code sub}, wie der
 * Rechnungsabruf) und startet einen eingeschriebenen Kurs per SSO-Deeplink in OpenOLAT.
 * <p>
 * Der {@code launchUrl} zeigt auf {@code {openolat.public-url}/url/RepositoryEntry/{openolatKey}} — die
 * OpenOLAT-„Jump-in"-URL, die im Browser die Keycloak-SSO-Anmeldung auslöst und den Kurs öffnet. Er wird
 * nur für {@code EINGESCHRIEBEN}e Trainings gesetzt (vorher ist der Kurs nicht zugänglich).
 */
@Path("/lms/portal")
@Tag(name = "LMS Portal")
@Produces(MediaType.APPLICATION_JSON)
public class LmsPortalResource {

    @Inject
    KurseinschreibungService service;

    @Inject
    Prozessspur prozess;

    @Inject
    JsonWebToken jwt;

    @ConfigProperty(name = "openolat.public-url")
    String openolatPublicUrl;

    /** Kunden-Lese-Sicht eines Trainings (ohne interne Outbox-Felder). */
    public record MeinTrainingView(Long einschreibungId, String kursTitel, String kursCode,
            EinschreibungStatus status, String launchUrl) {
    }

    /**
     * Eigen-skopiert über den OIDC-{@code sub} (UUID) aus dem {@link JsonWebToken} — genau der Schlüssel,
     * unter dem die Einschreibung gespeichert ist und den OpenOLAT als {@code KEYCLOAK}-Auth führt.
     * Bewusst NICHT {@code SecurityContext#getUserPrincipal().getName()} (das wäre der {@code preferred_username}).
     */
    @Authenticated
    @GET
    @Path("/trainings")
    @Transactional
    public List<MeinTrainingView> meineTrainings() {
        // USER_TASK in der Nutzungs-Phase: der Kunde ruft seine Trainings im Portal ab (Launch via SSO-Deeplink).
        prozess.schritt("Meine Trainings abrufen", Akteur.KUNDE, Prozess.System.PORTAL,
                Typ.USER_TASK, Phase.WBT_NUTZUNG);
        return service.meineTrainings(jwt.getSubject()).stream().map(this::toView).toList();
    }

    private MeinTrainingView toView(Kurseinschreibung e) {
        boolean startbar = e.status == EinschreibungStatus.EINGESCHRIEBEN && e.wbtKurs.openolatKey != null;
        String launchUrl = startbar
                ? openolatPublicUrl + "/url/RepositoryEntry/" + e.wbtKurs.openolatKey
                : null;
        return new MeinTrainingView(e.id, e.wbtKurs.titel, e.wbtKurs.code, e.status, launchUrl);
    }
}
