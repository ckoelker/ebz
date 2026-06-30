package de.netzfactor.ebz.controlling.integration.party.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import de.netzfactor.ebz.controlling.integration.party.model.Aktivitaet;
import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil;
import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Login;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.Weiterbildungsnachweis;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorRolle;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.service.DebitorHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * CRM-Pflege-Hoheit: Schreib-/Lese-Logik der CRM-Kernmaske (Stammdaten Person/Organisation, N:M-
 * Mitgliedschaften, Kontaktpunkte, generische Lookup-Reads, globale Suche). Trennt die fachlichen
 * Invarianten (Plan-Fallstricke) vom HTTP-Layer ({@code CrmResource}):
 * <ul>
 *   <li><b>Höchstens eine aktive Hauptzugehörigkeit</b> je Person und <b>höchstens ein aktiver
 *       Hauptansprechpartner</b> je Organisation (nicht „genau eine") — beim Setzen werden die übrigen
 *       zurückgesetzt.</li>
 *   <li>Klassifikationen kommen als <b>Lookup-Code</b> herein und werden FK-sicher aufgelöst.</li>
 *   <li>Mass-Assignment-Schutz: nie {@code id/version/status/golden*} aus dem Body.</li>
 * </ul>
 * Identitäts-/Merge-/Kontext-Themen bleiben im {@link PartyHoheitService}; dieser Service ergänzt die
 * reine Stammdatenpflege darüber.
 */
@ApplicationScoped
public class CrmService {

    /** Begrenzung der Live-Dublettentreffer (Showcase). */
    private static final int DUBLETTEN_LIMIT = 8;

    @Inject
    DublettenBerater dublettenBerater;

    // ───────────────────────── Eingabe-DTOs (Bean Validation = Stack-B-Quelle) ─────────────────────────

    public record PersonInput(@NotBlank String vorname, @NotBlank String nachname, String geschlecht,
            String titel, LocalDate geburtsdatum, String geburtsort, String geburtslandCode,
            String staatsangehoerigkeit1Code, String staatsangehoerigkeit2Code, String korrespondenzspracheCode,
            boolean werbesperre, boolean auskunftssperre, String leadQuelleCode) {
    }

    public record OrganisationInput(@NotBlank String name, String rechtsform, String handelsregisternummer,
            String registergericht, String brancheCode, String website, String ustId, Long uebergeordneteId,
            Integer bestandsgroesse, String gewerbeerlaubnis, String gewerbeerlaubnisBehoerde,
            LocalDate gewerbeerlaubnisDatum, boolean ausbildungsbetrieb, String ihkKammerCode,
            String leadQuelleCode, Set<String> unternehmenstypCodes, Set<String> schwerpunktCodes,
            Set<String> verbandCodes) {
    }

    public record MitgliedschaftInput(@NotBlank String rolleCode, String position, String abteilung,
            boolean hauptzugehoerigkeit, boolean hauptansprechpartner, boolean buchungsberechtigt,
            boolean rechnungsempfaenger, LocalDate gueltigVon, LocalDate gueltigBis) {
    }

    public record KontaktpunktInput(@NotBlank String typ, Long personId, Long organisationId,
            Long mitgliedschaftId, String label, boolean primaer, String email, String nummerE164,
            String nummerAnzeige, String telefonart, String strasse, String hausnummer, String adresszusatz,
            String plz, String ort, String region, String landCode, String auslandsblock) {
    }

    /** Schmaler Suchtreffer (Person ODER Organisation) für die globale Sofortsuche. */
    public record Treffer(String art, Long id, String titel, String untertitel) {
    }

    /** Eingabe einer Aktivität/Kontakthistorie (A9). Bezug = Person und/oder Organisation. */
    public record AktivitaetInput(@NotBlank String typCode, String richtung, @NotBlank String betreff,
            String inhaltHtml, Long personId, Long organisationId, Integer dauerMinuten) {
    }

    /** Eingabe einer Marketing-Einwilligung (A6). {@code organisationId} = null → globale Einwilligung. */
    public record EinwilligungInput(@NotNull Long personId, Long organisationId, @NotBlank String kanal,
            @NotBlank String zweck, String rechtsgrundlage, String quelleCode) {
    }

    /** Eingabe eines Weiterbildungsnachweises (A19, §34c GewO / §15b MaBV). */
    public record WeiterbildungInput(@NotNull Long personId, @NotBlank String titel, String anbieter,
            @NotNull BigDecimal stunden, @NotNull LocalDate datum, boolean extern) {
    }

    /** Eingabe der Live-Dublettenprüfung beim Anlegen (A16). {@code art} = PERSON|ORGANISATION. */
    public record DublettenPruefInput(@NotBlank String art, String vorname, String nachname, String titel,
            String name, String ustId) {
    }

    /** Ein potenzieller Dubletten-Bestandstreffer mit KI-/Regel-Bewertung (Vorschlag, keine Entscheidung). */
    public record DublettenKandidat(String art, Long id, String bezeichnung, String ort, double aehnlichkeit,
            String einschaetzung, String begruendung) {
    }

    // ───────────────────────── Person ─────────────────────────

    @Transactional
    public Person createPerson(PersonInput in) {
        Person p = new Person();
        p.status = Person.Status.AKTIV;
        applyPerson(p, in);
        p.persist();
        return p;
    }

    @Transactional
    public Person updatePerson(Long id, PersonInput in) {
        Person p = mussPerson(id);
        applyPerson(p, in);
        return p;
    }

    private void applyPerson(Person p, PersonInput in) {
        p.vorname = in.vorname().trim();
        p.nachname = in.nachname().trim();
        p.geschlecht = enumOf(Person.Geschlecht.class, in.geschlecht(), Person.Geschlecht.KEINE_ANGABE);
        p.titel = leer(in.titel());
        p.geburtsdatum = in.geburtsdatum();
        p.geburtsort = leer(in.geburtsort());
        p.geburtsland = land(in.geburtslandCode());
        p.staatsangehoerigkeit1 = land(in.staatsangehoerigkeit1Code());
        p.staatsangehoerigkeit2 = land(in.staatsangehoerigkeit2Code());
        p.korrespondenzsprache = lookup(Lookups.Sprache.class, in.korrespondenzspracheCode());
        p.werbesperre = in.werbesperre();
        p.auskunftssperre = in.auskunftssperre();
        if (in.leadQuelleCode() != null) {
            p.leadQuelle = lookup(Lookups.LeadQuelle.class, in.leadQuelleCode());
            if (p.leadDatum == null) {
                p.leadDatum = LocalDate.now();
            }
        }
        p.matchSchluessel = normalisiere(p.anzeigeName());
    }

    // ───────────────────────── Organisation ─────────────────────────

    @Transactional
    public Organisation createOrganisation(OrganisationInput in) {
        Organisation o = new Organisation();
        o.status = Organisation.Status.AKTIV;
        applyOrganisation(o, in);
        o.persist();
        return o;
    }

    @Transactional
    public Organisation updateOrganisation(Long id, OrganisationInput in) {
        Organisation o = mussOrganisation(id);
        applyOrganisation(o, in);
        return o;
    }

    private void applyOrganisation(Organisation o, OrganisationInput in) {
        o.name = in.name().trim();
        o.rechtsform = leer(in.rechtsform());
        o.handelsregisternummer = leer(in.handelsregisternummer());
        o.registergericht = leer(in.registergericht());
        o.branche = lookup(Lookups.Branche.class, in.brancheCode());
        o.website = leer(in.website());
        o.ustId = leer(in.ustId());
        o.uebergeordnete = in.uebergeordneteId() == null ? null : mussOrganisation(in.uebergeordneteId());
        o.bestandsgroesse = in.bestandsgroesse();
        o.gewerbeerlaubnis = enumOf(Organisation.Gewerbeerlaubnis.class, in.gewerbeerlaubnis(),
                Organisation.Gewerbeerlaubnis.KEINE);
        o.gewerbeerlaubnisBehoerde = leer(in.gewerbeerlaubnisBehoerde());
        o.gewerbeerlaubnisDatum = in.gewerbeerlaubnisDatum();
        o.ausbildungsbetrieb = in.ausbildungsbetrieb();
        o.ihkKammer = lookup(Lookups.IhkKammer.class, in.ihkKammerCode());
        if (in.leadQuelleCode() != null) {
            o.leadQuelle = lookup(Lookups.LeadQuelle.class, in.leadQuelleCode());
            if (o.leadDatum == null) {
                o.leadDatum = LocalDate.now();
            }
        }
        o.unternehmenstypen = aufloesen(Lookups.Unternehmenstyp.class, in.unternehmenstypCodes());
        o.taetigkeitsschwerpunkte = aufloesen(Lookups.Taetigkeitsschwerpunkt.class, in.schwerpunktCodes());
        o.verbandszugehoerigkeiten = aufloesen(Lookups.Verband.class, in.verbandCodes());
        o.matchSchluessel = DebitorHoheitService.matchSchluessel(o.name, null, o.ustId);
    }

    // ───────────────────────── Mitgliedschaft (N:M-Kern) ─────────────────────────

    @Transactional
    public Mitgliedschaft createMitgliedschaft(Long personId, Long organisationId, MitgliedschaftInput in) {
        Person p = mussPerson(personId);
        Organisation o = mussOrganisation(organisationId);
        Lookups.Rolle rolle = lookup(Lookups.Rolle.class, in.rolleCode());
        if (rolle == null) {
            throw RegelVerletzung.nichtGefunden("Rolle nicht gefunden: " + in.rolleCode());
        }
        long vorhanden = Mitgliedschaft.count(
                "person.id = ?1 and organisation.id = ?2 and rolle.id = ?3", p.id, o.id, rolle.id);
        if (vorhanden > 0) {
            throw new RegelVerletzung("Diese Person hat diese Rolle in dieser Organisation bereits.");
        }
        Mitgliedschaft m = new Mitgliedschaft();
        m.person = p;
        m.organisation = o;
        m.rolle = rolle;
        applyMitgliedschaft(m, in);
        m.persist();
        return m;
    }

    @Transactional
    public Mitgliedschaft updateMitgliedschaft(Long id, MitgliedschaftInput in) {
        Mitgliedschaft m = mussMitgliedschaft(id);
        m.rolle = lookup(Lookups.Rolle.class, in.rolleCode());
        applyMitgliedschaft(m, in);
        return m;
    }

    /** Ausscheiden = historisieren (gültigBis = heute), Haupt-Flags fallen weg. */
    @Transactional
    public Mitgliedschaft ausscheiden(Long id) {
        Mitgliedschaft m = mussMitgliedschaft(id);
        m.gueltigBis = LocalDate.now();
        m.hauptzugehoerigkeit = false;
        m.hauptansprechpartner = false;
        return m;
    }

    private void applyMitgliedschaft(Mitgliedschaft m, MitgliedschaftInput in) {
        m.position = leer(in.position());
        m.abteilung = leer(in.abteilung());
        m.buchungsberechtigt = in.buchungsberechtigt();
        m.rechnungsempfaenger = in.rechnungsempfaenger();
        m.gueltigVon = in.gueltigVon() == null ? LocalDate.now() : in.gueltigVon();
        m.gueltigBis = in.gueltigBis();
        // Invarianten: höchstens eine aktive Hauptzugehörigkeit/ein Hauptansprechpartner.
        if (in.hauptzugehoerigkeit()) {
            Mitgliedschaft.update("hauptzugehoerigkeit = false where person.id = ?1 and id <> ?2",
                    m.person.id, m.id == null ? -1L : m.id);
        }
        if (in.hauptansprechpartner()) {
            Mitgliedschaft.update("hauptansprechpartner = false where organisation.id = ?1 and id <> ?2",
                    m.organisation.id, m.id == null ? -1L : m.id);
        }
        m.hauptzugehoerigkeit = in.hauptzugehoerigkeit();
        m.hauptansprechpartner = in.hauptansprechpartner();
    }

    // ───────────────────────── Kontaktpunkt ─────────────────────────

    @Transactional
    public Kontaktpunkt saveKontaktpunkt(KontaktpunktInput in) {
        Kontaktpunkt k = new Kontaktpunkt();
        applyKontaktpunkt(k, in);
        k.persist();
        return k;
    }

    @Transactional
    public Kontaktpunkt updateKontaktpunkt(Long id, KontaktpunktInput in) {
        Kontaktpunkt k = Kontaktpunkt.findById(id);
        if (k == null) {
            throw RegelVerletzung.nichtGefunden("Kontaktpunkt nicht gefunden: " + id);
        }
        applyKontaktpunkt(k, in);
        return k;
    }

    @Transactional
    public void loescheKontaktpunkt(Long id) {
        if (!Kontaktpunkt.deleteById(id)) {
            throw RegelVerletzung.nichtGefunden("Kontaktpunkt nicht gefunden: " + id);
        }
    }

    private void applyKontaktpunkt(Kontaktpunkt k, KontaktpunktInput in) {
        k.typ = enumOf(Kontaktpunkt.Typ.class, in.typ(), null);
        if (k.typ == null) {
            throw new RegelVerletzung("Kontaktpunkt-Typ fehlt/ungültig: " + in.typ());
        }
        // Besitzer: genau einer
        k.person = in.personId() == null ? null : mussPerson(in.personId());
        k.organisation = in.organisationId() == null ? null : mussOrganisation(in.organisationId());
        k.mitgliedschaft = in.mitgliedschaftId() == null ? null : mussMitgliedschaft(in.mitgliedschaftId());
        long besitzer = (k.person != null ? 1 : 0) + (k.organisation != null ? 1 : 0)
                + (k.mitgliedschaft != null ? 1 : 0);
        if (besitzer != 1) {
            throw new RegelVerletzung("Ein Kontaktpunkt braucht genau einen Besitzer (Person, Organisation oder Mitgliedschaft).");
        }
        k.label = leer(in.label());
        k.primaer = in.primaer();
        switch (k.typ) {
            case EMAIL -> {
                k.email = pflicht(in.email(), "E-Mail").trim().toLowerCase();
            }
            case TELEFON -> {
                k.nummerE164 = leer(in.nummerE164());
                k.nummerAnzeige = pflicht(in.nummerAnzeige(), "Telefonnummer");
                k.telefonart = enumOf(Kontaktpunkt.Telefonart.class, in.telefonart(), null);
            }
            case ADRESSE -> {
                k.strasse = leer(in.strasse());
                k.hausnummer = leer(in.hausnummer());
                k.adresszusatz = leer(in.adresszusatz());
                Lookups.Land land = land(in.landCode());
                k.land = land;
                k.plz = pruefePlz(leer(in.plz()), land);
                k.ort = leer(in.ort());
                k.region = leer(in.region());
                k.auslandsblock = leer(in.auslandsblock());
            }
        }
    }

    /** Länderabhängige PLZ-Validierung (Plan A3): strenge Prüfung nur, wenn das Land eine Regel trägt. */
    private static String pruefePlz(String plz, Lookups.Land land) {
        if (plz != null && land != null && land.plzRegex != null && !plz.matches(land.plzRegex)) {
            throw new RegelVerletzung("PLZ '" + plz + "' ist für " + land.bezeichnung + " ungültig.");
        }
        return plz;
    }

    // ───────────────────────── Aktivität / Kontakthistorie (A9) ─────────────────────────

    @Transactional
    public Aktivitaet createAktivitaet(AktivitaetInput in) {
        if (in.personId() == null && in.organisationId() == null) {
            throw new RegelVerletzung("Eine Aktivität braucht einen Bezug (Person oder Organisation).");
        }
        Aktivitaet a = new Aktivitaet();
        a.typ = lookup(Lookups.Aktivitaetstyp.class, in.typCode());
        if (a.typ == null) {
            throw RegelVerletzung.nichtGefunden("Aktivitätstyp nicht gefunden: " + in.typCode());
        }
        a.richtung = enumOf(Aktivitaet.Richtung.class, in.richtung(), Aktivitaet.Richtung.AUSGEHEND);
        a.betreff = in.betreff().trim();
        a.inhaltHtml = leer(in.inhaltHtml());
        a.person = in.personId() == null ? null : mussPerson(in.personId());
        a.organisation = in.organisationId() == null ? null : mussOrganisation(in.organisationId());
        a.dauerMinuten = in.dauerMinuten();
        a.persist();
        return a;
    }

    /** Aktivität nachträglich bearbeiten (Backlog: Kontakthistorie editierbar). Bezug/Typ bleiben änderbar. */
    @Transactional
    public Aktivitaet updateAktivitaet(Long id, AktivitaetInput in) {
        Aktivitaet a = Aktivitaet.findById(id);
        if (a == null) {
            throw RegelVerletzung.nichtGefunden("Aktivität nicht gefunden: " + id);
        }
        Lookups.Aktivitaetstyp typ = lookup(Lookups.Aktivitaetstyp.class, in.typCode());
        if (typ == null) {
            throw RegelVerletzung.nichtGefunden("Aktivitätstyp nicht gefunden: " + in.typCode());
        }
        a.typ = typ;
        a.richtung = enumOf(Aktivitaet.Richtung.class, in.richtung(), Aktivitaet.Richtung.AUSGEHEND);
        a.betreff = in.betreff().trim();
        a.inhaltHtml = leer(in.inhaltHtml());
        a.dauerMinuten = in.dauerMinuten();
        return a;
    }

    /** Aktivität löschen (Backlog: Löschen im Bearbeiten-Fenster, mit Bestätigung im Frontend). */
    @Transactional
    public void loescheAktivitaet(Long id) {
        if (!Aktivitaet.deleteById(id)) {
            throw RegelVerletzung.nichtGefunden("Aktivität nicht gefunden: " + id);
        }
    }

    /** Kontakthistorie einer Person (neueste zuerst). */
    public List<Aktivitaet> aktivitaetenPerson(Long personId) {
        return Aktivitaet.list("person.id = ?1 order by zeitpunkt desc", personId);
    }

    /** Kontakthistorie einer Organisation inkl. der Aktivitäten ihrer verknüpften Personen (Plan: Firma-Kommunikation). */
    public List<Aktivitaet> aktivitaetenOrganisation(Long orgId) {
        return Aktivitaet.list(
                "organisation.id = ?1 or person.id in (select m.person.id from Mitgliedschaft m where m.organisation.id = ?1) "
                        + "order by zeitpunkt desc", orgId);
    }

    // ───────────────────────── Einwilligung / Opt-In (A6) ─────────────────────────

    /** Neue Einwilligung erfassen: startet {@code AUSSTEHEND} (Double-Opt-In folgt mit {@link #einwilligungErteilen}). */
    @Transactional
    public Einwilligung createEinwilligung(EinwilligungInput in) {
        Einwilligung e = new Einwilligung();
        e.person = mussPerson(in.personId());
        e.organisation = in.organisationId() == null ? null : mussOrganisation(in.organisationId());
        e.kanal = enumOf(Einwilligung.Kanal.class, in.kanal(), null);
        e.zweck = enumOf(Einwilligung.Zweck.class, in.zweck(), null);
        if (e.kanal == null || e.zweck == null) {
            throw new RegelVerletzung("Kanal und Zweck sind erforderlich.");
        }
        e.rechtsgrundlage = enumOf(Einwilligung.Rechtsgrundlage.class, in.rechtsgrundlage(),
                Einwilligung.Rechtsgrundlage.EINWILLIGUNG_6_1_A);
        e.quelle = lookup(Lookups.LeadQuelle.class, in.quelleCode());
        e.status = Einwilligung.Status.AUSSTEHEND;
        e.ausstehendSeit = LocalDateTime.now();
        e.persist();
        return e;
    }

    /** Double-Opt-In bestätigen: {@code ERTEILT} + Nachweis (Token/Zeit; IP käme aus dem Bestätigungs-Request). */
    @Transactional
    public Einwilligung einwilligungErteilen(Long id) {
        Einwilligung e = mussEinwilligung(id);
        if (e.status == Einwilligung.Status.WIDERRUFEN) {
            throw new RegelVerletzung("Eine widerrufene Einwilligung kann nicht erteilt werden.");
        }
        e.status = Einwilligung.Status.ERTEILT;
        e.erteiltAm = LocalDateTime.now();
        e.nachweisToken = java.util.UUID.randomUUID().toString();
        e.nachweisZeit = e.erteiltAm;
        return e;
    }

    /** Einwilligung widerrufen (jederzeit, Art. 7 Abs. 3 DSGVO). */
    @Transactional
    public Einwilligung einwilligungWiderrufen(Long id) {
        Einwilligung e = mussEinwilligung(id);
        e.status = Einwilligung.Status.WIDERRUFEN;
        e.widerrufenAm = LocalDateTime.now();
        return e;
    }

    public List<Einwilligung> einwilligungenPerson(Long personId) {
        return Einwilligung.list("person.id = ?1 order by id desc", personId);
    }

    /** Einwilligungen im Kontext einer Organisation (Firmen-Opt-Ins der verknüpften Personen). */
    public List<Einwilligung> einwilligungenOrganisation(Long orgId) {
        mussOrganisation(orgId);
        return Einwilligung.list("organisation.id = ?1 order by id desc", orgId);
    }

    // ───────────────────────── Weiterbildung §34c / §15b MaBV (A19) ─────────────────────────

    /** Statutarisches 3-Jahres-Soll der Weiterbildungspflicht (§15b MaBV: 20 Std. je 3-Jahres-Zeitraum). */
    private static final BigDecimal SOLL_STUNDEN = new BigDecimal("20");
    /** Beginn des ersten statutarischen Weiterbildungszeitraums (MaBV: ab 2018, danach 3-Jahres-Blöcke). */
    private static final int ZEITRAUM_BASIS_JAHR = 2018;

    /** Abgeleitetes Stundenkonto für den laufenden 3-Jahres-Zeitraum inkl. Frist-Ampel. */
    public record WeiterbildungKonto(LocalDate zeitraumVon, LocalDate zeitraumBis, BigDecimal soll,
            BigDecimal summe, BigDecimal rest, boolean erfuellt, String ampel,
            List<Weiterbildungsnachweis> nachweise) {
    }

    @Transactional
    public Weiterbildungsnachweis createWeiterbildung(WeiterbildungInput in) {
        if (in.stunden().signum() <= 0) {
            throw new RegelVerletzung("Stunden müssen größer als 0 sein.");
        }
        Weiterbildungsnachweis w = new Weiterbildungsnachweis();
        w.person = mussPerson(in.personId());
        w.titel = in.titel().trim();
        w.anbieter = leer(in.anbieter());
        w.stunden = in.stunden();
        w.datum = in.datum();
        w.extern = in.extern();
        w.persist();
        return w;
    }

    @Transactional
    public void loescheWeiterbildung(Long id) {
        if (!Weiterbildungsnachweis.deleteById(id)) {
            throw RegelVerletzung.nichtGefunden("Weiterbildungsnachweis nicht gefunden: " + id);
        }
    }

    /** Eine Zeile der Firmen-Weiterbildungsübersicht (Pflicht §34c je Mitarbeiter, A19/Org-Sicht). */
    public record WeiterbildungOrgZeile(Long personId, String personName, BigDecimal summe, BigDecimal soll,
            boolean erfuellt, String ampel) {
    }

    /** Stundenkonto der Person für den aktuell laufenden statutarischen 3-Jahres-Zeitraum. */
    public WeiterbildungKonto weiterbildungskonto(Long personId) {
        return kontoFuer(mussPerson(personId));
    }

    /**
     * Firmen-Weiterbildungsübersicht (A19/Org-Sicht): je <b>aktuell zugehöriger</b> Person des Maklerbetriebs
     * eine Zeile mit Ampel — so sehen EBZ-Mitarbeiter auf einen Blick, welche Mitarbeiter die §34c-Pflicht
     * (noch) nicht erfüllen. Distinct je Person (Mehrfachrollen zählen einmal).
     */
    public List<WeiterbildungOrgZeile> weiterbildungOrganisation(Long orgId) {
        mussOrganisation(orgId);
        List<Person> personen = Person.list(
                "id in (select distinct m.person.id from Mitgliedschaft m where m.organisation.id = ?1 "
                        + "and (m.gueltigBis is null or m.gueltigBis >= ?2)) order by nachname, vorname",
                orgId, LocalDate.now());
        return personen.stream().map(p -> {
            WeiterbildungKonto k = kontoFuer(p);
            return new WeiterbildungOrgZeile(p.id, p.anzeigeName(), k.summe(), k.soll(), k.erfuellt(), k.ampel());
        }).toList();
    }

    private WeiterbildungKonto kontoFuer(Person p) {
        int jahr = LocalDate.now().getYear();
        int startJahr = ZEITRAUM_BASIS_JAHR + 3 * ((jahr - ZEITRAUM_BASIS_JAHR) / 3);
        LocalDate von = LocalDate.of(startJahr, 1, 1);
        LocalDate bis = LocalDate.of(startJahr + 2, 12, 31);
        List<Weiterbildungsnachweis> alle = Weiterbildungsnachweis.list("person.id = ?1 order by datum desc", p.id);
        BigDecimal summe = alle.stream()
                .filter(w -> !w.datum.isBefore(von) && !w.datum.isAfter(bis))
                .map(w -> w.stunden).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean erfuellt = summe.compareTo(SOLL_STUNDEN) >= 0;
        BigDecimal rest = erfuellt ? BigDecimal.ZERO : SOLL_STUNDEN.subtract(summe);
        // Ampel: erfüllt = grün; sonst rot im letzten Jahr des Zeitraums, davor gelb (Frist-Mahnung).
        String ampel = erfuellt ? "GRUEN" : (jahr == startJahr + 2 ? "ROT" : "GELB");
        return new WeiterbildungKonto(von, bis, SOLL_STUNDEN, summe, rest, erfuellt, ampel, alle);
    }

    // ───────────────────────── Live-Dublettenprüfung beim Anlegen (A16) ─────────────────────────

    /**
     * Vorab-Dublettencheck (Plan A16, Fallstrick 10): billiger Bestands-Vorfilter über Name/USt-IdNr.
     * (kein Tastendruck-Spam — das Frontend ruft debounced an), anschließend Bewertung der wenigen
     * Treffer durch den {@link DublettenBerater} (KI mit robustem regelbasiertem Fallback). Liefert
     * <b>Vorschläge</b> zum Verknüpfen/Mergen; die finale Auflösung trifft der Mensch (HITL).
     */
    public List<DublettenKandidat> pruefeDubletten(DublettenPruefInput in) {
        if ("ORGANISATION".equalsIgnoreCase(in.art())) {
            return pruefeFirma(leer(in.name()), leer(in.ustId()));
        }
        return pruefePerson(leer(in.vorname()), leer(in.nachname()), in.titel());
    }

    private List<DublettenKandidat> pruefePerson(String vorname, String nachname, String titel) {
        if (vorname == null || nachname == null) {
            return List.of();
        }
        List<Person> treffer = Person.list("status <> ?1 and lower(vorname) = ?2 and lower(nachname) = ?3",
                Person.Status.ZUSAMMENGEFUEHRT, vorname.toLowerCase(), nachname.toLowerCase());
        String anzeige = ((titel == null || titel.isBlank() ? "" : titel.trim() + " ")
                + vorname + " " + nachname).trim();
        return treffer.stream().limit(DUBLETTEN_LIMIT).map(z -> {
            DublettenUrteil u = dublettenBerater.bewertePersonKandidat(anzeige, z);
            return new DublettenKandidat("PERSON", z.id, z.anzeigeName(),
                    PartyHoheitService.personAdresse(z.id).ort(), u.aehnlichkeit(),
                    u.einschaetzung().name(), u.begruendung());
        }).toList();
    }

    private List<DublettenKandidat> pruefeFirma(String name, String ustId) {
        if (name == null) {
            return List.of();
        }
        String n = name.toLowerCase();
        List<Organisation> treffer = (ustId == null)
                ? Organisation.list("status <> ?1 and lower(name) = ?2",
                        Organisation.Status.ZUSAMMENGEFUEHRT, n)
                : Organisation.list("status <> ?1 and (lower(name) = ?2 or lower(ustId) = ?3)",
                        Organisation.Status.ZUSAMMENGEFUEHRT, n, ustId.toLowerCase());
        return treffer.stream().limit(DUBLETTEN_LIMIT).map(z -> {
            DublettenUrteil u = dublettenBerater.bewerteFirmaKandidat(name, ustId, z);
            return new DublettenKandidat("ORGANISATION", z.id, z.name,
                    PartyHoheitService.orgAdresse(z.id).ort(), u.aehnlichkeit(),
                    u.einschaetzung().name(), u.begruendung());
        }).toList();
    }

    // ───────────────────────── 360°-Sicht (A18) ─────────────────────────

    /** 360°-Bündel: read-only Anmeldungen/Buchungen + festgeschriebene Rechnungen eines Kontakts. */
    public record Uebersicht(List<Anmeldung> anmeldungen, List<Rechnung> rechnungen) {
    }

    /**
     * 360°-Sicht einer Person: ihre eigenen Anmeldungen/Buchungen (als Teilnehmer) + die Rechnungen
     * ihres PRIVAT-Debitors. Firmenkontext-Belege gehören zur jeweiligen Organisation und erscheinen
     * dort, nicht hier (DSGVO-Trennung privat/dienstlich).
     */
    public Uebersicht uebersichtPerson(Long personId) {
        Person p = mussPerson(personId);
        List<Anmeldung> anmeldungen = Anmeldung.list("teilnehmerPerson.id = ?1 order by id desc", personId);
        List<Rechnung> rechnungen = rechnungenFuer(PartyHoheitService.privatDebitorSchluessel(p),
                DebitorRolle.PRIVAT);
        return new Uebersicht(anmeldungen, rechnungen);
    }

    /**
     * 360°-Sicht einer Organisation (DSGVO-Scope wie {@link BuchungService#firmensicht}): <b>nur</b>
     * Buchungen im Kontext dieser Organisation + die Rechnungen ihres FIRMA-Debitors. Privatbuchungen
     * der verknüpften Personen sind strukturell ausgeschlossen.
     */
    public Uebersicht uebersichtOrganisation(Long orgId) {
        Organisation o = mussOrganisation(orgId);
        List<Anmeldung> anmeldungen = Anmeldung.list("kontextOrganisation.id = ?1 order by id desc", orgId);
        List<Rechnung> rechnungen = rechnungenFuer(o.matchSchluessel, DebitorRolle.FIRMA);
        return new Uebersicht(anmeldungen, rechnungen);
    }

    /**
     * Festgeschriebene Rechnungen (kein {@code ENTWURF}) der AKTIVen Debitoren zu einem
     * {@code matchSchluessel} — exakter Schlüssel-Join auf die bestehende Debitor-Hoheit (wie der
     * Portal-Self-Service), ohne neue Debitoren anzulegen.
     */
    private static List<Rechnung> rechnungenFuer(String matchSchluessel, DebitorRolle rolle) {
        if (matchSchluessel == null || matchSchluessel.isBlank()) {
            return List.of();
        }
        List<Debitor> debitoren = Debitor.list("matchSchluessel = ?1 and rolle = ?2 and status = ?3",
                matchSchluessel, rolle, DebitorStatus.AKTIV);
        if (debitoren.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = debitoren.stream().map(d -> d.id).collect(Collectors.toSet());
        return Rechnung.list("debitor.id in ?1 and status <> ?2 order by ausstellungsdatum desc, id desc",
                ids, RechnungStatus.ENTWURF);
    }

    // ───────────────────────── Recht auf Vergessen (A7, Art. 17 DSGVO) ─────────────────────────

    /** Aufbewahrungsfrist (GoBD/§147 AO) bis zur planmäßigen Anonymisierung, falls keine angegeben. */
    private static final int AUFBEWAHRUNG_JAHRE_DEFAULT = 10;

    /**
     * Phase 1 (Einschränkung der Verarbeitung, Art. 18): Person <b>sperren</b> + Werbe-/Auskunftssperre
     * setzen + Anonymisierung auf das Ende der Aufbewahrungsfrist terminieren. Reversibel-nah; die
     * eigentliche Löschung folgt mit {@link #personAnonymisieren}.
     */
    @Transactional
    public Person personSperren(Long personId, Integer aufbewahrungJahre) {
        Person p = mussPerson(personId);
        int jahre = (aufbewahrungJahre == null || aufbewahrungJahre < 0)
                ? AUFBEWAHRUNG_JAHRE_DEFAULT : aufbewahrungJahre;
        p.loeschStatus = Person.LoeschStatus.GESPERRT;
        p.werbesperre = true;
        p.auskunftssperre = true;
        p.anonymisierenAb = LocalDate.now().plusYears(jahre);
        return p;
    }

    /**
     * Phase 2 (Löschung, Art. 17): personenbezogene Daten <b>irreversibel anonymisieren</b> — Kernfelder
     * überschreiben, Kommunikationskanäle ({@link Kontaktpunkt}) und Login-Identitäten ({@link Login})
     * entfernen und die Envers-<b>Versionshistorie purgen</b> (sonst lebte die Person in den {@code _aud}-
     * Tabellen weiter). Buchungs-/Rechnungsbelege bleiben aus Aufbewahrungsgründen bestehen, verlieren aber
     * den Personenbezug (Schlüssel anonymisiert).
     */
    @Transactional
    public Person personAnonymisieren(Long personId) {
        Person p = mussPerson(personId);
        Kontaktpunkt.delete("person.id = ?1", personId);
        Login.delete("person.id = ?1", personId);
        p.vorname = "Anonymisiert";
        p.nachname = "#" + personId;
        p.geschlecht = Person.Geschlecht.KEINE_ANGABE;
        p.titel = null;
        p.geburtsdatum = null;
        p.geburtsort = null;
        p.geburtsland = null;
        p.staatsangehoerigkeit1 = null;
        p.staatsangehoerigkeit2 = null;
        p.fotoUrl = null;
        p.keycloakSub = null;
        p.leadQuelle = null;
        p.matchSchluessel = null;
        p.werbesperre = true;
        p.auskunftssperre = true;
        p.loeschStatus = Person.LoeschStatus.ANONYMISIERT;
        p.anonymisierenAb = LocalDate.now();
        // Versionsdaten endgültig entfernen (A7) — nach Flush, damit die Lösch-DML eingespielt ist.
        io.quarkus.hibernate.orm.panache.Panache.getEntityManager().flush();
        purgeAudit("kontaktpunkt_aud", "person_id", personId);
        purgeAudit("login_aud", "person_id", personId);
        purgeAudit("person_aud", "id", personId);
        return p;
    }

    /** Best-effort-Purge einer Envers-Audit-Tabelle (Tabellen-/Spaltennamen sind code-konstant, kein Injection-Pfad). */
    private static void purgeAudit(String table, String whereSpalte, Object wert) {
        var em = io.quarkus.hibernate.orm.panache.Panache.getEntityManager();
        Object existiert = em.createNativeQuery(
                        "select 1 from information_schema.tables where table_schema = 'mdm' and table_name = ?1")
                .setParameter(1, table).getResultStream().findFirst().orElse(null);
        if (existiert == null) {
            return;
        }
        em.createNativeQuery("delete from mdm." + table + " where " + whereSpalte + " = ?1")
                .setParameter(1, wert).executeUpdate();
    }

    // ───────────────────────── Suche ─────────────────────────

    public List<Treffer> suche(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        List<Treffer> personen = Person.<Person>list(
                        "lower(vorname || ' ' || nachname) like ?1 and status <> ?2",
                        like, Person.Status.ZUSAMMENGEFUEHRT).stream()
                .limit(20)
                .map(p -> new Treffer("PERSON", p.id, p.anzeigeName(), p.briefanrede()))
                .toList();
        List<Treffer> firmen = Organisation.<Organisation>list(
                        "lower(name) like ?1 and status <> ?2", like, Organisation.Status.ZUSAMMENGEFUEHRT).stream()
                .limit(20)
                .map(o -> new Treffer("ORGANISATION", o.id, o.name, o.ustId))
                .toList();
        return java.util.stream.Stream.concat(personen.stream(), firmen.stream()).toList();
    }

    // ───────────────────────── intern ─────────────────────────

    private static Person mussPerson(Long id) {
        Person p = Person.findById(id);
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + id);
        }
        return p;
    }

    private static Organisation mussOrganisation(Long id) {
        Organisation o = Organisation.findById(id);
        if (o == null) {
            throw RegelVerletzung.nichtGefunden("Organisation nicht gefunden: " + id);
        }
        return o;
    }

    private static Mitgliedschaft mussMitgliedschaft(Long id) {
        Mitgliedschaft m = Mitgliedschaft.findById(id);
        if (m == null) {
            throw RegelVerletzung.nichtGefunden("Mitgliedschaft nicht gefunden: " + id);
        }
        return m;
    }

    private static Einwilligung mussEinwilligung(Long id) {
        Einwilligung e = Einwilligung.findById(id);
        if (e == null) {
            throw RegelVerletzung.nichtGefunden("Einwilligung nicht gefunden: " + id);
        }
        return e;
    }

    private static Lookups.Land land(String code) {
        return code == null || code.isBlank() ? null : lookup(Lookups.Land.class, code.toUpperCase());
    }

    /**
     * Löst einen Lookup-Wert per {@code code} typsicher auf. Die JPQL-Entity-Namen folgen der Konvention
     * {@code "Lookup" + SimpleName} (siehe {@code @Entity(name=…)} in {@link Lookups}).
     */
    private static <T extends Lookups.LookupBase> T lookup(Class<T> typ, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return io.quarkus.hibernate.orm.panache.Panache.getEntityManager()
                .createQuery("select e from Lookup" + typ.getSimpleName() + " e where e.code = :c", typ)
                .setParameter("c", code).getResultStream().findFirst().orElse(null);
    }

    private static <T extends Lookups.LookupBase> Set<T> aufloesen(Class<T> typ, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new java.util.HashSet<>();
        }
        return codes.stream().map(c -> lookup(typ, c)).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }

    private static <E extends Enum<E>> E enumOf(Class<E> typ, String wert, E fallback) {
        if (wert == null || wert.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(typ, wert.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegelVerletzung("Ungültiger Wert '" + wert + "' für " + typ.getSimpleName() + ".");
        }
    }

    private static String leer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String pflicht(String s, String feld) {
        if (s == null || s.isBlank()) {
            throw new RegelVerletzung(feld + " ist erforderlich.");
        }
        return s;
    }

    private static String normalisiere(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
