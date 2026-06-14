package de.netzfactor.ebz.controlling.integration.party.web;

import java.net.URI;
import java.util.List;

import io.quarkus.security.Authenticated;
import io.vertx.core.http.HttpServerRequest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.party.service.BuchungService;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;
import de.netzfactor.ebz.controlling.integration.party.service.RateLimiter;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ExterneBestellung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zahlungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;

/**
 * Party-Kern-API (Schema {@code party}): Identität (Person + E-Mails), N:M-Verknüpfung zu
 * Organisationen (Mitgliedschaft) und — der Kern — die <b>Bestellkontexte einer Identität</b> plus die
 * kontextabhängige Abrechnungs-Projektion auf den Debitor. Schreib-Ops verlangen {@code rechnung-pflege}
 * (Stammdaten-Hoheit); Lesen offen. DTOs als schlanke nested Records (Mass-Assignment-Schutz: nie
 * id/version/status aus dem Body).
 */
@Path("/party")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PartyResource {

    @Inject
    PartyHoheitService party;

    @Inject
    BuchungService buchung;

    @Inject
    RateLimiter rateLimiter;

    // ───────────────────────── DTOs (gebündelt) ─────────────────────────

    /** Admin-/Migrations-Anlage mit bekanntem externen {@code sub} (Bulk-Provisionierung). */
    public record SelbstRegistrierung(@NotBlank String keycloakSub, @NotBlank @Email String email,
            @NotBlank String anzeigeName) {
    }

    /** Selbst-Login des Endnutzers: der {@code sub} kommt aus dem Token (nicht aus dem Body). */
    public record Login(@NotBlank @Email String email, @NotBlank String anzeigeName) {
    }

    public record OrganisationDto(@NotBlank String name, String strasse, String plz, String ort,
            String land, String ustId) {
    }

    /**
     * Öffentliche Self-Service-Anfrage eines Ausbildungsbetriebs (kein Login). {@code website} ist ein
     * <b>Honeypot</b> — von echten Nutzern leer gelassen, von Bots oft befüllt; ein gefülltes Feld wird
     * still abgelehnt.
     */
    public record AusbildungsbetriebAnfrage(@NotBlank String name, String strasse, String plz, String ort,
            String land, String ustId, @NotBlank @Email String ansprechpartnerEmail,
            @NotBlank String ansprechpartnerName, String website) {
    }

    public record AnfrageView(Long organisationId, String organisationStatus, PersonView ansprechpartner) {
    }

    public record OrganisationView(Long id, String name, String plz, String ort, String ustId,
            String status, Long goldenOrganisationId) {
    }

    public record OrgMergeRequest(@NotNull Long quellId, @NotNull Long zielId) {
    }

    public record TeilnehmerAnlage(@NotBlank @Email String email, @NotBlank String anzeigeName,
            @NotNull Mitgliedschaft.Rolle rolle, boolean buchungsberechtigt) {
    }

    public record MergeRequest(@NotNull Long quellId, @NotNull Long zielId) {
    }

    public record PersonView(Long id, String keycloakSub, String anzeigeName, String plz, String ort,
            String status, Long goldenPersonId, List<String> emails, List<MitgliedschaftView> mitgliedschaften) {
    }

    public record MitgliedschaftView(Long organisationId, String organisation, Mitgliedschaft.Rolle rolle,
            boolean buchungsberechtigt) {
    }

    public record KontextView(String art, Long organisationId, String bezeichnung, List<Mitgliedschaft.Rolle> rollen) {
    }

    public record DebitorView(Long id, String debitorNr, String bereich, String rolle, String name) {
    }

    public record Berufsschulbuchung(@NotNull Long teilnehmerPersonId, Long bestellerPersonId,
            Long kontextOrganisationId, @NotBlank String schuljahr, int halbjahr,
            @NotNull Zimmerart zimmerart, int unterrichtBetragCent, Integer uebernachtungBetragCent) {
    }

    public record BuchungView(Long anmeldungId, Long teilnehmerPersonId, Long bestellerPersonId,
            Long kontextOrganisationId, Long zahlungspflichtigerDebitorId, String teilnehmerName) {
    }

    public record Hochschulbuchung(@NotNull Long teilnehmerPersonId, Long bestellerPersonId,
            Long kontextOrganisationId, @NotBlank String semester, int semesterbetragCent,
            Integer firmaAnteilCent, Integer ratenAnzahl) {
    }

    public record HochschulView(Long anmeldungId, Long teilnehmerPersonId, Long kontextOrganisationId,
            Long zahlungspflichtigerDebitorId, Long firmaDebitorId, Integer firmaAnteilCent, String teilnehmerName) {
    }

    public record BuchungZeile(Long anmeldungId, String teilnehmerName, Long teilnehmerPersonId,
            Long kontextOrganisationId, Long zahlungspflichtigerDebitorId, String schuljahr, Integer halbjahr) {
    }

    public record ShopBestellung(@NotBlank String quelle, @NotBlank String externeId,
            @NotNull Zahlungsart zahlungsart, Bereich bereich,
            @NotBlank @Email String kaeuferEmail, @NotBlank String kaeuferName,
            Long kontextOrganisationId, @Valid @NotEmpty List<ExterneBestellung.Position> positionen) {
    }

    public record ShopBelegView(Long rechnungId, Long debitorId, String bereich, String quelle, String externeId) {
    }

    // ───────────────────────── Identität ─────────────────────────

    /**
     * Admin-/Migrations-Anlage einer Identität mit bekanntem externen {@code sub} (Bulk-Provisionierung).
     * Der Endnutzer-Selbstlogin läuft über {@link #login} ({@code sub} aus dem Token).
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/personen/selbstregistrieren")
    @Transactional
    public Response selbstRegistrieren(@Valid SelbstRegistrierung req) {
        long vorher = Person.count();
        Person p = party.selbstRegistrieren(req.keycloakSub(), req.email(), req.anzeigeName());
        boolean neu = Person.count() > vorher;
        return Response.status(neu ? Response.Status.CREATED : Response.Status.OK)
                .entity(toView(p)).build();
    }

    /**
     * Selbst-Login des Endnutzers: der Login-Anker ({@code sub}) wird <b>aus dem Token</b> gelesen (nicht
     * aus dem Body — nicht fälschbar). Erstanmeldung legt eine neue aktive Person an (201) oder claimt
     * eine firmenseitig vor-angelegte (200). E-Mail/Name kommen produktiv aus verifizierten Token-Claims.
     */
    @Authenticated
    @POST
    @Path("/personen/login")
    @Transactional
    public Response login(@Valid Login req, @Context SecurityContext ctx) {
        String sub = ctx.getUserPrincipal().getName();
        long vorher = Person.count();
        Person p = party.selbstRegistrieren(sub, req.email(), req.anzeigeName());
        boolean neu = Person.count() > vorher;
        return Response.status(neu ? Response.Status.CREATED : Response.Status.OK)
                .entity(toView(p)).build();
    }

    @GET
    @Path("/personen/{id}")
    @Transactional
    public Response getPerson(@PathParam("id") Long id) {
        Person p = Person.findById(id);
        return p == null ? notFound() : Response.ok(toView(p)).build();
    }

    /** Merge-Kandidaten (gleicher Namensschlüssel) zur Dublettenauflösung. */
    @GET
    @Path("/personen/{id}/kandidaten")
    @Transactional
    public List<PersonView> kandidaten(@PathParam("id") Long id) {
        return party.kandidaten(id).stream().map(PartyResource::toView).toList();
    }

    /** Führt eine Dublette ({@code quellId}) in die Ziel-Person zusammen. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/personen/merge")
    @Transactional
    public PersonView merge(@Valid MergeRequest req) {
        return toView(party.merge(req.quellId(), req.zielId()));
    }

    // ───────────────────────── Organisation & Mitgliedschaft ─────────────────────────

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/organisationen")
    @Transactional
    public Response createOrganisation(@Valid OrganisationDto dto) {
        // Admin-Anlage = vertrauenswürdig → direkt AKTIV (matchSchluessel wird gesetzt).
        Organisation o = party.legeOrganisationAn(dto.name(), dto.strasse(), dto.plz(), dto.ort(),
                dto.land(), dto.ustId(), Organisation.Status.AKTIV);
        return Response.created(URI.create("/party/organisationen/" + o.id))
                .entity(o).build();
    }

    /**
     * <b>Öffentlicher</b> Self-Service-Lead (unauthentifiziert, §9.1): ein Ausbildungsbetrieb meldet sich
     * mit Firmendaten + Ansprechpartner an. Bot-/Spam-Schutz = Honeypot ({@code website}) + Rate-Limit
     * je Client-IP. Es entsteht eine <b>provisorische</b> Organisation ({@code ANGEFRAGT}) + provisorischer
     * Ansprechpartner; <i>kein Login</i> — der wird erst nach der HITL-/KI-Dublettenprüfung provisioniert.
     */
    @POST
    @Path("/anfragen/ausbildungsbetrieb")
    @Transactional
    public Response anfrageAusbildungsbetrieb(@Valid AusbildungsbetriebAnfrage req,
            @Context HttpServerRequest http) {
        if (req.website() != null && !req.website().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build(); // Honeypot ausgelöst (Bot)
        }
        if (!rateLimiter.erlaube("anfrage:" + clientIp(http), 5, 60_000)) {
            return Response.status(429).build();
        }
        PartyHoheitService.AnfrageErgebnis erg = party.anfrageAusbildungsbetrieb(
                req.name(), req.strasse(), req.plz(), req.ort(), req.land(), req.ustId(),
                req.ansprechpartnerEmail(), req.ansprechpartnerName());
        AnfrageView view = new AnfrageView(erg.organisation().id, erg.organisation().status.name(),
                toView(erg.ansprechpartner()));
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    @GET
    @Path("/organisationen/{id}")
    @Transactional
    public Response getOrganisation(@PathParam("id") Long id) {
        Organisation o = Organisation.findById(id);
        return o == null ? notFound() : Response.ok(toOrgView(o)).build();
    }

    /** Organisations-Dubletten-Kandidaten (gleicher Schlüssel) zur HITL-Auflösung. */
    @GET
    @Path("/organisationen/{id}/kandidaten")
    @Transactional
    public List<OrganisationView> organisationKandidaten(@PathParam("id") Long id) {
        return party.organisationKandidaten(id).stream().map(PartyResource::toOrgView).toList();
    }

    /** Führt eine Firmen-Dublette ({@code quellId}) in die Ziel-Organisation zusammen (HITL-Entscheidung). */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/organisationen/merge")
    @Transactional
    public OrganisationView mergeOrganisation(@Valid OrgMergeRequest req) {
        return toOrgView(party.mergeOrganisation(req.quellId(), req.zielId()));
    }

    /** Firmenseitige Vor-Anlage eines Teilnehmers/Ansprechpartners per E-Mail (provisorisch + Mitgliedschaft). */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/organisationen/{id}/teilnehmer")
    @Transactional
    public Response teilnehmerAnlegen(@PathParam("id") Long organisationId, @Valid TeilnehmerAnlage req) {
        Person p = party.registriereTeilnehmer(organisationId, req.email(), req.anzeigeName(),
                req.rolle(), req.buchungsberechtigt());
        return Response.status(Response.Status.CREATED).entity(toView(p)).build();
    }

    // ───────────────────────── Bestellkontexte & Abrechnungs-Projektion ─────────────────────────

    /** Wählbare Bestellkontexte der Identität: PRIVAT + jede buchungsberechtigte Organisation. */
    @GET
    @Path("/personen/{id}/kontexte")
    @Transactional
    public List<KontextView> kontexte(@PathParam("id") Long id) {
        return party.kontexte(id).stream()
                .map(k -> new KontextView(k.art().name(), k.organisationId(), k.bezeichnung(), k.rollen()))
                .toList();
    }

    /**
     * Projiziert den Abrechnungs-Debitor für einen gewählten Kontext und Bereich: ohne
     * {@code organisationId} privat (eigener Debitor), sonst im Auftrag der Organisation (Org-Debitor).
     */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/personen/{id}/debitor")
    @Transactional
    public DebitorView debitor(@PathParam("id") Long id, @QueryParam("organisationId") Long organisationId,
            @QueryParam("bereich") Bereich bereich) {
        Debitor d = party.ermittleDebitor(id, organisationId, bereich);
        return new DebitorView(d.id, d.debitorNr, d.bereich.name(), d.rolle.name(), d.name);
    }

    // ───────────────────────── Buchung im Kontext (Naht zur Abrechnung) ─────────────────────────

    /**
     * Bucht eine Berufsschul-Anmeldung <i>im gewählten Kontext</i>: der zahlungspflichtige Debitor wird
     * aus Identität + Kontext projiziert (privat vs. im Auftrag der Organisation), nicht übergeben. Die
     * entstehende Anmeldung verarbeitet der bestehende Rechnungslauf (R1) unverändert weiter.
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/buchungen/berufsschule")
    @Transactional
    public Response bucheBerufsschule(@Valid Berufsschulbuchung req) {
        Anmeldung a = buchung.bucheBerufsschule(new BuchungService.Berufsschulbuchung(
                req.teilnehmerPersonId(), req.bestellerPersonId(), req.kontextOrganisationId(),
                req.schuljahr(), req.halbjahr(), req.zimmerart(),
                req.unterrichtBetragCent(), req.uebernachtungBetragCent()));
        BuchungView view = new BuchungView(a.id, a.teilnehmerPersonId, a.bestellerPersonId,
                a.kontextOrganisationId, a.zahlungspflichtigerDebitorId, a.teilnehmerName);
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    /**
     * R7 über den Party-Kern: eine externe Bestellung (z. B. bezahlte Vendure-Order) wird identitäts-/
     * kontextgeführt abgerechnet — Käufer per E-Mail aufgelöst, zahlungspflichtiger Debitor aus dem
     * Kontext projiziert (privat vs. im Auftrag der Organisation). Idempotent über {@code quelle|externeId}.
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/quellen/shop-bestellung")
    @Transactional
    public Response shopBestellung(@Valid ShopBestellung req) {
        long vorher = Rechnung.count();
        Rechnung r = buchung.ausShopBestellung(new BuchungService.Shopbestellung(
                req.quelle(), req.externeId(), req.zahlungsart(), req.bereich(),
                req.kaeuferEmail(), req.kaeuferName(), req.kontextOrganisationId(), req.positionen()));
        boolean neu = Rechnung.count() > vorher;
        ShopBelegView view = new ShopBelegView(r.id, r.debitorId, r.bereich.name(), req.quelle(), req.externeId());
        return Response.status(neu ? Response.Status.CREATED : Response.Status.OK).entity(view).build();
    }

    /**
     * Bucht eine Hochschul-Einschreibung im Kontext (R6): Eigenanteil → privater Debitor der/des
     * Studierenden; bei dualem Studium ({@code kontextOrganisationId} + {@code firmaAnteilCent}) trägt
     * die Organisation ihren Anteil. Der bestehende Hochschul-Rechnungslauf erzeugt die Forderung(en).
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/buchungen/hochschule")
    @Transactional
    public Response bucheHochschule(@Valid Hochschulbuchung req) {
        Anmeldung a = buchung.bucheHochschule(new BuchungService.Hochschulbuchung(
                req.teilnehmerPersonId(), req.bestellerPersonId(), req.kontextOrganisationId(),
                req.semester(), req.semesterbetragCent(), req.firmaAnteilCent(), req.ratenAnzahl()));
        HochschulView view = new HochschulView(a.id, a.teilnehmerPersonId, a.kontextOrganisationId,
                a.zahlungspflichtigerDebitorId, a.firmaDebitorId, a.firmaAnteilCent, a.teilnehmerName);
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    // ───────────────────────── DSGVO: kontext-skopierte Sichten ─────────────────────────

    /**
     * Firmenportal-Sicht: liefert <b>nur</b> die Buchungen im Kontext dieser Organisation. Der Aufrufer
     * wird über den Token-{@code sub} aufgelöst und muss Mitglied der Organisation sein (sonst 403);
     * Privatbuchungen der Personen bleiben strukturell unsichtbar (DSGVO-Trennung).
     */
    @Authenticated
    @GET
    @Path("/firmensicht/{organisationId}")
    @Transactional
    public Response firmensicht(@PathParam("organisationId") Long organisationId, @Context SecurityContext ctx) {
        Person aufrufer = party.findeNachSub(ctx.getUserPrincipal().getName());
        if (aufrufer == null || !party.istMitglied(aufrufer.id, organisationId)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<BuchungZeile> zeilen = buchung.firmensicht(organisationId).stream()
                .map(PartyResource::toZeile).toList();
        return Response.ok(zeilen).build();
    }

    /** 360°-Sicht (intern/Selbst): alle Buchungen, in denen die Person Teilnehmer ist — privat wie über Firmen. */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/personen/{id}/buchungen")
    @Transactional
    public List<BuchungZeile> personenBuchungen(@PathParam("id") Long id) {
        return buchung.personensicht(id).stream().map(PartyResource::toZeile).toList();
    }

    private static BuchungZeile toZeile(Anmeldung a) {
        return new BuchungZeile(a.id, a.teilnehmerName, a.teilnehmerPersonId, a.kontextOrganisationId,
                a.zahlungspflichtigerDebitorId, a.schuljahr, a.halbjahr);
    }

    // ───────────────────────── Helfer ─────────────────────────

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /** Client-IP für das Rate-Limit: bevorzugt {@code X-Forwarded-For} (Reverse-Proxy), sonst Remote-Adresse. */
    private static String clientIp(HttpServerRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.remoteAddress() == null ? "unbekannt" : http.remoteAddress().host();
    }

    private static OrganisationView toOrgView(Organisation o) {
        return new OrganisationView(o.id, o.name, o.plz, o.ort, o.ustId, o.status.name(), o.goldenOrganisationId);
    }

    private static PersonView toView(Person p) {
        List<String> emails = PersonEmail.<PersonEmail>list("personId", p.id).stream()
                .map(e -> e.email).toList();
        List<MitgliedschaftView> ms = Mitgliedschaft.<Mitgliedschaft>list("personId", p.id).stream()
                .map(m -> {
                    Organisation o = Organisation.findById(m.organisationId);
                    return new MitgliedschaftView(m.organisationId, o == null ? null : o.name,
                            m.rolle, m.buchungsberechtigt);
                }).toList();
        return new PersonView(p.id, p.keycloakSub, p.anzeigeName, p.plz, p.ort, p.status.name(),
                p.goldenPersonId, emails, ms);
    }
}
