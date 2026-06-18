package de.netzfactor.ebz.controlling.integration.party.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import de.netzfactor.ebz.controlling.integration.party.model.Aktivitaet;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.CrmService;
import de.netzfactor.ebz.controlling.integration.party.service.CtiService;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * CRM-Kernmaske-API (Schema {@code mdm}): flache Pflege von Person/Organisation, der N:M-Mitgliedschaften
 * (der Kern), Kontaktpunkte, generischer Lookup-Reads und der globalen Sofortsuche. Schreib-Ops verlangen
 * {@code crm-pflege}; Datenschutz-Ops {@code crm-datenschutz}; Lesen ist offen (RBAC-Feinschliff
 * {@code crm-lesen} folgt mit der echten Keycloak-Realm-Konfiguration). DTOs als schlanke nested Records
 * (Mass-Assignment-Schutz: nie id/version/status aus dem Body).
 */
@Path("/crm")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CrmResource {

    @Inject
    CrmService crm;

    @Inject
    de.netzfactor.ebz.controlling.integration.party.service.CtiService cti;

    // ───────────────────────── View-DTOs (gebündelt) ─────────────────────────

    public record LookupView(String code, String bezeichnung) {
    }

    public record PageView<T>(List<T> items, long total, int page, int size) {
    }

    public record PersonListItem(Long id, String anzeigeName, String ort, String hauptFirma, String status) {
    }

    public record KontaktpunktView(Long id, String typ, String label, boolean primaer, String status,
            String email, String nummerAnzeige, String telefonart,
            String strasse, String hausnummer, String plz, String ort, String region, String landCode) {
    }

    public record MitgliedschaftView(Long id, Long organisationId, String organisation, String rolleCode,
            String rolle, String position, String abteilung, boolean hauptzugehoerigkeit,
            boolean hauptansprechpartner, boolean buchungsberechtigt, boolean rechnungsempfaenger,
            LocalDate gueltigVon, LocalDate gueltigBis) {
    }

    public record PersonDetail(Long id, String vorname, String nachname, String anzeigeName, String geschlecht,
            String titel, String briefanrede, LocalDate geburtsdatum, String geburtsort, String geburtslandCode,
            String korrespondenzspracheCode, boolean werbesperre, boolean auskunftssperre, String status,
            String loeschStatus, LocalDate anonymisierenAb, List<String> emails, List<KontaktpunktView> kontaktpunkte,
            List<MitgliedschaftView> mitgliedschaften) {
    }

    public record OrgListItem(Long id, String name, String ort, String ustId, String status) {
    }

    public record OrgMitgliedView(Long mitgliedschaftId, Long personId, String person, String rolle,
            boolean hauptansprechpartner, boolean buchungsberechtigt, LocalDate gueltigBis) {
    }

    public record OrgDetail(Long id, String name, String rechtsform, String handelsregisternummer,
            String registergericht, String brancheCode, String website, String ustId, Long uebergeordneteId,
            Integer bestandsgroesse, String gewerbeerlaubnis, boolean ausbildungsbetrieb, String ihkKammerCode,
            List<String> unternehmenstypen, List<String> taetigkeitsschwerpunkte, List<String> verbaende,
            String strasse, String plz, String ort, String status, List<OrgMitgliedView> mitglieder) {
    }

    public record AktivitaetView(Long id, String typCode, String typ, String richtung, String betreff,
            String inhaltHtml, Long personId, String person, Long organisationId, String organisation,
            java.time.LocalDateTime zeitpunkt, Integer dauerMinuten) {
    }

    public record EinwilligungView(Long id, String kanal, String zweck, String status, String rechtsgrundlage,
            String quelleCode, Long personId, String personName, Long organisationId, String organisation,
            java.time.LocalDateTime ausstehendSeit, java.time.LocalDateTime erteiltAm,
            java.time.LocalDateTime widerrufenAm) {
    }

    public record WeiterbildungOrgZeileView(Long personId, String personName, java.math.BigDecimal summe,
            java.math.BigDecimal soll, boolean erfuellt, String ampel) {
    }

    public record WeiterbildungView(Long id, String titel, String anbieter, java.math.BigDecimal stunden,
            LocalDate datum, boolean extern) {
    }

    public record WeiterbildungKontoView(LocalDate zeitraumVon, LocalDate zeitraumBis, java.math.BigDecimal soll,
            java.math.BigDecimal summe, java.math.BigDecimal rest, boolean erfuellt, String ampel,
            List<WeiterbildungView> nachweise) {
    }

    public record AnmeldungView(Long id, String typ, String teilnehmerName, String status, String zeitraum,
            long betragCent, Long kontextOrganisationId, String kontextOrganisation) {
    }

    public record RechnungView(Long id, String nummer, String bereich, LocalDate ausstellungsdatum,
            String status, long summeCent, String versandStatus) {
    }

    public record Uebersicht360View(List<AnmeldungView> anmeldungen, List<RechnungView> rechnungen) {
    }

    // ───────────────────────── Lookups (generisch) ─────────────────────────

    /** Generischer Lookup-Read: {@code kategorie} = rolle|verband|unternehmenstyp|schwerpunkt|beziehungstyp|
     *  branche|land|sprache|leadquelle|aktivitaetstyp|ihk. Nur aktive Werte, nach Sortierung. */
    @GET
    @Path("/lookups/{kategorie}")
    public List<LookupView> lookups(@PathParam("kategorie") String kategorie) {
        String entity = switch (kategorie.toLowerCase()) {
            case "rolle" -> "LookupRolle";
            case "verband" -> "LookupVerband";
            case "unternehmenstyp" -> "LookupUnternehmenstyp";
            case "schwerpunkt" -> "LookupTaetigkeitsschwerpunkt";
            case "beziehungstyp" -> "LookupBeziehungstyp";
            case "branche" -> "LookupBranche";
            case "land" -> "LookupLand";
            case "sprache" -> "LookupSprache";
            case "leadquelle" -> "LookupLeadQuelle";
            case "aktivitaetstyp" -> "LookupAktivitaetstyp";
            case "ihk" -> "LookupIhkKammer";
            default -> throw RegelVerletzung.nichtGefunden("Unbekannte Lookup-Kategorie: " + kategorie);
        };
        return io.quarkus.hibernate.orm.panache.Panache.getEntityManager()
                .createQuery("select e from " + entity + " e where e.aktiv = true order by e.sortierung, e.bezeichnung",
                        Lookups.LookupBase.class)
                .getResultStream().map(l -> new LookupView(l.code, l.bezeichnung)).toList();
    }

    // ───────────────────────── Suche ─────────────────────────

    @GET
    @Path("/suche")
    public List<CrmService.Treffer> suche(@QueryParam("q") String q) {
        return crm.suche(q);
    }

    // ───────────────────────── Live-Dublettenprüfung beim Anlegen (A16) ─────────────────────────

    /** Vorab-Dublettencheck beim Anlegen: liefert Bestandstreffer + KI-/Regel-Bewertung zum Verknüpfen.
     *  Reiner Lese-Vorgang (kein Schreiben) → offen wie die übrigen CRM-Reads; RBAC-Feinschliff folgt. */
    @POST
    @Path("/dubletten-pruefung")
    @Transactional
    public List<CrmService.DublettenKandidat> dublettenPruefung(@Valid CrmService.DublettenPruefInput in) {
        return crm.pruefeDubletten(in);
    }

    // ───────────────────────── Person ─────────────────────────

    @GET
    @Path("/personen")
    @Transactional
    public PageView<PersonListItem> personen(@QueryParam("q") String q,
            @QueryParam("page") @jakarta.ws.rs.DefaultValue("0") int page,
            @QueryParam("size") @jakarta.ws.rs.DefaultValue("20") int size) {
        var query = (q == null || q.isBlank())
                ? Person.<Person>find("status <> ?1", Sort.by("nachname").and("vorname"), Person.Status.ZUSAMMENGEFUEHRT)
                : Person.<Person>find("status <> ?1 and lower(vorname || ' ' || nachname) like ?2",
                        Sort.by("nachname").and("vorname"),
                        Person.Status.ZUSAMMENGEFUEHRT, "%" + q.trim().toLowerCase() + "%");
        long total = query.count();
        List<PersonListItem> items = query.page(Page.of(page, size)).list().stream()
                .map(p -> new PersonListItem(p.id, p.anzeigeName(),
                        PartyHoheitService.personAdresse(p.id).ort(), hauptFirma(p.id), p.status.name()))
                .toList();
        return new PageView<>(items, total, page, size);
    }

    @GET
    @Path("/personen/{id}")
    @Transactional
    public PersonDetail person(@PathParam("id") Long id) {
        Person p = Person.findById(id);
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + id);
        }
        return personDetail(p);
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/personen")
    public Response personAnlegen(@Valid CrmService.PersonInput in) {
        Person p = crm.createPerson(in);
        return Response.status(Response.Status.CREATED).entity(person(p.id)).build();
    }

    @RolesAllowed("crm-pflege")
    @PUT
    @Path("/personen/{id}")
    public PersonDetail personAendern(@PathParam("id") Long id, @Valid CrmService.PersonInput in) {
        crm.updatePerson(id, in);
        return person(id);
    }

    // ───────────────────────── Organisation ─────────────────────────

    @GET
    @Path("/organisationen")
    @Transactional
    public PageView<OrgListItem> organisationen(@QueryParam("q") String q,
            @QueryParam("page") @jakarta.ws.rs.DefaultValue("0") int page,
            @QueryParam("size") @jakarta.ws.rs.DefaultValue("20") int size) {
        var query = (q == null || q.isBlank())
                ? Organisation.<Organisation>find("status <> ?1", Sort.by("name"), Organisation.Status.ZUSAMMENGEFUEHRT)
                : Organisation.<Organisation>find("status <> ?1 and lower(name) like ?2", Sort.by("name"),
                        Organisation.Status.ZUSAMMENGEFUEHRT, "%" + q.trim().toLowerCase() + "%");
        long total = query.count();
        List<OrgListItem> items = query.page(Page.of(page, size)).list().stream()
                .map(o -> new OrgListItem(o.id, o.name, PartyHoheitService.orgAdresse(o.id).ort(), o.ustId,
                        o.status.name()))
                .toList();
        return new PageView<>(items, total, page, size);
    }

    @GET
    @Path("/organisationen/{id}")
    @Transactional
    public OrgDetail organisation(@PathParam("id") Long id) {
        Organisation o = Organisation.findById(id);
        if (o == null) {
            throw RegelVerletzung.nichtGefunden("Organisation nicht gefunden: " + id);
        }
        return orgDetail(o);
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/organisationen")
    public Response organisationAnlegen(@Valid CrmService.OrganisationInput in) {
        Organisation o = crm.createOrganisation(in);
        return Response.status(Response.Status.CREATED).entity(organisation(o.id)).build();
    }

    @RolesAllowed("crm-pflege")
    @PUT
    @Path("/organisationen/{id}")
    public OrgDetail organisationAendern(@PathParam("id") Long id, @Valid CrmService.OrganisationInput in) {
        crm.updateOrganisation(id, in);
        return organisation(id);
    }

    // ───────────────────────── Mitgliedschaft (N:M-Kern) ─────────────────────────

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/personen/{personId}/organisationen/{orgId}/mitgliedschaften")
    public Response mitgliedschaftAnlegen(@PathParam("personId") Long personId,
            @PathParam("orgId") Long orgId, @Valid CrmService.MitgliedschaftInput in) {
        crm.createMitgliedschaft(personId, orgId, in);
        return Response.status(Response.Status.CREATED).entity(person(personId)).build();
    }

    @RolesAllowed("crm-pflege")
    @PUT
    @Path("/mitgliedschaften/{id}")
    @Transactional
    public MitgliedschaftView mitgliedschaftAendern(@PathParam("id") Long id,
            @Valid CrmService.MitgliedschaftInput in) {
        return mitgliedschaftView(crm.updateMitgliedschaft(id, in));
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/mitgliedschaften/{id}/ausscheiden")
    @Transactional
    public MitgliedschaftView ausscheiden(@PathParam("id") Long id) {
        return mitgliedschaftView(crm.ausscheiden(id));
    }

    // ───────────────────────── Kontaktpunkt ─────────────────────────

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/kontaktpunkte")
    @Transactional
    public Response kontaktpunktAnlegen(@Valid CrmService.KontaktpunktInput in) {
        Kontaktpunkt k = crm.saveKontaktpunkt(in);
        return Response.status(Response.Status.CREATED).entity(kontaktpunktView(k)).build();
    }

    @RolesAllowed("crm-pflege")
    @PUT
    @Path("/kontaktpunkte/{id}")
    @Transactional
    public KontaktpunktView kontaktpunktAendern(@PathParam("id") Long id, @Valid CrmService.KontaktpunktInput in) {
        return kontaktpunktView(crm.updateKontaktpunkt(id, in));
    }

    @RolesAllowed("crm-pflege")
    @DELETE
    @Path("/kontaktpunkte/{id}")
    public Response kontaktpunktLoeschen(@PathParam("id") Long id) {
        crm.loescheKontaktpunkt(id);
        return Response.noContent().build();
    }

    // ───────────────────────── Aktivität / Kontakthistorie (A9) ─────────────────────────

    @GET
    @Path("/personen/{id}/aktivitaeten")
    @Transactional
    public List<AktivitaetView> personAktivitaeten(@PathParam("id") Long id) {
        return crm.aktivitaetenPerson(id).stream().map(CrmResource::aktivitaetView).toList();
    }

    @GET
    @Path("/organisationen/{id}/aktivitaeten")
    @Transactional
    public List<AktivitaetView> organisationAktivitaeten(@PathParam("id") Long id) {
        return crm.aktivitaetenOrganisation(id).stream().map(CrmResource::aktivitaetView).toList();
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/aktivitaeten")
    @Transactional
    public Response aktivitaetAnlegen(@Valid CrmService.AktivitaetInput in) {
        return Response.status(Response.Status.CREATED).entity(aktivitaetView(crm.createAktivitaet(in))).build();
    }

    @RolesAllowed("crm-pflege")
    @PUT
    @Path("/aktivitaeten/{id}")
    @Transactional
    public AktivitaetView aktivitaetAendern(@PathParam("id") Long id, @Valid CrmService.AktivitaetInput in) {
        return aktivitaetView(crm.updateAktivitaet(id, in));
    }

    @RolesAllowed("crm-pflege")
    @DELETE
    @Path("/aktivitaeten/{id}")
    public Response aktivitaetLoeschen(@PathParam("id") Long id) {
        crm.loescheAktivitaet(id);
        return Response.noContent().build();
    }

    // ───────────────────────── Einwilligung / Opt-In (A6) ─────────────────────────

    @GET
    @Path("/personen/{id}/einwilligungen")
    @Transactional
    public List<EinwilligungView> personEinwilligungen(@PathParam("id") Long id) {
        return crm.einwilligungenPerson(id).stream().map(CrmResource::einwilligungView).toList();
    }

    @GET
    @Path("/organisationen/{id}/einwilligungen")
    @Transactional
    public List<EinwilligungView> organisationEinwilligungen(@PathParam("id") Long id) {
        return crm.einwilligungenOrganisation(id).stream().map(CrmResource::einwilligungView).toList();
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/einwilligungen")
    @Transactional
    public Response einwilligungAnlegen(@Valid CrmService.EinwilligungInput in) {
        return Response.status(Response.Status.CREATED).entity(einwilligungView(crm.createEinwilligung(in))).build();
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/einwilligungen/{id}/erteilen")
    @Transactional
    public EinwilligungView einwilligungErteilen(@PathParam("id") Long id) {
        return einwilligungView(crm.einwilligungErteilen(id));
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/einwilligungen/{id}/widerrufen")
    @Transactional
    public EinwilligungView einwilligungWiderrufen(@PathParam("id") Long id) {
        return einwilligungView(crm.einwilligungWiderrufen(id));
    }

    // ───────────────────────── Weiterbildung §34c (A19) ─────────────────────────

    @GET
    @Path("/personen/{id}/weiterbildung")
    @Transactional
    public WeiterbildungKontoView personWeiterbildung(@PathParam("id") Long id) {
        CrmService.WeiterbildungKonto k = crm.weiterbildungskonto(id);
        return new WeiterbildungKontoView(k.zeitraumVon(), k.zeitraumBis(), k.soll(), k.summe(), k.rest(),
                k.erfuellt(), k.ampel(), k.nachweise().stream().map(CrmResource::weiterbildungView).toList());
    }

    @GET
    @Path("/organisationen/{id}/weiterbildung")
    @Transactional
    public List<WeiterbildungOrgZeileView> organisationWeiterbildung(@PathParam("id") Long id) {
        return crm.weiterbildungOrganisation(id).stream()
                .map(z -> new WeiterbildungOrgZeileView(z.personId(), z.personName(), z.summe(), z.soll(),
                        z.erfuellt(), z.ampel()))
                .toList();
    }

    @RolesAllowed("crm-pflege")
    @POST
    @Path("/weiterbildung")
    @Transactional
    public Response weiterbildungAnlegen(@Valid CrmService.WeiterbildungInput in) {
        return Response.status(Response.Status.CREATED)
                .entity(weiterbildungView(crm.createWeiterbildung(in))).build();
    }

    @RolesAllowed("crm-pflege")
    @DELETE
    @Path("/weiterbildung/{id}")
    public Response weiterbildungLoeschen(@PathParam("id") Long id) {
        crm.loescheWeiterbildung(id);
        return Response.noContent().build();
    }

    // ───────────────────────── 360°-Sicht (A18) ─────────────────────────

    @GET
    @Path("/personen/{id}/uebersicht")
    @Transactional
    public Uebersicht360View personUebersicht(@PathParam("id") Long id) {
        return uebersicht360(crm.uebersichtPerson(id));
    }

    @GET
    @Path("/organisationen/{id}/uebersicht")
    @Transactional
    public Uebersicht360View organisationUebersicht(@PathParam("id") Long id) {
        return uebersicht360(crm.uebersichtOrganisation(id));
    }

    // ───────────────────────── Recht auf Vergessen (A7) — nur crm-datenschutz ─────────────────────────

    /** Optionaler Body der Sperre: Aufbewahrungsfrist bis zur planmäßigen Anonymisierung. */
    public record SperrenInput(Integer aufbewahrungJahre) {
    }

    @RolesAllowed("crm-datenschutz")
    @POST
    @Path("/personen/{id}/sperren")
    public PersonDetail personSperren(@PathParam("id") Long id, SperrenInput in) {
        crm.personSperren(id, in == null ? null : in.aufbewahrungJahre());
        return person(id);
    }

    @RolesAllowed("crm-datenschutz")
    @POST
    @Path("/personen/{id}/anonymisieren")
    public PersonDetail personAnonymisieren(@PathParam("id") Long id) {
        crm.personAnonymisieren(id);
        return person(id);
    }

    // ───────────────────────── CTI / Anruf (A13) ─────────────────────────

    /** Simuliert einen eingehenden Anruf (TK-Gateway-Webhook): matcht den Kontakt, protokolliert und
     *  broadcastet das Event an alle offenen CTI-Cockpit-WebSockets. Showcase-Auslöser für die Live-Demo. */
    @POST
    @Path("/cti/simuliere-anruf")
    public CtiService.AnrufEvent ctiSimuliereAnruf(CtiService.AnrufAnfrage in) {
        return cti.simuliereAnruf(in);
    }

    private static Uebersicht360View uebersicht360(CrmService.Uebersicht u) {
        return new Uebersicht360View(
                u.anmeldungen().stream().map(CrmResource::anmeldungView).toList(),
                u.rechnungen().stream().map(CrmResource::rechnungView).toList());
    }

    private static AnmeldungView anmeldungView(de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung a) {
        String zeitraum = a.schuljahr != null
                ? a.schuljahr + (a.halbjahr != null ? " · HJ " + a.halbjahr : "")
                : a.semester;
        long betrag = (a.unterrichtBetragCent == null ? 0 : a.unterrichtBetragCent)
                + (a.uebernachtungBetragCent == null ? 0 : a.uebernachtungBetragCent)
                + (a.semesterbetragCent == null ? 0 : a.semesterbetragCent);
        return new AnmeldungView(a.id, a.typ == null ? null : a.typ.name(), a.teilnehmerName,
                a.status == null ? null : a.status.name(), zeitraum, betrag, a.kontextOrganisationId(),
                a.kontextOrganisation == null ? null : a.kontextOrganisation.name);
    }

    private static RechnungView rechnungView(de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung r) {
        return new RechnungView(r.id, r.nummer, r.bereich == null ? null : r.bereich.name(),
                r.ausstellungsdatum, r.status == null ? null : r.status.name(), r.summeCent(),
                r.versandStatus == null ? null : r.versandStatus.name());
    }

    // ───────────────────────── Mapping ─────────────────────────

    private static PersonDetail personDetail(Person p) {
        List<KontaktpunktView> kps = Kontaktpunkt.<Kontaktpunkt>list("person.id", p.id).stream()
                .map(CrmResource::kontaktpunktView).toList();
        List<MitgliedschaftView> ms = Mitgliedschaft.<Mitgliedschaft>list("person.id", p.id).stream()
                .map(CrmResource::mitgliedschaftView).toList();
        return new PersonDetail(p.id, p.vorname, p.nachname, p.anzeigeName(), p.geschlecht.name(), p.titel,
                p.briefanrede(), p.geburtsdatum, p.geburtsort, code(p.geburtsland),
                code(p.korrespondenzsprache), p.werbesperre, p.auskunftssperre, p.status.name(),
                p.loeschStatus.name(), p.anonymisierenAb, PartyHoheitService.alleEmails(p.id), kps, ms);
    }

    private static OrgDetail orgDetail(Organisation o) {
        PartyHoheitService.Adresse a = PartyHoheitService.orgAdresse(o.id);
        List<OrgMitgliedView> mitglieder = Mitgliedschaft.<Mitgliedschaft>list("organisation.id", o.id).stream()
                .map(m -> new OrgMitgliedView(m.id, m.personId(), m.person == null ? null : m.person.anzeigeName(),
                        m.rolle == null ? null : m.rolle.bezeichnung, m.hauptansprechpartner,
                        m.buchungsberechtigt, m.gueltigBis))
                .toList();
        return new OrgDetail(o.id, o.name, o.rechtsform, o.handelsregisternummer, o.registergericht,
                code(o.branche), o.website, o.ustId, o.uebergeordnete == null ? null : o.uebergeordnete.id,
                o.bestandsgroesse, o.gewerbeerlaubnis.name(), o.ausbildungsbetrieb, code(o.ihkKammer),
                o.unternehmenstypen.stream().map(l -> l.code).toList(),
                o.taetigkeitsschwerpunkte.stream().map(l -> l.code).toList(),
                o.verbandszugehoerigkeiten.stream().map(l -> l.code).toList(),
                a.strasse(), a.plz(), a.ort(), o.status.name(), mitglieder);
    }

    private static KontaktpunktView kontaktpunktView(Kontaktpunkt k) {
        return new KontaktpunktView(k.id, k.typ.name(), k.label, k.primaer, k.status.name(), k.email,
                k.nummerAnzeige, k.telefonart == null ? null : k.telefonart.name(), k.strasse, k.hausnummer,
                k.plz, k.ort, k.region, code(k.land));
    }

    private static MitgliedschaftView mitgliedschaftView(Mitgliedschaft m) {
        Organisation o = m.organisation;
        return new MitgliedschaftView(m.id, m.organisationId(), o == null ? null : o.name,
                m.rolle == null ? null : m.rolle.code, m.rolle == null ? null : m.rolle.bezeichnung,
                m.position, m.abteilung, m.hauptzugehoerigkeit, m.hauptansprechpartner, m.buchungsberechtigt,
                m.rechnungsempfaenger, m.gueltigVon, m.gueltigBis);
    }

    private static EinwilligungView einwilligungView(de.netzfactor.ebz.controlling.integration.party.model.Einwilligung e) {
        return new EinwilligungView(e.id, e.kanal == null ? null : e.kanal.name(),
                e.zweck == null ? null : e.zweck.name(), e.status == null ? null : e.status.name(),
                e.rechtsgrundlage == null ? null : e.rechtsgrundlage.name(), code(e.quelle),
                e.person == null ? null : e.person.id, e.person == null ? null : e.person.anzeigeName(),
                e.organisation == null ? null : e.organisation.id,
                e.organisation == null ? null : e.organisation.name,
                e.ausstehendSeit, e.erteiltAm, e.widerrufenAm);
    }

    private static WeiterbildungView weiterbildungView(
            de.netzfactor.ebz.controlling.integration.party.model.Weiterbildungsnachweis w) {
        return new WeiterbildungView(w.id, w.titel, w.anbieter, w.stunden, w.datum, w.extern);
    }

    private static AktivitaetView aktivitaetView(Aktivitaet a) {
        return new AktivitaetView(a.id, a.typ == null ? null : a.typ.code, a.typ == null ? null : a.typ.bezeichnung,
                a.richtung == null ? null : a.richtung.name(), a.betreff, a.inhaltHtml,
                a.person == null ? null : a.person.id, a.person == null ? null : a.person.anzeigeName(),
                a.organisation == null ? null : a.organisation.id, a.organisation == null ? null : a.organisation.name,
                a.zeitpunkt, a.dauerMinuten);
    }

    private static String hauptFirma(Long personId) {
        Mitgliedschaft m = Mitgliedschaft.find("person.id = ?1 and hauptzugehoerigkeit = true", personId)
                .firstResult();
        if (m == null) {
            m = Mitgliedschaft.find("person.id = ?1 and gueltigBis is null", personId).firstResult();
        }
        return m == null || m.organisation == null ? null : m.organisation.name;
    }

    private static String code(Lookups.LookupBase l) {
        return l == null ? null : l.code;
    }
}
