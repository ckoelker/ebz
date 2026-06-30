package de.netzfactor.ebz.controlling.integration.rechnung.web;

import java.net.URI;
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

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.AnmeldungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.DebitorDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.BestandImportDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.DatevProtokollDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.DebitorAliasDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.DebitorAnlageDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ExterneBestellung;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.KorrekturRequest;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ManuellePositionDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.MergeRequest;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.RechnungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.RechnungPositionDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.HochschulLaufRequest;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.RechnungslaufRequest;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.SonderrechnungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ZahlungseingangDto;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorAlias;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungVersandStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.datev.Buchungssatz;
import de.netzfactor.ebz.controlling.integration.rechnung.datev.DatevService;
import de.netzfactor.ebz.controlling.integration.rechnung.datev.DatevUebergabe;
import de.netzfactor.ebz.controlling.integration.rechnung.gobd.GobdArchivService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.BestellungBillingService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.DebitorHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RechnungService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RechnungVersandService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RechnungslaufService;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdDaten;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdMapper;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

/**
 * Billing-API (R1, Schema {@code rechnung}). Stammdaten (Debitor/Anmeldung) als schlanke CRUD über
 * {@code @Valid}-DTOs (Mass-Assignment-Schutz: nie id/version/nummer aus dem Body). Der Beleg-
 * Lebenszyklus (Lauf → Ausstellen/Festschreibung → Storno/Gutschrift/Nachberechnung) delegiert an die
 * Services. Schreib-Ops verlangen die Realm-Rolle {@code rechnung-pflege} (RBAC). Lese-Listen offen
 * (analog {@code bildung}).
 */
@Path("/rechnung")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RechnungResource {

    @Inject
    RechnungslaufService rechnungslauf;

    @Inject
    RechnungService rechnungService;

    @Inject
    RechnungVersandService rechnungVersand;

    @Inject
    ZugferdService zugferd;

    @Inject
    RechnungZugferdMapper zugferdMapper;

    @Inject
    GobdArchivService gobdArchiv;

    @Inject
    DebitorHoheitService debitorHoheit;

    @Inject
    DatevService datev;

    @Inject
    BestellungBillingService bestellungBilling;

    // ───────────────────────── Debitoren ─────────────────────────
    @GET
    @Path("/debitoren")
    @Transactional
    public List<DebitorDto> listDebitoren() {
        return Debitor.<Debitor>listAll().stream().map(RechnungResource::toDebitor).toList();
    }

    @GET
    @Path("/debitoren/{id}")
    @Transactional
    public Response getDebitor(@PathParam("id") Long id) {
        Debitor d = Debitor.findById(id);
        return d == null ? notFound() : Response.ok(toDebitor(d)).build();
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/debitoren")
    @Transactional
    public Response createDebitor(@Valid DebitorDto dto) {
        Debitor d = new Debitor();
        applyDebitor(dto, d);
        d.persist();
        return created("/rechnung/debitoren/" + d.id, toDebitor(d));
    }

    @RolesAllowed("rechnung-pflege")
    @PUT
    @Path("/debitoren/{id}")
    @Transactional
    public Response updateDebitor(@PathParam("id") Long id, @Valid DebitorDto dto) {
        Debitor d = Debitor.findById(id);
        if (d == null) {
            return notFound();
        }
        if (stale(dto.version(), d.version)) {
            return conflict();
        }
        applyDebitor(dto, d);
        return Response.ok(toDebitor(d)).build();
    }

    // ───────────────────────── Debitoren-Hoheit (R3) ─────────────────────────

    /**
     * Gouvernierte Anlage: zentrale Nummernvergabe + idempotenter Dublettenschutz. Existiert bereits
     * ein Golden-Record gleicher Identität im Bereich, wird er wiederverwendet (200) statt einer neuen
     * Dublette (201).
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/debitoren/anlegen")
    @Transactional
    public Response anlegenDebitor(@Valid DebitorAnlageDto dto) {
        long vorher = Debitor.count();
        Debitor d = debitorHoheit.findeOderLege(toStammdaten(dto));
        boolean neu = Debitor.count() > vorher;
        return Response.status(neu ? Response.Status.CREATED : Response.Status.OK)
                .entity(toDebitor(d)).build();
    }

    /** Altbestand übernehmen: dedupliziert auf den Golden-Record und konserviert die Altnummer als Alias. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/debitoren/import")
    @Transactional
    public DebitorDto importDebitor(@Valid BestandImportDto req) {
        return toDebitor(debitorHoheit.importiereBestand(toStammdaten(req.debitor()), req.quelle(), req.externeNr()));
    }

    /** Löst eine externe/alte Debitorennummer auf den aktiven Golden-Record auf. */
    @GET
    @Path("/debitoren/aufloesen")
    @Transactional
    public Response aufloesenDebitor(@QueryParam("quelle") String quelle, @QueryParam("externeNr") String externeNr) {
        Debitor d = debitorHoheit.aufloesen(quelle, externeNr);
        return d == null ? notFound() : Response.ok(toDebitor(d)).build();
    }

    /** Dublettenkandidaten zu einem Debitor (gleicher Dublettenschlüssel im Bereich). */
    @GET
    @Path("/debitoren/{id}/kandidaten")
    @Transactional
    public List<DebitorDto> kandidaten(@PathParam("id") Long id) {
        return debitorHoheit.kandidaten(id).stream().map(RechnungResource::toDebitor).toList();
    }

    /** Aliase (Altnummern) eines Debitors. */
    @GET
    @Path("/debitoren/{id}/aliase")
    @Transactional
    public List<DebitorAliasDto> aliase(@PathParam("id") Long id) {
        return debitorHoheit.aliase(id).stream().map(RechnungResource::toAlias).toList();
    }

    /** Führt eine Dublette ({@code quellId}) in den Golden-Record ({@code zielId}) zusammen. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/debitoren/merge")
    @Transactional
    public DebitorDto mergeDebitoren(@Valid MergeRequest req) {
        return toDebitor(debitorHoheit.merge(req.quellId(), req.zielId()));
    }

    private static DebitorHoheitService.Stammdaten toStammdaten(DebitorAnlageDto dto) {
        return new DebitorHoheitService.Stammdaten(dto.bereich(), dto.rolle(), dto.name(), dto.strasse(),
                dto.plz(), dto.ort(), dto.land(), dto.ustId(), dto.iban(), dto.email());
    }

    // ───────────────────────── DATEV-Übergabe (R4) ─────────────────────────

    /** Buchungssätze (Vorschau) der festgeschriebenen Belege eines Zeitraums. */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/datev/buchungssaetze")
    @Transactional
    public List<Buchungssatz> datevBuchungssaetze(@QueryParam("von") String von,
            @QueryParam("bis") String bis, @QueryParam("bereich") Bereich bereich) {
        LocalDate v = datum(von, LocalDate.of(2000, 1, 1));
        LocalDate b = datum(bis, LocalDate.of(2999, 12, 31));
        return datev.buchungssaetze(datev.belege(v, b, bereich));
    }

    /** DATEV-Buchungsstapel als EXTF-CSV (Import-Brücke) für den Zeitraum. */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/datev/buchungsstapel")
    @Produces("text/csv")
    @Transactional
    public Response datevBuchungsstapel(@QueryParam("von") String von,
            @QueryParam("bis") String bis, @QueryParam("bereich") Bereich bereich) {
        LocalDate v = datum(von, LocalDate.of(2000, 1, 1));
        LocalDate b = datum(bis, LocalDate.of(2999, 12, 31));
        byte[] csv = datev.extfCsv(datev.belege(v, b, bereich), v, b);
        // EXTF ist Windows-1252-kodiert (DATEV-Vorgabe) — Charset passend deklarieren.
        return Response.ok(csv).type("text/csv; charset=windows-1252")
                .header("Content-Disposition", "attachment; filename=\"EXTF_Buchungsstapel_" + v + "_" + b + ".csv\"")
                .build();
    }

    /** Übergabe an den aktiven DATEV-Weg (datev.modus): EXTF-CSV-Brücke bzw. DATEV-Cloud-Mock. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/datev/uebergabe")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public DatevProtokollDto datevUebergeben(@QueryParam("von") String von,
            @QueryParam("bis") String bis, @QueryParam("bereich") Bereich bereich) {
        LocalDate v = datum(von, LocalDate.of(2000, 1, 1));
        LocalDate b = datum(bis, LocalDate.of(2999, 12, 31));
        DatevUebergabe.Protokoll p = datev.uebergeben(datev.belege(v, b, bereich), v, b);
        return new DatevProtokollDto(p.modus(), p.referenz(), p.anzahlBuchungen(),
                p.artefakt() == null ? 0 : p.artefakt().length, p.hinweis());
    }

    private static LocalDate datum(String s, LocalDate fallback) {
        return s == null || s.isBlank() ? fallback : LocalDate.parse(s);
    }

    // ───────────────────────── Anmeldungen ─────────────────────────
    @GET
    @Path("/anmeldungen")
    @Transactional
    public List<AnmeldungDto> listAnmeldungen() {
        return Anmeldung.<Anmeldung>listAll().stream().map(RechnungResource::toAnmeldung).toList();
    }

    @GET
    @Path("/anmeldungen/{id}")
    @Transactional
    public Response getAnmeldung(@PathParam("id") Long id) {
        Anmeldung a = Anmeldung.findById(id);
        return a == null ? notFound() : Response.ok(toAnmeldung(a)).build();
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/anmeldungen")
    @Transactional
    public Response createAnmeldung(@Valid AnmeldungDto dto) {
        Anmeldung a = new Anmeldung();
        applyAnmeldung(dto, a);
        a.persist();
        return created("/rechnung/anmeldungen/" + a.id, toAnmeldung(a));
    }

    @RolesAllowed("rechnung-pflege")
    @PUT
    @Path("/anmeldungen/{id}")
    @Transactional
    public Response updateAnmeldung(@PathParam("id") Long id, @Valid AnmeldungDto dto) {
        Anmeldung a = Anmeldung.findById(id);
        if (a == null) {
            return notFound();
        }
        if (stale(dto.version(), a.version)) {
            return conflict();
        }
        applyAnmeldung(dto, a);
        return Response.ok(toAnmeldung(a)).build();
    }

    @RolesAllowed("rechnung-pflege")
    @DELETE
    @Path("/anmeldungen/{id}")
    @Transactional
    public Response abbrechenAnmeldung(@PathParam("id") Long id) {
        Anmeldung a = Anmeldung.findById(id);
        if (a == null) {
            return notFound();
        }
        a.status = AnmeldungStatus.ABGEBROCHEN; // Soft-Delete: aus dem Lauf, aber Historie bleibt
        return Response.noContent().build();
    }

    // ───────────────────────── Rechnungslauf ─────────────────────────
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/laeufe")
    @Transactional
    @APIResponse(responseCode = "200", description = "Erzeugte/idempotent wiederverwendete Entwürfe",
            content = @Content(schema = @Schema(implementation = RechnungDto.class)))
    public List<RechnungDto> rechnungslauf(@Valid RechnungslaufRequest req) {
        return rechnungslauf.erzeugeBerufsschulEntwuerfe(req.schuljahr(), req.halbjahr())
                .stream().map(RechnungResource::toRechnung).toList();
    }

    /** Hochschul-Rechnungslauf (R6): je Anmeldung Firmen-Split (zwei Rechnungen) und/oder Raten. */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/laeufe/hochschule")
    @Transactional
    @APIResponse(responseCode = "200", description = "Erzeugte/idempotent wiederverwendete Entwürfe",
            content = @Content(schema = @Schema(implementation = RechnungDto.class)))
    public List<RechnungDto> hochschulLauf(@Valid HochschulLaufRequest req) {
        return rechnungslauf.erzeugeHochschulEntwuerfe(req.semester())
                .stream().map(RechnungResource::toRechnung).toList();
    }

    /**
     * Quellen-agnostische Naht (R7): eine externe Bestellung (z. B. bezahlte Vendure-Order) wird neben
     * der Anmeldung zur Abrechnungsbasis — Kunde via Debitoren-Hoheit (R3) aufgelöst, Bestellung
     * idempotent (200 bei erneutem Push) in einen Rechnungs-Entwurf überführt.
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/quellen/bestellung")
    @Transactional
    public Response bestellungUebernehmen(@Valid ExterneBestellung bestellung) {
        long vorher = Rechnung.count();
        Rechnung r = bestellungBilling.ausBestellung(bestellung);
        boolean neu = Rechnung.count() > vorher;
        return Response.status(neu ? Response.Status.CREATED : Response.Status.OK)
                .entity(toRechnung(r)).build();
    }

    // ───────────────────────── Rechnungen lesen ─────────────────────────
    @GET
    @Path("/rechnungen")
    @Transactional
    public List<RechnungDto> listRechnungen(@QueryParam("status") RechnungStatus status,
            @QueryParam("bereich") Bereich bereich) {
        List<Rechnung> alle;
        if (status != null && bereich != null) {
            alle = Rechnung.list("status = ?1 and bereich = ?2", status, bereich);
        } else if (status != null) {
            alle = Rechnung.list("status", status);
        } else if (bereich != null) {
            alle = Rechnung.list("bereich", bereich);
        } else {
            alle = Rechnung.listAll();
        }
        return alle.stream().map(RechnungResource::toRechnung).toList();
    }

    /**
     * Legt eine freie <b>Sonderrechnung</b> an (leerer Entwurf außerhalb der Standard-Läufe); danach über
     * {@code /positionen} bestücken und {@code /ausstellen} festschreiben. {@code bereich}/
     * {@code zahlungszielTage} optional (Default Debitor-Bereich / 14 Tage).
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen")
    @Transactional
    public Response erstelleSonderrechnung(@Valid SonderrechnungDto dto) {
        Rechnung r = rechnungService.erstelleSonderrechnung(dto.debitorId(), dto.bereich(),
                dto.zeitraumBezeichnung(), dto.zahlungszielTage());
        return created("/rechnung/rechnungen/" + r.id, toRechnung(r));
    }

    @GET
    @Path("/rechnungen/{id}")
    @Transactional
    public Response getRechnung(@PathParam("id") Long id) {
        Rechnung r = Rechnung.findById(id);
        return r == null ? notFound() : Response.ok(toRechnung(r)).build();
    }

    /**
     * Liefert den Beleg als ZUGFeRD-E-Rechnung (PDF/A-3 + EN-16931-XML), R2 (§4b). Für jeden
     * ausgestellten (festgeschriebenen) Beleg — Rechnung (Typ 380) wie Korrekturbeleg (Gutschrift/
     * Storno = Typ 381 mit Bezug aufs Original). Der Mustang-Validator ist Pflicht-Tor — schlägt er
     * an, wird nichts ausgeliefert (502 mit Report).
     */
    @RolesAllowed("rechnung-pflege")
    @GET
    @Path("/rechnungen/{id}/zugferd")
    @Produces("application/pdf")
    @Transactional
    public Response zugferd(@PathParam("id") Long id) {
        Rechnung r = Rechnung.findById(id);
        if (r == null) {
            return notFound();
        }
        if (r.status != RechnungStatus.AUSGESTELLT && r.status != RechnungStatus.BEZAHLT
                && r.status != RechnungStatus.STORNIERT) {
            return jsonFehler(Response.Status.CONFLICT, "E-Rechnung nur für festgeschriebene Belege (Status: " + r.status + ").");
        }
        try {
            RechnungZugferdDaten daten = zugferdMapper.baue(r);
            ZugferdService.Ergebnis erg = zugferd.erzeugeUndValidiere(daten);
            if (!erg.valide()) {
                return jsonFehler(Response.Status.BAD_GATEWAY,
                        "ZUGFeRD-Validierung fehlgeschlagen — nicht ausgeliefert. Report: " + erg.report());
            }
            return Response.ok(erg.pdf())
                    .header("Content-Disposition", "attachment; filename=\"beleg-" + r.nummer + ".pdf\"")
                    .build();
        } catch (Exception ex) {
            return jsonFehler(Response.Status.BAD_GATEWAY, "ZUGFeRD-Erzeugung fehlgeschlagen: " + ex.getMessage());
        }
    }

    /**
     * Versendet die E-Rechnung des festgeschriebenen Belegs als ZUGFeRD-PDF-Anhang per E-Mail an den
     * Debitor und vermerkt den Versand ({@code versandStatus}/{@code versendetAm}). Der Mustang-Validator
     * ist Pflicht-Tor; erneuter Aufruf ist erlaubt (Re-Send). Fehler → 404/409 (siehe {@link RechnungVersandService}).
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/versenden")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public RechnungDto versenden(@PathParam("id") Long id) {
        return toRechnung(rechnungVersand.versende(id));
    }

    // ───────────────────────── Lebenszyklus (Service) ─────────────────────────
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/positionen")
    @Transactional
    public RechnungDto addPosition(@PathParam("id") Long id, @Valid ManuellePositionDto dto) {
        return toRechnung(rechnungService.addManuellePosition(id, dto));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/ausstellen")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public RechnungDto ausstellen(@PathParam("id") Long id) {
        return festschreibenUndArchivieren(rechnungService.ausstellen(id));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/storno")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public RechnungDto storno(@PathParam("id") Long id) {
        return festschreibenUndArchivieren(rechnungService.storno(id));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/gutschrift")
    @Transactional
    public RechnungDto gutschrift(@PathParam("id") Long id, @Valid KorrekturRequest req) {
        return festschreibenUndArchivieren(rechnungService.gutschrift(id, req.grund(), req.positionen()));
    }

    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/nachberechnung")
    @Transactional
    public RechnungDto nachberechnung(@PathParam("id") Long id, @Valid KorrekturRequest req) {
        return festschreibenUndArchivieren(rechnungService.nachberechnung(id, req.grund(), req.positionen()));
    }

    /**
     * Verbucht einen manuellen Zahlungseingang ({@code AUSGESTELLT → BEZAHLT}). Body optional
     * (Datum default heute, Betrag default Belegsumme). Nur Forderungs-Belege; Wiederholung/falscher
     * Status → 409 (siehe {@link RechnungService#bezahlen}). Offene Posten/Mahnwesen bleiben bei DATEV.
     */
    @RolesAllowed("rechnung-pflege")
    @POST
    @Path("/rechnungen/{id}/bezahlen")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public RechnungDto bezahlen(@PathParam("id") Long id, ZahlungseingangDto dto) {
        ZahlungseingangDto z = dto == null ? new ZahlungseingangDto(null, null, null) : dto;
        return toRechnung(rechnungService.bezahlen(id, z.bezahltAm(), z.zahlbetragCent(), z.zahlungsReferenz()));
    }

    /**
     * Festschreibung + GoBD-Archivierung in EINER Transaktion: schlägt das Archivieren des validierten
     * ZUGFeRD fehl (Validierung 409 / MinIO 500), rollt die Festschreibung zurück — kein ausgestellter
     * Beleg ohne revisionssicheren E-Rechnungs-Beleg.
     */
    private RechnungDto festschreibenUndArchivieren(Rechnung r) {
        gobdArchiv.archiviereWennAktiv(r);
        return toRechnung(r);
    }

    // ───────────────────────── Helfer ─────────────────────────
    private static boolean stale(Long dtoVersion, long entityVersion) {
        return dtoVersion != null && dtoVersion != entityVersion; // Optimistic Locking
    }

    private static Response created(String location, Object body) {
        return Response.created(URI.create(location)).entity(body).build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private static Response conflict() {
        return Response.status(Response.Status.CONFLICT).build();
    }

    private static Response jsonFehler(Response.Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new RegelVerletzungMapper.Fehler(message)).build();
    }

    private static void applyDebitor(DebitorDto dto, Debitor d) {
        d.debitorNr = dto.debitorNr();
        d.bereich = dto.bereich();
        d.rolle = dto.rolle();
        d.name = dto.name();
        d.strasse = dto.strasse();
        d.plz = dto.plz();
        d.ort = dto.ort();
        d.land = dto.land();
        d.ustId = dto.ustId();
        d.iban = dto.iban();
        d.email = dto.email();
        // R3: auch manuell gepflegte Debitoren bekommen den Dublettenschlüssel, damit Match/Merge greift.
        if (d.status == null) {
            d.status = DebitorStatus.AKTIV;
        }
        d.matchSchluessel = DebitorHoheitService.matchSchluessel(dto.name(), dto.plz(), dto.ustId());
    }

    private static DebitorDto toDebitor(Debitor d) {
        return new DebitorDto(d.id, d.version, d.debitorNr, d.bereich, d.rolle, d.name,
                d.strasse, d.plz, d.ort, d.land, d.ustId, d.iban, d.email,
                d.status == null ? null : d.status.name(), d.goldenDebitorId);
    }

    private static DebitorAliasDto toAlias(DebitorAlias a) {
        return new DebitorAliasDto(a.id, a.debitorId(), a.quelle, a.externeNr);
    }

    private static void applyAnmeldung(AnmeldungDto dto, Anmeldung a) {
        a.typ = dto.typ();
        a.teilnehmerName = dto.teilnehmerName();
        a.teilnehmerEmail = dto.teilnehmerEmail();
        a.bildungsangebot = dto.bildungsangebotId() == null ? null : Bildungsangebot.findById(dto.bildungsangebotId());
        a.zahlungspflichtigerDebitor = dto.zahlungspflichtigerDebitorId() == null
                ? null : Debitor.findById(dto.zahlungspflichtigerDebitorId());
        a.status = dto.status();
        a.schuljahr = dto.schuljahr();
        a.halbjahr = dto.halbjahr();
        a.zimmerart = dto.zimmerart();
        a.unterrichtBetragCent = dto.unterrichtBetragCent();
        a.uebernachtungBetragCent = dto.uebernachtungBetragCent();
        a.semester = dto.semester();
        a.semesterbetragCent = dto.semesterbetragCent();
        a.firmaDebitor = dto.firmaDebitorId() == null ? null : Debitor.findById(dto.firmaDebitorId());
        a.firmaAnteilCent = dto.firmaAnteilCent();
        a.ratenAnzahl = dto.ratenAnzahl();
    }

    private static AnmeldungDto toAnmeldung(Anmeldung a) {
        return new AnmeldungDto(a.id, a.version, a.typ, a.teilnehmerName, a.teilnehmerEmail,
                a.bildungsangebotId(), a.zahlungspflichtigerDebitorId(), a.status,
                a.schuljahr, a.halbjahr, a.zimmerart, a.unterrichtBetragCent, a.uebernachtungBetragCent,
                a.semester, a.semesterbetragCent, a.firmaDebitorId(), a.firmaAnteilCent, a.ratenAnzahl);
    }

    private static RechnungDto toRechnung(Rechnung r) {
        List<RechnungPositionDto> pos = r.positionen.stream().map(RechnungResource::toPosition).toList();
        RechnungVersandStatus versand = r.versandStatus == null
                ? RechnungVersandStatus.NICHT_VERSENDET : r.versandStatus;
        return new RechnungDto(r.id, r.version, r.belegart, r.bereich, r.nummer, r.debitorId(),
                r.zeitraumBezeichnung, r.ausstellungsdatum, r.zahlungszielTage, r.status,
                r.originalRechnungId(), r.summeCent(), versand, r.versendetAm, r.versendetAn,
                r.bezahltAm, r.zahlbetragCent, r.zahlungsReferenz, pos);
    }

    private static RechnungPositionDto toPosition(RechnungPosition p) {
        return new RechnungPositionDto(p.id, p.teilnehmerName, p.beschreibung, p.menge,
                p.einzelbetragCent, p.betragCent(), p.steuerfall, p.steuersatz, p.befreiungsgrund,
                p.leistungsart, p.herkunft);
    }
}
