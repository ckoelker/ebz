package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorAlias;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorRolle;
import de.netzfactor.ebz.controlling.integration.rechnung.model.DebitorStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;

/**
 * Debitoren-Hoheit (R3): zentrale, idempotente Vergabe + Match/Merge + Alias-Auflösung — das „eine
 * Gehirn" für Debitorenstammdaten, das die Doppel-Debitoren beseitigt.
 * <ul>
 *   <li><b>findeOderLege</b> — künftige Anlagen: existiert ein Golden-Record mit gleichem
 *       Dublettenschlüssel im Bereich, wird er wiederverwendet; sonst zentrale Nummernvergabe.</li>
 *   <li><b>importiereBestand</b> — Altbestand: dedupliziert auf den Golden-Record und konserviert die
 *       Altnummer als {@link DebitorAlias} (Weg A, minimal-invasiv).</li>
 *   <li><b>merge</b> — räumt Dubletten zusammen: hängt Forderungen/Anmeldungen um, bewahrt Altnummern
 *       als Alias und markiert den unterlegenen Datensatz als ZUSAMMENGEFUEHRT.</li>
 * </ul>
 */
@ApplicationScoped
public class DebitorHoheitService {

    @Inject
    DebitorNummernService nummern;

    /** Eingangs-Stammdaten (entkoppelt von DTO/Entity). */
    public record Stammdaten(Bereich bereich, DebitorRolle rolle, String name, String strasse,
            String plz, String ort, String land, String ustId, String iban, String email) {
    }

    /** Idempotente Anlage: vorhandenen Golden-Record im Bereich wiederverwenden oder neu vergeben. */
    @Transactional
    public Debitor findeOderLege(Stammdaten s) {
        String schluessel = matchSchluessel(s.name(), s.plz(), s.ustId());
        Debitor treffer = Debitor.find("bereich = ?1 and status = ?2 and matchSchluessel = ?3",
                s.bereich(), DebitorStatus.AKTIV, schluessel).firstResult();
        if (treffer != null) {
            return treffer;
        }
        Debitor d = new Debitor();
        d.debitorNr = nummern.vergib(s.bereich());
        d.bereich = s.bereich();
        d.rolle = s.rolle();
        d.name = s.name();
        d.strasse = s.strasse();
        d.plz = s.plz();
        d.ort = s.ort();
        d.land = s.land();
        d.ustId = s.ustId();
        d.iban = s.iban();
        d.email = s.email();
        d.status = DebitorStatus.AKTIV;
        d.matchSchluessel = schluessel;
        d.persist();
        return d;
    }

    /** Altbestand übernehmen: dedupliziert auf den Golden-Record und merkt die Altnummer als Alias. */
    @Transactional
    public Debitor importiereBestand(Stammdaten s, String quelle, String externeNr) {
        Debitor golden = findeOderLege(s);
        legeAliasAn(golden.id, quelle, externeNr);
        return golden;
    }

    /** Löst eine externe/alte Nummer auf den aktiven Golden-Record auf (folgt Merge-Zeigern). */
    public Debitor aufloesen(String quelle, String externeNr) {
        DebitorAlias alias = DebitorAlias.find("quelle = ?1 and externeNr = ?2", quelle, externeNr).firstResult();
        if (alias == null) {
            return null;
        }
        return golden(Debitor.findById(alias.debitorId));
    }

    /** Dublettenkandidaten: andere aktive Debitoren im Bereich mit gleichem Dublettenschlüssel. */
    public List<Debitor> kandidaten(Long debitorId) {
        Debitor d = Debitor.findById(debitorId);
        if (d == null || d.matchSchluessel == null) {
            return List.of();
        }
        return Debitor.list("bereich = ?1 and status = ?2 and matchSchluessel = ?3 and id <> ?4",
                d.bereich, DebitorStatus.AKTIV, d.matchSchluessel, d.id);
    }

    /** Aliase eines Debitors (zur Anzeige/Audit). */
    public List<DebitorAlias> aliase(Long debitorId) {
        return DebitorAlias.list("debitorId", debitorId);
    }

    /**
     * Führt {@code quell} in {@code ziel} zusammen: Forderungen + Anmeldungen werden umgehängt, die
     * Quell-Aliase und die Quell-Nummer selbst bleiben als Alias auf {@code ziel} erhalten, der
     * unterlegene Datensatz wird ZUSAMMENGEFUEHRT (zeigt per {@code goldenDebitorId} auf {@code ziel}).
     */
    @Transactional
    public Debitor merge(Long quellId, Long zielId) {
        if (quellId.equals(zielId)) {
            throw new RegelVerletzung("Quell- und Ziel-Debitor sind identisch.");
        }
        Debitor quell = mussAktiv(quellId);
        Debitor ziel = mussAktiv(zielId);
        if (quell.bereich != ziel.bereich) {
            throw new RegelVerletzung("Zusammenführen nur innerhalb desselben Bereichs möglich.");
        }
        Rechnung.update("debitorId = ?1 where debitorId = ?2", ziel.id, quell.id);
        Anmeldung.update("zahlungspflichtigerDebitorId = ?1 where zahlungspflichtigerDebitorId = ?2", ziel.id, quell.id);
        DebitorAlias.update("debitorId = ?1 where debitorId = ?2", ziel.id, quell.id);
        legeAliasAn(ziel.id, "MERGE", quell.debitorNr);
        quell.status = DebitorStatus.ZUSAMMENGEFUEHRT;
        quell.goldenDebitorId = ziel.id;
        return ziel;
    }

    // ───────────────────────── intern ─────────────────────────

    private void legeAliasAn(Long debitorId, String quelle, String externeNr) {
        if (externeNr == null || externeNr.isBlank()) {
            return;
        }
        long vorhanden = DebitorAlias.count("quelle = ?1 and externeNr = ?2", quelle, externeNr);
        if (vorhanden == 0) {
            DebitorAlias a = new DebitorAlias();
            a.debitorId = debitorId;
            a.quelle = quelle;
            a.externeNr = externeNr;
            a.persist();
        }
    }

    private static Debitor golden(Debitor d) {
        if (d == null) {
            return null;
        }
        return d.status == DebitorStatus.ZUSAMMENGEFUEHRT && d.goldenDebitorId != null
                ? Debitor.findById(d.goldenDebitorId)
                : d;
    }

    private static Debitor mussAktiv(Long id) {
        Debitor d = Debitor.findById(id);
        if (d == null) {
            throw RegelVerletzung.nichtGefunden("Debitor nicht gefunden: " + id);
        }
        if (d.status != DebitorStatus.AKTIV) {
            throw new RegelVerletzung("Debitor " + id + " ist nicht aktiv (Status: " + d.status + ").");
        }
        return d;
    }

    /** Normalisierter Dublettenschlüssel: USt-IdNr. (stark) bzw. Name+PLZ (schwach). */
    public static String matchSchluessel(String name, String plz, String ustId) {
        if (ustId != null && !ustId.isBlank()) {
            return "ust:" + normalisiere(ustId);
        }
        return "np:" + normalisiere(name) + "|" + normalisiere(plz);
    }

    private static String normalisiere(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
