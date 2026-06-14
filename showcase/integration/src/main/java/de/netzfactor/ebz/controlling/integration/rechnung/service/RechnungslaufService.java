package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;

/**
 * Erzeugt aus den aktiven Berufsschul-Anmeldungen eines Zeitraums Rechnungs-Entwürfe (R1).
 * <p>
 * Ablauf: alle {@code AKTIV}en BERUFSSCHULE-Anmeldungen mit {@code schuljahr/halbjahr} →
 * <b>gruppiert je zahlungspflichtigem Debitor</b> (= Sammelrechnung) → je Gruppe eine
 * {@code Rechnung(ENTWURF)} ohne Nummer; je Anmeldung 1–2 Positionen (Unterricht immer, Übernachtung
 * nur bei {@code zimmerart ≠ KEINE}), alle {@code BEFREIT}. Die Positionsbeträge sind aus den
 * Anmeldung-Feldern vorbefüllt (Entscheidung a) und im Entwurf editierbar.
 * <p>
 * <b>Idempotent:</b> über {@code laufSchluessel} (Bereich+Zeitraum+Debitor) wird je Debitor und
 * Zeitraum kein zweiter Entwurf erzeugt; ein erneuter Lauf liefert die bestehenden Entwürfe zurück.
 */
@ApplicationScoped
public class RechnungslaufService {

    private static final String BEFREIUNGSGRUND = "Umsatzsteuerbefreit nach § 4 UStG (Bildungsleistung)";

    @Inject
    Prozessspur prozess;

    @Transactional
    public List<Rechnung> erzeugeBerufsschulEntwuerfe(String schuljahr, int halbjahr) {
        List<Anmeldung> anmeldungen = Anmeldung.list(
                "typ = ?1 and status = ?2 and schuljahr = ?3 and halbjahr = ?4",
                AnmeldungTyp.BERUFSSCHULE, AnmeldungStatus.AKTIV, schuljahr, halbjahr);

        // Gruppierung je zahlungspflichtigem Debitor → eine Sammelrechnung
        Map<Long, List<Anmeldung>> jeDebitor = new LinkedHashMap<>();
        for (Anmeldung a : anmeldungen) {
            jeDebitor.computeIfAbsent(a.zahlungspflichtigerDebitorId(), k -> new ArrayList<>()).add(a);
        }

        String zeitraum = "Schuljahr %s, %d. Halbjahr".formatted(schuljahr, halbjahr);
        List<Rechnung> ergebnis = new ArrayList<>();

        for (Map.Entry<Long, List<Anmeldung>> e : jeDebitor.entrySet()) {
            Long debitorId = e.getKey();
            String schluessel = "BERUFSSCHULE|%s|%d|%d".formatted(schuljahr, halbjahr, debitorId);

            Rechnung r = Rechnung.find("laufSchluessel", schluessel).firstResult();
            if (r != null) {
                ergebnis.add(r); // idempotent: bestehenden Entwurf wiederverwenden
                continue;
            }

            r = new Rechnung();
            r.belegart = Belegart.RECHNUNG;
            r.bereich = Bereich.BERUFSSCHULE;
            r.debitor = Debitor.findById(debitorId);
            r.zeitraumBezeichnung = zeitraum;
            r.laufSchluessel = schluessel;
            r.status = RechnungStatus.ENTWURF;
            r.persist();

            for (Anmeldung a : e.getValue()) {
                r.positionen.add(unterrichtsposition(r, a, zeitraum));
                if (a.zimmerart != null && a.zimmerart != Zimmerart.KEINE && a.uebernachtungBetragCent != null) {
                    r.positionen.add(uebernachtungsposition(r, a, zeitraum));
                }
            }
            ergebnis.add(r);
        }
        if (!anmeldungen.isEmpty()) {
            prozess.schritt("Rechnungslauf bucht Anmeldung", Akteur.SYSTEM, Prozess.System.RECHNUNGSLAUF,
                    Typ.BUSINESS_RULE, Phase.RECHNUNGSLAUF);
        }
        return ergebnis;
    }

    /**
     * Erzeugt aus den aktiven Hochschul-Anmeldungen eines Semesters Rechnungs-Entwürfe (R6).
     * <p>
     * Je Anmeldung entstehen <b>Forderungsteile</b>: bei Firmen-Split (dualer Studienplatz) zwei
     * getrennte Rechnungen (Firmenanteil + Eigenanteil) an verschiedene Debitoren — unabhängige
     * Forderungen ohne Restschuld-Haftung; sonst eine Rechnung über die volle Gebühr. Jeder Teil wird
     * in {@code ratenAnzahl} Raten zerlegt (ganzzahlig, Restbetrag in die letzte Rate; gestaffelte
     * Fälligkeit). Alle Positionen {@code BEFREIT} (§4 UStG, Bildung). Idempotent über {@code laufSchluessel}.
     */
    @Transactional
    public List<Rechnung> erzeugeHochschulEntwuerfe(String semester) {
        List<Anmeldung> anmeldungen = Anmeldung.list(
                "typ = ?1 and status = ?2 and semester = ?3",
                AnmeldungTyp.HOCHSCHULE, AnmeldungStatus.AKTIV, semester);

        List<Rechnung> ergebnis = new ArrayList<>();
        for (Anmeldung a : anmeldungen) {
            for (Forderungsteil teil : forderungsteile(a)) {
                int n = a.ratenAnzahl == null || a.ratenAnzahl < 1 ? 1 : a.ratenAnzahl;
                long basis = teil.betragCent / n;
                for (int i = 1; i <= n; i++) {
                    long rate = i < n ? basis : teil.betragCent - basis * (n - 1);
                    String schluessel = "HOCHSCHULE|%s|%d|%d|%s|r%d"
                            .formatted(semester, a.id, teil.debitor.id, teil.label, i);
                    Rechnung vorhanden = Rechnung.find("laufSchluessel", schluessel).firstResult();
                    if (vorhanden != null) {
                        ergebnis.add(vorhanden);
                        continue;
                    }
                    String ratenZusatz = n > 1 ? " (Rate %d/%d)".formatted(i, n) : "";
                    String zeitraum = "Semester %s – %s%s".formatted(semester, teil.label, ratenZusatz);

                    Rechnung r = new Rechnung();
                    r.belegart = Belegart.RECHNUNG;
                    r.bereich = Bereich.HOCHSCHULE;
                    r.debitor = teil.debitor;
                    r.zeitraumBezeichnung = zeitraum;
                    r.laufSchluessel = schluessel;
                    r.status = RechnungStatus.ENTWURF;
                    r.zahlungszielTage = 14 + (i - 1) * 30; // gestaffelte Fälligkeit je Rate
                    r.persist();

                    RechnungPosition p = basis(r, a);
                    p.beschreibung = "Studiengebühr %s (%s%s) – %s"
                            .formatted(semester, teil.label, ratenZusatz, a.teilnehmerName);
                    p.einzelbetragCent = (int) rate;
                    p.leistungsart = Leistungsart.SONSTIGE;
                    r.positionen.add(p);
                    ergebnis.add(r);
                }
            }
        }
        return ergebnis;
    }

    private static List<Forderungsteil> forderungsteile(Anmeldung a) {
        long voll = a.semesterbetragCent == null ? 0 : a.semesterbetragCent;
        if (a.firmaDebitor != null && a.firmaAnteilCent != null) {
            return List.of(
                    new Forderungsteil(a.firmaDebitor, a.firmaAnteilCent, "Firmenanteil"),
                    new Forderungsteil(a.zahlungspflichtigerDebitor, voll - a.firmaAnteilCent, "Eigenanteil"));
        }
        return List.of(new Forderungsteil(a.zahlungspflichtigerDebitor, voll, "Studiengebühr"));
    }

    /** Ein Forderungsanteil einer Hochschul-Anmeldung (Debitor + Betrag + Bezeichnung). */
    private record Forderungsteil(Debitor debitor, long betragCent, String label) {
    }

    private static RechnungPosition unterrichtsposition(Rechnung r, Anmeldung a, String zeitraum) {
        RechnungPosition p = basis(r, a);
        p.beschreibung = "Unterricht (%s) – %s".formatted(zeitraum, a.teilnehmerName);
        p.einzelbetragCent = a.unterrichtBetragCent == null ? 0 : a.unterrichtBetragCent;
        p.leistungsart = Leistungsart.UNTERRICHT;
        return p;
    }

    private static RechnungPosition uebernachtungsposition(Rechnung r, Anmeldung a, String zeitraum) {
        RechnungPosition p = basis(r, a);
        String zimmer = a.zimmerart == Zimmerart.EINZEL ? "Einzelzimmer" : "Doppelzimmer";
        p.beschreibung = "Übernachtung %s (%s) – %s".formatted(zimmer, zeitraum, a.teilnehmerName);
        p.einzelbetragCent = a.uebernachtungBetragCent;
        p.leistungsart = Leistungsart.UEBERNACHTUNG;
        return p;
    }

    private static RechnungPosition basis(Rechnung r, Anmeldung a) {
        RechnungPosition p = new RechnungPosition();
        p.rechnung = r;
        p.teilnehmerName = a.teilnehmerName;
        p.menge = 1;
        p.steuerfall = Steuerfall.BEFREIT;
        p.steuersatz = 0;
        p.befreiungsgrund = BEFREIUNGSGRUND;
        p.herkunft = "AUTO";
        return p;
    }
}
