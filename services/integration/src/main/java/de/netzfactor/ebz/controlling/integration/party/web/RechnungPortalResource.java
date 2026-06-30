package de.netzfactor.ebz.controlling.integration.party.web;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.security.Authenticated;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.KundenRechnungService;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdDaten;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdMapper;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

/**
 * Kunden-Rechnungsabruf im Außenportal (Realm {@code ebz-customers}): der eingeloggte Kunde sieht seine
 * <b>eigenen, festgeschriebenen</b> Belege — kontext-skopiert (privat als Selbstzahler vs. je Firma, für
 * die er buchungsberechtigt ist) — und lädt deren ZUGFeRD-E-Rechnung als PDF herunter. Autorisierung
 * läuft über den Token-{@code sub} → {@link Person}; die Sichtbarkeit liefert {@link KundenRechnungService}
 * (keine interne {@code rechnung-pflege}-Rolle, kein Zugriff auf fremde Belege/Entwürfe).
 */
@Path("/party/portal")
@Produces(MediaType.APPLICATION_JSON)
public class RechnungPortalResource {

    @Inject
    KundenRechnungService kundenRechnung;

    @Inject
    ZugferdService zugferd;

    @Inject
    RechnungZugferdMapper zugferdMapper;

    /** Ein wählbarer Rechnungs-Kontext des Logins (PRIVAT bzw. eine buchungsberechtigte Firma). */
    public record RechnungsKontextView(String art, Long organisationId, String bezeichnung) {
    }

    /** Kunden-Lese-Sicht eines Belegs (ohne interne Felder). PDF unter {@code /rechnungen/{id}/zugferd}. */
    public record PortalRechnungView(Long id, String nummer, Belegart belegart, Bereich bereich,
            RechnungStatus status, LocalDate ausstellungsdatum, int zahlungszielTage, long summeCent,
            String zeitraumBezeichnung, String empfaengerName) {
    }

    /** Die wählbaren Kontexte des Logins — speist die Kontext-Auswahl im Portal. */
    @Authenticated
    @GET
    @Path("/rechnungs-kontexte")
    @Transactional
    public List<RechnungsKontextView> rechnungsKontexte(@Context SecurityContext ctx) {
        Person aufrufer = mussAufrufer(ctx);
        return kundenRechnung.kontexte(aufrufer.id).stream()
                .map(k -> new RechnungsKontextView(k.art().name(), k.organisationId(), k.bezeichnung()))
                .toList();
    }

    /**
     * Festgeschriebene Belege eines Kontexts. Ohne {@code organisationId} ⇒ privat (Selbstzahler);
     * mit {@code organisationId} der Firmenkontext (Buchungsberechtigung vorausgesetzt, sonst 403).
     */
    @Authenticated
    @GET
    @Path("/rechnungen")
    @Transactional
    public List<PortalRechnungView> meineRechnungen(@QueryParam("organisationId") Long organisationId,
            @Context SecurityContext ctx) {
        Person aufrufer = mussAufrufer(ctx);
        return kundenRechnung.rechnungenImKontext(aufrufer.id, organisationId).stream()
                .map(RechnungPortalResource::toView).toList();
    }

    /** Lädt die ZUGFeRD-E-Rechnung (PDF/A-3 + EN-16931-XML) eines eigenen Belegs herunter. */
    @Authenticated
    @GET
    @Path("/rechnungen/{id}/zugferd")
    @Produces("application/pdf")
    @Transactional
    public Response zugferd(@PathParam("id") Long id, @Context SecurityContext ctx) {
        Person aufrufer = mussAufrufer(ctx);
        Rechnung r = kundenRechnung.meineRechnung(aufrufer.id, id); // 404/403 via RegelVerletzung
        try {
            RechnungZugferdDaten daten = zugferdMapper.baue(r);
            ZugferdService.Ergebnis erg = zugferd.erzeugeUndValidiere(daten);
            if (!erg.valide()) {
                return Response.status(Response.Status.BAD_GATEWAY)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new Fehler("E-Rechnung konnte nicht erzeugt werden (Validierung). Bitte EBZ kontaktieren."))
                        .build();
            }
            return Response.ok(erg.pdf())
                    .header("Content-Disposition", "attachment; filename=\"rechnung-" + r.nummer + ".pdf\"")
                    .build();
        } catch (Exception ex) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new Fehler("E-Rechnung konnte nicht erzeugt werden. Bitte EBZ kontaktieren."))
                    .build();
        }
    }

    private record Fehler(String message) {
    }

    /** Token-{@code sub} → bekannte {@link Person}; 403 (via {@link RegelVerletzung}), wenn keine Identität dahinter steht. */
    private Person mussAufrufer(SecurityContext ctx) {
        String sub = ctx.getUserPrincipal() == null ? null : ctx.getUserPrincipal().getName();
        Person aufrufer = kundenRechnung.aufrufer(sub);
        if (aufrufer == null) {
            throw new RegelVerletzung(403, "Zu Ihrem Login ist keine Kundenidentität bekannt.");
        }
        return aufrufer;
    }

    private static PortalRechnungView toView(Rechnung r) {
        return new PortalRechnungView(r.id, r.nummer, r.belegart, r.bereich, r.status, r.ausstellungsdatum,
                r.zahlungszielTage, r.summeCent(), r.zeitraumBezeichnung,
                r.debitor == null ? null : r.debitor.name);
    }
}
