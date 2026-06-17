package de.netzfactor.ebz.controlling.integration.party.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;

import de.netzfactor.ebz.controlling.integration.party.model.Aktivitaet;
import de.netzfactor.ebz.controlling.integration.party.model.Kontaktpunkt;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
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
