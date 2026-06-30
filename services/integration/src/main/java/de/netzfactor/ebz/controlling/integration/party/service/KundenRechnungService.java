package de.netzfactor.ebz.controlling.integration.party.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorRolle;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.service.DebitorHoheitService;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * Kunden-Self-Service auf die eigenen Belege (Außenportal, Realm {@code ebz-customers}): bildet den
 * eingeloggten Menschen <b>kontext-skopiert</b> auf seine Debitoren ab und liefert deren
 * <i>festgeschriebene</i> Rechnungen — der Brückenkopf Party-Kern → Billing-Hoheit für die Lese-Sicht.
 *
 * <p>Die Auflösung Login → Debitor ist seiteneffektfrei und nutzt denselben {@link DebitorHoheitService#matchSchluessel
 * matchSchluessel}, mit dem die Debitor-Projektion ({@link PartyHoheitService#ermittleDebitor}) angelegt
 * wurde — also ein exakter Schlüssel-Join, kein Raten und ohne neue Debitoren anzulegen:
 * <ul>
 *   <li><b>Privat (Selbstzahler):</b> der PRIVAT-Debitor zur Person ({@code name|plz}-Schlüssel).</li>
 *   <li><b>Firma:</b> der FIRMA-Debitor jeder Organisation, für die der Login <i>buchungsberechtigt</i>
 *       ist (USt-Id- bzw. {@code name|plz}-Schlüssel der Organisation).</li>
 * </ul>
 * Entwürfe ({@link RechnungStatus#ENTWURF}) sind nie sichtbar; nur Debitoren im Status
 * {@link DebitorStatus#AKTIV} zählen (gemergte Forderungen hängen am Golden-Record).
 */
@ApplicationScoped
public class KundenRechnungService {

    @Inject
    PartyHoheitService party;

    @Inject
    Prozessspur prozess;

    /** Auflösung des Aufrufers über den Token-{@code sub}; {@code null} → keine bekannte Identität. */
    public Person aufrufer(String keycloakSub) {
        return party.findeNachSub(keycloakSub);
    }

    /** Wählbare Rechnungs-Kontexte des Logins (PRIVAT + jede buchungsberechtigte Firma). */
    public List<PartyHoheitService.Kontext> kontexte(Long personId) {
        return party.kontexte(personId);
    }

    /**
     * Festgeschriebene Belege eines gewählten Kontexts. {@code organisationId == null} ⇒ privat
     * (Selbstzahler); sonst der Firmenkontext — der Login muss dort buchungsberechtigt sein (sonst 403).
     */
    @Transactional
    public List<Rechnung> rechnungenImKontext(Long personId, Long organisationId) {
        Person p = mussPerson(personId);
        Set<Long> debitorIds = debitorIdsFuerKontext(p, organisationId);
        prozess.schritt("Rechnungen im Portal abrufen", Akteur.FIRMA, Prozess.System.PORTAL,
                Typ.USER_TASK, Phase.RECHNUNGSLAUF);
        if (debitorIds.isEmpty()) {
            return List.of();
        }
        return Rechnung.list("debitor.id in ?1 and status <> ?2 order by ausstellungsdatum desc, id desc",
                debitorIds, RechnungStatus.ENTWURF);
    }

    /**
     * Ein einzelner Beleg des Logins für den PDF-Abruf — über <b>alle</b> Kontexte des Aufrufers
     * autorisiert. Wirft 404, wenn der Beleg fehlt/Entwurf ist, und 403, wenn er keinem Debitor des
     * Logins gehört (Fremd-Beleg).
     */
    @Transactional
    public Rechnung meineRechnung(Long personId, Long rechnungId) {
        Person p = mussPerson(personId);
        Rechnung r = Rechnung.findById(rechnungId);
        if (r == null || r.status == RechnungStatus.ENTWURF) {
            throw RegelVerletzung.nichtGefunden("Beleg nicht gefunden: " + rechnungId);
        }
        if (!alleMeineDebitorIds(p).contains(r.debitorId())) {
            throw new RegelVerletzung(403, "Dieser Beleg gehört nicht zu Ihrem Konto.");
        }
        return r;
    }

    // ───────────────────────── intern: Login → Debitoren ─────────────────────────

    /** Debitoren-IDs eines einzelnen Kontexts (mit Buchungsberechtigung als Firmen-Tor). */
    private Set<Long> debitorIdsFuerKontext(Person p, Long organisationId) {
        if (organisationId == null) {
            String schluessel = PartyHoheitService.privatDebitorSchluessel(p);
            return debitorIds(schluessel, DebitorRolle.PRIVAT);
        }
        if (!party.istBuchungsberechtigt(p.id, organisationId)) {
            throw new RegelVerletzung(403, "Sie sind nicht berechtigt, die Rechnungen dieser Organisation zu sehen.");
        }
        Organisation o = Organisation.findById(organisationId);
        if (o == null) {
            throw RegelVerletzung.nichtGefunden("Organisation nicht gefunden: " + organisationId);
        }
        return debitorIds(o.matchSchluessel, DebitorRolle.FIRMA);
    }

    /** Vereinigung aller Debitoren des Logins (privat + alle buchungsberechtigten Firmen) — für die PDF-Autorisierung. */
    private Set<Long> alleMeineDebitorIds(Person p) {
        Set<Long> ids = new LinkedHashSet<>(debitorIdsFuerKontext(p, null));
        for (PartyHoheitService.Kontext k : party.kontexte(p.id)) {
            if (k.organisationId() != null) {
                ids.addAll(debitorIds(findeOrgMatchSchluessel(k.organisationId()), DebitorRolle.FIRMA));
            }
        }
        return ids;
    }

    private static Set<Long> debitorIds(String matchSchluessel, DebitorRolle rolle) {
        if (matchSchluessel == null || matchSchluessel.isBlank()) {
            return Set.of();
        }
        List<Debitor> debitoren = Debitor.list("matchSchluessel = ?1 and rolle = ?2 and status = ?3",
                matchSchluessel, rolle, DebitorStatus.AKTIV);
        Set<Long> ids = new LinkedHashSet<>();
        for (Debitor d : debitoren) {
            ids.add(d.id);
        }
        return ids;
    }

    private static String findeOrgMatchSchluessel(Long organisationId) {
        Organisation o = Organisation.findById(organisationId);
        return o == null ? null : o.matchSchluessel;
    }

    private Person mussPerson(Long personId) {
        Person p = Person.findById(personId);
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + personId);
        }
        return p;
    }
}
