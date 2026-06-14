package de.netzfactor.ebz.controlling.integration.party.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.party.model.DublettenReview;
import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * HITL-Dubletten-Workflow: stellt die <b>Review-Queue</b> offener Fälle zusammen (provisorische
 * Personen / angefragte Firmen, die einen Dubletten-Kandidaten haben und noch nicht entschieden sind)
 * — je Fall mit dem KI-{@link DublettenUrteil} zur Priorisierung — und führt die <b>menschliche
 * Entscheidung</b> aus (Merge via bestehende {@link PartyHoheitService}-Hoheit oder bestätigte
 * Neuanlage). Jede Entscheidung wird als {@link DublettenReview} auditiert.
 */
@ApplicationScoped
public class DublettenReviewService {

    /** Begrenzung des Queue-Scans (Showcase) — neueste zuerst. */
    private static final int SCAN_LIMIT = 200;

    @Inject
    DublettenBerater berater;

    @Inject
    PartyHoheitService party;

    public enum Art {
        FIRMA, PERSON
    }

    public enum Entscheidung {
        NEUANLAGE_BESTAETIGT, GEMERGT
    }

    /** Ein vorgeschlagenes Merge-Ziel mit KI-Bewertung. */
    public record ZielVorschlag(Long zielId, String bezeichnung, double aehnlichkeit,
            String einschaetzung, String begruendung) {
    }

    /** Ein offener Review-Fall: der Kandidat + seine bewerteten Merge-Ziele. */
    public record Fall(Art art, Long kandidatId, String bezeichnung, List<ZielVorschlag> vorschlaege) {
    }

    /** Offene Fälle: angefragte Firmen + provisorische Personen mit Kandidaten, noch nicht entschieden. */
    public List<Fall> offeneFaelle() {
        List<Fall> faelle = new ArrayList<>();

        List<Organisation> orgs = Organisation.find("status = ?1 order by id desc",
                Organisation.Status.ANGEFRAGT).page(0, SCAN_LIMIT).list();
        for (Organisation o : orgs) {
            if (bereitsEntschieden(Art.FIRMA, o.id)) {
                continue;
            }
            List<Organisation> kandidaten = party.organisationKandidaten(o.id);
            if (kandidaten.isEmpty()) {
                continue;
            }
            List<ZielVorschlag> vs = kandidaten.stream()
                    .map(z -> vorschlag(z.id, z.name, berater.bewerteFirma(o, z))).toList();
            faelle.add(new Fall(Art.FIRMA, o.id, o.name, vs));
        }

        List<Person> personen = Person.find("status = ?1 order by id desc",
                Person.Status.PROVISORISCH).page(0, SCAN_LIMIT).list();
        for (Person p : personen) {
            if (bereitsEntschieden(Art.PERSON, p.id)) {
                continue;
            }
            List<Person> kandidaten = party.kandidaten(p.id);
            if (kandidaten.isEmpty()) {
                continue;
            }
            List<ZielVorschlag> vs = kandidaten.stream()
                    .map(z -> vorschlag(z.id, z.anzeigeName, berater.bewertePerson(p, z))).toList();
            faelle.add(new Fall(Art.PERSON, p.id, p.anzeigeName, vs));
        }
        return faelle;
    }

    /**
     * Führt die HITL-Entscheidung aus und auditiert sie: {@code GEMERGT} führt den Kandidaten in das Ziel
     * zusammen (über die bestehende Merge-Hoheit), {@code NEUANLAGE_BESTAETIGT} bestätigt den Kandidaten
     * als eigenständig (bei Firmen: Status {@code ANGEFRAGT → AKTIV}, damit Schritt C provisionieren kann).
     */
    @Transactional
    public DublettenReview entscheide(Art art, Long kandidatId, Entscheidung entscheidung, Long zielId,
            String entscheider) {
        // KI-Urteil für das gewählte Ziel als Audit-Snapshot festhalten (Basis der Entscheidung).
        DublettenUrteil urteil = null;
        if (zielId != null) {
            urteil = art == Art.FIRMA
                    ? berater.bewerteFirma(orgMuss(kandidatId), orgMuss(zielId))
                    : berater.bewertePerson(personMuss(kandidatId), personMuss(zielId));
        }

        if (entscheidung == Entscheidung.GEMERGT) {
            if (zielId == null) {
                throw new RegelVerletzung("Ein Merge braucht eine Ziel-Id.");
            }
            if (art == Art.FIRMA) {
                party.mergeOrganisation(kandidatId, zielId);
            } else {
                party.merge(kandidatId, zielId);
            }
        } else if (art == Art.FIRMA) {
            // Bestätigte Neuanlage einer Firma → produktiv schalten (claimbarer Login folgt in Schritt C).
            orgMuss(kandidatId).status = Organisation.Status.AKTIV;
        }
        // Bestätigte Neuanlage einer Person: bleibt PROVISORISCH (claimbar); das Audit dokumentiert sie.

        DublettenReview r = new DublettenReview();
        r.art = art.name();
        r.kandidatId = kandidatId;
        r.zielId = entscheidung == Entscheidung.GEMERGT ? zielId : null;
        r.entscheidung = entscheidung.name();
        if (urteil != null) {
            r.kiAehnlichkeit = urteil.aehnlichkeit();
            r.kiEinschaetzung = urteil.einschaetzung().name();
            r.kiBegruendung = urteil.begruendung();
        }
        r.entschiedenVon = entscheider;
        r.entschiedenAm = Instant.now();
        r.persist();
        return r;
    }

    private static ZielVorschlag vorschlag(Long zielId, String bezeichnung, DublettenUrteil u) {
        return new ZielVorschlag(zielId, bezeichnung, u.aehnlichkeit(), u.einschaetzung().name(), u.begruendung());
    }

    private boolean bereitsEntschieden(Art art, Long kandidatId) {
        return DublettenReview.count("art = ?1 and kandidatId = ?2", art.name(), kandidatId) > 0;
    }

    private static Organisation orgMuss(Long id) {
        Organisation o = Organisation.findById(id);
        if (o == null) {
            throw RegelVerletzung.nichtGefunden("Organisation nicht gefunden: " + id);
        }
        return o;
    }

    private static Person personMuss(Long id) {
        Person p = Person.findById(id);
        if (p == null) {
            throw RegelVerletzung.nichtGefunden("Person nicht gefunden: " + id);
        }
        return p;
    }
}
