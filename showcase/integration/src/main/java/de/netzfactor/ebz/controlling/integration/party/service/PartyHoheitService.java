package de.netzfactor.ebz.controlling.integration.party.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorRolle;
import de.netzfactor.ebz.controlling.integration.rechnung.service.DebitorHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * Party-Kern (Identitäts-Hoheit): das „eine Gehirn" für Personen-Identität und Bestellkontexte — der
 * Differenzierer, der bewusst <b>selbst gebaut</b> wird (HubSpot bleibt reines Marketing).
 *
 * <p>Löst die Leitfrage <i>„eine Identität, n Bestellkontexte (privat / Hauptfirma / Nebenbeschäftigung)"</i>:
 * <ul>
 *   <li><b>Identität = {@link Person}</b>, eindeutig über die globale {@link PersonEmail}-Unique —
 *       <i>nicht</i> die E-Mail ist die Identität, sondern die Person; E-Mail ist Auflösungsschlüssel.</li>
 *   <li><b>Kontext = pro Vorgang gewählt</b> aus {@code {PRIVAT} ∪ {Organisationen mit aktiver,
 *       buchungsberechtigter Mitgliedschaft}} — Haupt- und Nebenfirma sind schlicht zwei FIRMA-Kontexte.</li>
 *   <li><b>Abrechnung folgt dem Kontext</b>: {@link #ermittleDebitor} projiziert den passenden Debitor
 *       (privat → eigener; Firma → Org-Debitor) über die bestehende {@link DebitorHoheitService}.</li>
 * </ul>
 * Die „selbe E-Mail privat und als Firmen-Azubi"-Kollision verschwindet so: firmenseitige Vor-Anlage
 * und private Selbstregistrierung konvergieren idempotent auf <i>eine</i> Person ({@link #selbstRegistrieren}
 * = Account-Claiming). Match/Merge auf Personenebene räumt Zweit-Adressen/Dubletten zusammen.
 */
@ApplicationScoped
public class PartyHoheitService {

    @Inject
    DebitorHoheitService debitorHoheit;

    @Inject
    Prozessspur prozess;

    /** Ein wählbarer Bestellkontext einer Person. {@code organisationId == null} ⇒ PRIVAT. */
    public record Kontext(Art art, Long organisationId, String bezeichnung, List<Mitgliedschaft.Rolle> rollen) {
        public enum Art { PRIVAT, FIRMA }
    }

    /** Ergebnis einer Self-Service-Anfrage: die (provisorische) Organisation + ihr Ansprechpartner. */
    public record AnfrageErgebnis(Organisation organisation, Person ansprechpartner) {
    }

    // ───────────────────────── Identität: anlegen / claimen / mergen ─────────────────────────

    /**
     * Selbstregistrierung/Login eines Menschen mit seiner E-Mail. Existiert die Adresse bereits (z. B.
     * von der Firma als Azubi vor-angelegt), wird <b>dieselbe Person geclaimt</b>: Keycloak-{@code sub}
     * gebunden, Adresse verifiziert, Status → AKTIV. Sonst entsteht eine neue, sofort aktive Person.
     */
    @Transactional
    public Person selbstRegistrieren(String keycloakSub, String email, String anzeigeName) {
        prozess.schritt("Login & Konto-Claim", Akteur.FIRMA, Prozess.System.KEYCLOAK, Typ.USER_TASK,
                Phase.EINLADUNG);
        // 1) Adresse bekannt → diese Person claimen (Login binden, Adresse verifizieren).
        PersonEmail vorhanden = PersonEmail.find("email", normEmail(email)).firstResult();
        if (vorhanden != null) {
            Person p = golden(Person.findById(vorhanden.personId()));
            bindeSub(p, keycloakSub);
            vorhanden.verifiziert = true;
            return p;
        }
        // 2) Adresse neu, aber Login schon bekannt (Folge-Login mit weiterer Adresse) → Adresse anhängen.
        Person bySub = keycloakSub == null ? null : Person.find("keycloakSub", keycloakSub).firstResult();
        if (bySub != null) {
            Person p = golden(bySub);
            legeEmailAn(p, email, true, false);
            return p;
        }
        // 3) Beides neu → neue, sofort aktive Identität.
        Person p = neuePerson(anzeigeName, Person.Status.AKTIV);
        p.keycloakSub = keycloakSub;
        p.persist();
        legeEmailAn(p, email, true, true);
        return p;
    }

    /** Auflösung des Aufrufers über den Token-{@code sub} (für die Sicht-Autorisierung). */
    public Person findeNachSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return null;
        }
        return golden(Person.find("keycloakSub", keycloakSub).firstResult());
    }

    /** Hat die Person (irgend)eine Mitgliedschaft in der Organisation? (Basis der Firmenportal-Scope-Prüfung.) */
    public boolean istMitglied(Long personId, Long organisationId) {
        return Mitgliedschaft.count("person.id = ?1 and organisation.id = ?2", personId, organisationId) > 0;
    }

    /** Darf die Person im Auftrag der Organisation bestellen? (Scope-Prüfung für das Self-Service-Portal.) */
    public boolean istBuchungsberechtigt(Long personId, Long organisationId) {
        return Mitgliedschaft.count("person.id = ?1 and organisation.id = ?2 and buchungsberechtigt = true",
                personId, organisationId) > 0;
    }

    /**
     * Firmenseitige Vor-Anlage eines Teilnehmers/Ansprechpartners per E-Mail: findet die Person über die
     * Adresse oder legt sie <b>provisorisch</b> an (noch kein Login), und verknüpft sie idempotent per
     * {@link Mitgliedschaft} mit der Organisation. Loggt der Mensch sich später selbst ein, greift
     * {@link #selbstRegistrieren} auf genau diese Person.
     */
    @Transactional
    public Person registriereTeilnehmer(Long organisationId, String email, String anzeigeName,
            Mitgliedschaft.Rolle rolle, boolean buchungsberechtigt) {
        Organisation o = mussOrganisation(organisationId);
        PersonEmail vorhanden = PersonEmail.find("email", normEmail(email)).firstResult();
        Person p;
        if (vorhanden != null) {
            p = golden(Person.findById(vorhanden.personId()));
        } else {
            p = neuePerson(anzeigeName, Person.Status.PROVISORISCH);
            p.persist();
            legeEmailAn(p, email, false, true);
        }
        legeMitgliedschaftAn(p, o, rolle, buchungsberechtigt);
        return p;
    }

    /**
     * Idempotente Identitäts-Auflösung allein über die E-Mail (ohne Login/Org) — der Anker für
     * Quellsysteme ohne Keycloak-Kontext, z. B. ein Shop-Gast (R7). Existiert die Adresse, wird die
     * Person wiederverwendet; sonst entsteht eine provisorische Person (claimbar beim späteren Login).
     */
    @Transactional
    public Person findeOderLegePerson(String email, String anzeigeName) {
        PersonEmail vorhanden = PersonEmail.find("email", normEmail(email)).firstResult();
        if (vorhanden != null) {
            return golden(Person.findById(vorhanden.personId()));
        }
        Person p = neuePerson(anzeigeName, Person.Status.PROVISORISCH);
        p.persist();
        legeEmailAn(p, email, false, true);
        return p;
    }

    /** Merge-Kandidaten: andere aktive Personen mit gleichem (schwachem) Namensschlüssel. */
    public List<Person> kandidaten(Long personId) {
        Person p = Person.findById(personId);
        if (p == null || p.matchSchluessel == null) {
            return List.of();
        }
        // Dubletten-Kandidaten schließen provisorische (firmenseitig vor-angelegte) Personen ein.
        return Person.list("status <> ?1 and matchSchluessel = ?2 and id <> ?3",
                Person.Status.ZUSAMMENGEFUEHRT, p.matchSchluessel, p.id);
    }

    /**
     * Führt {@code quell} in {@code ziel} zusammen: E-Mails und Mitgliedschaften werden umgehängt, der
     * Login-{@code sub} ggf. übernommen, die unterlegene Person wird ZUSAMMENGEFUEHRT (zeigt per
     * {@link Person#goldenPersonId} auf {@code ziel}). Same-Email-Dubletten über zwei Adressen lösen
     * sich so zu einer Identität auf.
     */
    @Transactional
    public Person merge(Long quellId, Long zielId) {
        if (quellId.equals(zielId)) {
            throw new RegelVerletzung("Quell- und Ziel-Person sind identisch.");
        }
        Person quell = mussAktiv(quellId);
        Person ziel = mussAktiv(zielId);
        PersonEmail.update("person = ?1 where person.id = ?2", ziel, quell.id);
        Mitgliedschaft.update("person = ?1 where person.id = ?2", ziel, quell.id);
        if (ziel.keycloakSub == null && quell.keycloakSub != null) {
            ziel.keycloakSub = quell.keycloakSub;
            quell.keycloakSub = null; // Unique-Constraint: sub darf nur einmal existieren
        }
        quell.status = Person.Status.ZUSAMMENGEFUEHRT;
        quell.goldenPersonId = ziel.id;
        return ziel;
    }

    // ───────────────────────── Organisation: anlegen / Dubletten / mergen ─────────────────────────

    /**
     * Legt eine Organisation an und setzt den {@link Organisation#matchSchluessel} mit <i>derselben</i>
     * Normalisierung wie der Debitor ({@link DebitorHoheitService#matchSchluessel}) — so ist die spätere
     * Auflösung Debitor↔Organisation ein exakter Schlüssel-Join, kein Raten.
     */
    @Transactional
    public Organisation legeOrganisationAn(String name, String strasse, String plz, String ort,
            String land, String ustId, Organisation.Status status) {
        Organisation o = new Organisation();
        o.name = name;
        o.strasse = strasse;
        o.plz = plz;
        o.ort = ort;
        o.land = land;
        o.ustId = ustId;
        o.status = status == null ? Organisation.Status.AKTIV : status;
        o.matchSchluessel = DebitorHoheitService.matchSchluessel(name, plz, ustId);
        o.persist();
        return o;
    }

    /**
     * Self-Service-Anfrage eines Ausbildungsbetriebs: legt die Organisation <b>provisorisch</b>
     * ({@code ANGEFRAGT}) an und verknüpft den Ansprechpartner als buchungsberechtigte
     * {@code AUSBILDER}-{@link Mitgliedschaft} (Person provisorisch, claimbar beim späteren Login).
     * Kein Login wird hier erzeugt — der entsteht erst nach der HITL-/KI-Dublettenprüfung.
     */
    @Transactional
    public AnfrageErgebnis anfrageAusbildungsbetrieb(String name, String strasse, String plz, String ort,
            String land, String ustId, String ansprechpartnerEmail, String ansprechpartnerName) {
        prozess.schritt("Ausbildungsbetrieb-Anfrage stellen", Akteur.ANONYM, Prozess.System.PORTAL,
                Typ.USER_TASK, Phase.ANFRAGE_DUBLETTEN);
        Organisation o = legeOrganisationAn(name, strasse, plz, ort, land, ustId, Organisation.Status.ANGEFRAGT);
        Person ap = registriereTeilnehmer(o.id, ansprechpartnerEmail, ansprechpartnerName,
                Mitgliedschaft.Rolle.AUSBILDER, true);
        return new AnfrageErgebnis(o, ap);
    }

    /** Dubletten-Kandidaten einer Organisation: andere, nicht zusammengeführte Firmen mit gleichem Schlüssel. */
    public List<Organisation> organisationKandidaten(Long organisationId) {
        Organisation o = Organisation.findById(organisationId);
        if (o == null || o.matchSchluessel == null) {
            return List.of();
        }
        return Organisation.list("status <> ?1 and matchSchluessel = ?2 and id <> ?3",
                Organisation.Status.ZUSAMMENGEFUEHRT, o.matchSchluessel, o.id);
    }

    /**
     * Führt die Quell-Organisation in die Ziel-Organisation zusammen (HITL-Entscheidung): Mitgliedschaften
     * werden umgehängt, die unterlegene Firma wird {@code ZUSAMMENGEFUEHRT} und zeigt per
     * {@link Organisation#goldenOrganisationId} auf das Ziel.
     */
    @Transactional
    public Organisation mergeOrganisation(Long quellId, Long zielId) {
        if (quellId.equals(zielId)) {
            throw new RegelVerletzung("Quell- und Ziel-Organisation sind identisch.");
        }
        Organisation quell = mussOrganisationAktiv(quellId);
        Organisation ziel = mussOrganisationAktiv(zielId);
        Mitgliedschaft.update("organisation = ?1 where organisation.id = ?2", ziel, quell.id);
        quell.status = Organisation.Status.ZUSAMMENGEFUEHRT;
        quell.goldenOrganisationId = ziel.id;
        return ziel;
    }

    // ───────────────────────── Kontexte & Abrechnungs-Projektion ─────────────────────────

    /** Wählbare Bestellkontexte: PRIVAT + jede Organisation mit aktiver, buchungsberechtigter Mitgliedschaft. */
    public List<Kontext> kontexte(Long personId) {
        Person p = golden(Person.findById(personId));
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + personId);
        }
        List<Kontext> ergebnis = new ArrayList<>();
        ergebnis.add(new Kontext(Kontext.Art.PRIVAT, null, "Privat", List.of()));
        LocalDate heute = LocalDate.now();
        List<Mitgliedschaft> mitgliedschaften = Mitgliedschaft.list("person.id", p.id);
        // Pro Organisation einen FIRMA-Kontext, wenn mindestens eine aktive buchungsberechtigte Rolle besteht.
        mitgliedschaften.stream()
                .filter(m -> m.buchungsberechtigt && aktiv(m, heute))
                .map(Mitgliedschaft::organisationId)
                .distinct()
                .forEach(orgId -> {
                    Organisation o = Organisation.findById(orgId);
                    List<Mitgliedschaft.Rolle> rollen = mitgliedschaften.stream()
                            .filter(m -> m.organisationId().equals(orgId) && m.buchungsberechtigt && aktiv(m, heute))
                            .map(m -> m.rolle).toList();
                    ergebnis.add(new Kontext(Kontext.Art.FIRMA, orgId,
                            o == null ? ("Organisation " + orgId) : o.name, rollen));
                });
        return ergebnis;
    }

    /**
     * Projiziert den Abrechnungs-Debitor für einen gewählten Kontext und Bereich — der Naht-Punkt zur
     * Billing-Hoheit: {@code organisationId == null} ⇒ privater Debitor der Person, sonst der
     * Org-Debitor (Bestellberechtigung wird geprüft). Idempotent via {@link DebitorHoheitService}.
     */
    @Transactional
    public Debitor ermittleDebitor(Long personId, Long organisationId, Bereich bereich) {
        Person p = golden(Person.findById(personId));
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + personId);
        }
        if (organisationId == null) {
            return debitorHoheit.findeOderLege(new DebitorHoheitService.Stammdaten(
                    bereich, DebitorRolle.PRIVAT, p.anzeigeName, null, p.plz, p.ort, "DE",
                    null, null, primaerEmail(p.id)));
        }
        mussBuchungsberechtigt(p.id, organisationId);
        Organisation o = mussOrganisation(organisationId);
        return debitorHoheit.findeOderLege(new DebitorHoheitService.Stammdaten(
                bereich, DebitorRolle.FIRMA, o.name, o.strasse, o.plz, o.ort,
                o.land == null ? "DE" : o.land, o.ustId, null, null));
    }

    // ───────────────────────── intern ─────────────────────────

    private static Person neuePerson(String anzeigeName, Person.Status status) {
        Person p = new Person();
        p.anzeigeName = anzeigeName;
        p.status = status;
        p.matchSchluessel = normalisiere(anzeigeName);
        return p;
    }

    private static void bindeSub(Person p, String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return;
        }
        if (p.keycloakSub != null && !p.keycloakSub.equals(keycloakSub)) {
            throw new RegelVerletzung("Person " + p.id + " ist bereits an einen anderen Login gebunden.");
        }
        p.keycloakSub = keycloakSub;
        if (p.status == Person.Status.PROVISORISCH) {
            p.status = Person.Status.AKTIV; // Account-Claiming
        }
    }

    private void legeEmailAn(Person person, String email, boolean verifiziert, boolean primaer) {
        PersonEmail e = new PersonEmail();
        e.person = person;
        e.email = normEmail(email);
        e.verifiziert = verifiziert;
        e.primaer = primaer;
        e.persist();
    }

    private void legeMitgliedschaftAn(Person person, Organisation organisation, Mitgliedschaft.Rolle rolle,
            boolean buchungsberechtigt) {
        long vorhanden = Mitgliedschaft.count("person.id = ?1 and organisation.id = ?2 and rolle = ?3",
                person.id, organisation.id, rolle);
        if (vorhanden > 0) {
            return; // idempotent
        }
        Mitgliedschaft m = new Mitgliedschaft();
        m.person = person;
        m.organisation = organisation;
        m.rolle = rolle;
        m.buchungsberechtigt = buchungsberechtigt;
        m.gueltigVon = LocalDate.now();
        m.persist();
    }

    private void mussBuchungsberechtigt(Long personId, Long orgId) {
        long ok = Mitgliedschaft.count("person.id = ?1 and organisation.id = ?2 and buchungsberechtigt = true",
                personId, orgId);
        if (ok == 0) {
            throw new RegelVerletzung("Person " + personId + " darf nicht im Auftrag von Organisation "
                    + orgId + " bestellen.");
        }
    }

    private static Organisation mussOrganisation(Long orgId) {
        Organisation o = Organisation.findById(orgId);
        if (o == null) {
            throw RegelVerletzung.nichtGefunden("Organisation nicht gefunden: " + orgId);
        }
        return o;
    }

    private static Organisation mussOrganisationAktiv(Long orgId) {
        Organisation o = mussOrganisation(orgId);
        if (o.status == Organisation.Status.ZUSAMMENGEFUEHRT) {
            throw new RegelVerletzung("Organisation " + orgId + " ist bereits zusammengeführt.");
        }
        return o;
    }

    private static String primaerEmail(Long personId) {
        PersonEmail e = PersonEmail.find("person.id = ?1 and primaer = true", personId).firstResult();
        if (e == null) {
            e = PersonEmail.find("person.id", personId).firstResult();
        }
        return e == null ? null : e.email;
    }

    private static boolean aktiv(Mitgliedschaft m, LocalDate heute) {
        return (m.gueltigVon == null || !m.gueltigVon.isAfter(heute))
                && (m.gueltigBis == null || !m.gueltigBis.isBefore(heute));
    }

    private static Person golden(Person p) {
        if (p == null) {
            return null;
        }
        return p.status == Person.Status.ZUSAMMENGEFUEHRT && p.goldenPersonId != null
                ? Person.findById(p.goldenPersonId)
                : p;
    }

    private static Person mussAktiv(Long id) {
        Person p = Person.findById(id);
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + id);
        }
        if (p.status == Person.Status.ZUSAMMENGEFUEHRT) {
            throw new RegelVerletzung("Person " + id + " ist bereits zusammengeführt.");
        }
        return p;
    }

    private static String normEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String normalisiere(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
